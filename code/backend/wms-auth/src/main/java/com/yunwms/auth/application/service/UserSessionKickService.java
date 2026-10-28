package com.yunwms.auth.application.service;

import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditEntry;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.port.AuditRecorder;
import com.yunwms.auth.domain.port.SessionInvalidator;
import com.yunwms.auth.domain.port.UserQuery;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;

/**
 * 管理员使某个账号的全部会话立即失效的用例，即「踢下线」。
 *
 * <p>能力本身在 {@link SessionInvalidator#invalidateAllOf}（1.4 已落地，本人改密与停用账号在消费它）。
 * 本类补的是它不做的那三件事：账号存在性、审计留痕，以及一个能被权限点单独授权的入口。
 * 授权不写在代码里，由控制器上的 {@code system:user:kick} 表达——{@link SessionInvalidator}
 * 的类注释已写明「获得授权之前不得把本能力直接暴露成管理端接口」。
 *
 * <p><b>必须先确认账号存在。</b>{@code invalidateAllOf} 对不存在的标识静默成功：登出一个没有任何
 * 会话的登录标识不会报错，于是接口会回 200，调用方以为踢了人，实际什么也没发生。
 * 这一次读同时给出审计要用的登录名，因此不为「在不在」另发一次查询——与
 * {@link UserUpdateService} 的「存在性判定与变更前快照取自同一次读」同一口径：分两次读会让审计里的
 * 登录名来自另一个时间点。已逻辑删除的账号按不存在处理（{@link UserQuery#findById} 的契约如此），
 * 它的会话早已随之失效。
 *
 * <p><b>允许踢自己。</b>踢自己只会把发起本次请求的会话打掉，权限、角色与数据范围都存在库里，
 * 重新登录一条不少。{@code UserStatusChangeService} 里那条「不能停用自己」防的是另一件事——
 * 停用之后没有恢复入口；踢下线没有这个问题，因此这里不写拒绝分支，也不为它加任何提示。
 * 连带的结果是本用例不需要读操作人身份：没有 {@code updated_by} 要填，审计的操作人由
 * {@link AuditRecorder} 的实现自己从请求上下文取，于是「操作人与目标是不是同一个人」
 * 这个判断在本类里根本没有出现的机会——这是刻意的取舍，不是漏了一次注入。
 *
 * <p><b>顺序固定为「查账号 → 留痕 → 失效会话」，与 {@code UserStatusChangeService} 的
 * 「落库 → 失效会话 → 留痕」相反，理由是两者的前置动作性质不同。</b>那处的前置动作是往库里写状态，
 * 写完不影响操作人自己的会话；这里的前置动作是让目标账号的会话失效，而<b>目标可能就是操作人自己</b>。
 * 一旦先失效，本次请求的令牌随之作废，{@code AuditRecorder} 的实现再去读会话只能读到空，
 * 日志会落成「操作人：系统」——一条永久且错误的归因。{@code AuditRecorderImpl} 已经写明
 * 「写『系统』是在陈述事实，那时确实有一个真人时写成系统会永久留下一条错误的归因」，
 * 而审计只追加、不可修改，事后无法订正。因此会话失效必须排在留痕之后。
 *
 * <p>这个顺序的代价是「审计写失败时踢不出去」（异常直接透出，管理员重试即可）：
 * 与「先踢掉会话、再留下一条归因错误的记录」相比，前者可重试、后者不可修复。
 *
 * <p><b>两侧快照都为 {@code null}。</b>踢下线不改账号的任何字段，而用户快照的白名单里也没有「会话」
 * 这个维度（见 {@code UserAuditSnapshots}）。写两份逐字相同的账号快照，读日志的人只会看到
 * 「改了，但前后一样」，与不写没有区别；这与「重置密码」同一处理——那条日志能回答的也只是
 * 「谁在什么时候对谁做了什么」。
 *
 * <p><b>重复踢照样成功、照样留痕。</b>会话本来就已经没了，再失效一次没有代价；跳过留痕则会让
 * 「上一次没记上」永远补不回来。与停用的同状态重放同一口径。
 *
 * <p>不开事务：只有一次会话失效与一条审计，两者都不是能一起回滚的东西。与
 * {@code UserStatusChangeService} 同一判断。
 */
@Service
public class UserSessionKickService {

    private final UserQuery userQuery;
    private final SessionInvalidator sessionInvalidator;
    private final AuditRecorder auditRecorder;

    public UserSessionKickService(UserQuery userQuery,
                                  SessionInvalidator sessionInvalidator,
                                  AuditRecorder auditRecorder) {
        this.userQuery = userQuery;
        this.sessionInvalidator = sessionInvalidator;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 使该账号的全部会话立即失效。
     *
     * <p>作用范围是「全部会话」而不是「其他会话」：管理端踢人没有「保留发起请求的那台设备」的语义，
     * 那种语义属于本人改密（{@link SessionInvalidator#invalidateOthersOfCurrent}）。
     *
     * @param userId 目标用户标识；可以是操作人自己
     * @throws BizException 用户不存在或已逻辑删除（40400）
     */
    public void kick(long userId) {
        // 一次读同时回答「在不在」与「审计里的登录名叫什么」，见类注释
        UserProfile target = userQuery.findById(userId)
                .orElseThrow(() -> BizException.notFound("用户不存在"));

        String loginName = target.summary().loginName();
        // 动作编码沿用「停用用户」的 UPDATE 而不是 LOGIN：它是管理端对账号施加的安全动作，
        // LOGIN 留给真正发生在登录链路上的事件（登录成功、失败锁定），否则登录历史会被管理操作污染。
        // 摘要写明动作而不是「操作用户」：看日志的人要能一眼分辨「改资料」与「强制下线」
        auditRecorder.record(new AuditEntry(AuditBizType.USER, loginName, AuditAction.UPDATE,
                "踢下线用户 " + loginName, null, null));

        // 排在留痕之后：目标可能是操作人自己，先失效会让审计读不到会话、把操作人记成「系统」，见类注释
        sessionInvalidator.invalidateAllOf(userId);
    }
}

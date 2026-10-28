package com.yunwms.auth.application.service;

import com.yunwms.auth.application.audit.UserAuditSnapshots;
import com.yunwms.auth.application.command.ChangeUserStatusCommand;
import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditEntry;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.domain.port.AuditRecorder;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.SessionInvalidator;
import com.yunwms.auth.domain.port.UserAccountUpdater;
import com.yunwms.auth.domain.port.UserQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 启用与停用账号的用例。
 *
 * <p>校验顺序固定为「取操作人 → 账号存在 → 不停用自己 → 不停用最后一个可用超管 → 落库 → 失效会话」，
 * 按「错得越早、改起来越省事」排列：前两项是改入参，后两项被拒绝时要改的是这次操作本身。
 *
 * <p><b>停用必须让既有会话立刻失效</b>：规格要求「管理员停用某用户 → 该用户既有会话立即失效」。
 * 只改库是不够的——令牌还在，已登录的终端可以带着它一直用到过期，最长两小时。
 * 失效落在令牌层（{@link SessionInvalidator#invalidateAllOf}），不靠每个业务接口自己去查状态，
 * 那种做法漏掉一个入口等于没停用。
 *
 * <p><b>不能停用自己</b>：停用会连带把自己这次请求的会话打掉，管理员会以为「点了一下就被登出」，
 * 而如果他是唯一能进配置页的人，重登也进不来。与 {@code UserRoleAssignmentService}
 * 「不能移除自己最后的内置角色」同一口径：都是「误操作一次就没有恢复入口」的自我保护。
 *
 * <p><b>不能停用最后一个可用超管</b>：停完之后没有任何账号能配置权限，恢复只能改库。
 * 判定交给 {@link UserQuery#isOnlyAvailableSuperAdmin}，一条 SQL 出结论；本类不取全部超管再逐个比对，
 * 那种写法一是随账号数增长，二是两次查询之间有人新增了超管时结论就过期了。
 *
 * <p>两条保护都用 {@link ErrorCode#CONFLICT}（409 段位）而不是 40000：入参本身合法，
 * 被拒绝的是它造成的状态。与「不能移除自己最后的内置角色」同一取舍，理由见那个用例的说明。
 *
 * <p><b>留痕写明方向。</b>摘要写「停用用户」或「启用用户」而不是「修改状态」：看日志的人需要知道
 * 这次是收紧还是放宽。同状态重放同样记一条——那次提交确实发生过，方向取目标状态；
 * 与编辑、启停角色的「没改就不记」不同，这里每一次提交都真的把状态写成目标值。
 *
 * <p>不开事务：只有一条 UPDATE，会话失效与审计都不是能一起回滚的东西。加了事务反而会让
 * 「库已改、会话未失效」看起来像可以一起回滚——它们本来就不能。三个动作的顺序固定为
 * 「落库 → 失效会话 → 留痕」：审计放在最后，是因为前面两步都是不可撤销的既成事实，
 * 而审计写失败时异常照常透出（管理员重试即可补上），不该让它先于会话失效挡住停用。
 */
@Service
public class UserStatusChangeService {

    private final UserQuery userQuery;
    private final UserAccountUpdater userAccountUpdater;
    private final SessionInvalidator sessionInvalidator;
    private final CurrentSession currentSession;
    private final AuditRecorder auditRecorder;

    public UserStatusChangeService(UserQuery userQuery,
                                   UserAccountUpdater userAccountUpdater,
                                   SessionInvalidator sessionInvalidator,
                                   CurrentSession currentSession,
                                   AuditRecorder auditRecorder) {
        this.userQuery = userQuery;
        this.userAccountUpdater = userAccountUpdater;
        this.sessionInvalidator = sessionInvalidator;
        this.currentSession = currentSession;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 把账号改成目标状态；停用时同时失效该账号的全部会话。
     *
     * <p>目标状态与当前状态相同时照常返回成功：状态流转是幂等的，重复提交不该报错。
     * 停用一个已经停用的账号时那条「失效全部会话」照发——它的令牌本就不该存在，
     * 多失效一次没有代价，而跳过它会让「上一次停用没失效成功」永远补不回来。
     *
     * @param command 状态变更入参
     * @throws BizException 当前会话无效（40100）；用户不存在（40400）；
     *                      停用自己、停用最后一个可用超管（40900）
     */
    public void change(ChangeUserStatusCommand command) {
        long operatorId = currentSession.currentUserId()
                // 鉴权过滤器已拦过未登录请求，走到这里取不到会话只能是账号在请求途中被删除或令牌已失效；
                // 此时不能继续：updated_by 会写成空值，事后无从定位这次停用是谁做的
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        long targetUserId = command.userId();
        // 一次读同时回答「在不在」与「改前是什么」：分两次读会让快照里的值来自另一个时间点
        UserProfile current = userQuery.findById(targetUserId)
                .orElseThrow(() -> BizException.notFound("用户不存在"));

        UserStatus targetStatus = command.status();
        if (targetStatus == UserStatus.DISABLED) {
            if (targetUserId == operatorId) {
                throw BizException.conflict("不能停用自己");
            }
            if (userQuery.isOnlyAvailableSuperAdmin(targetUserId)) {
                throw BizException.conflict("不能停用系统中最后一个可用的超级管理员");
            }
        }

        userAccountUpdater.updateStatus(targetUserId, targetStatus, operatorId, Instant.now());

        if (targetStatus == UserStatus.DISABLED) {
            // 排在落库之后：先失效再落库时，若库写失败，用户已被踢下线而账号仍然可用，
            // 管理员会看到「操作失败」，用户却需要重新登录一次。反过来只是「库已改、会话晚几毫秒失效」
            sessionInvalidator.invalidateAllOf(targetUserId);
        }

        String loginName = current.summary().loginName();
        // 方向取目标状态而不是「前后对比」：同状态重放时前后相同，但那次提交确实把账号写成了这个状态
        String summary = (targetStatus == UserStatus.DISABLED ? "停用用户 " : "启用用户 ") + loginName;
        auditRecorder.record(new AuditEntry(AuditBizType.USER, loginName, AuditAction.UPDATE, summary,
                UserAuditSnapshots.of(current), UserAuditSnapshots.ofStatusChanged(current, targetStatus)));
    }
}

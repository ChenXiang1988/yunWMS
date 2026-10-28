package com.yunwms.auth.application.service;

import com.yunwms.auth.application.audit.UserAuditSnapshots;
import com.yunwms.auth.application.command.UpdateUserCommand;
import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditEntry;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.port.AuditRecorder;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.UserAccountUpdater;
import com.yunwms.auth.domain.port.UserQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/**
 * 编辑用户基本信息的用例：覆盖展示名、邮箱与企业微信 userid。
 *
 * <p>校验顺序固定为「取操作人 → 账号存在 → 展示名非空 → 落库」，落库时由数据库兜住邮箱那两条约束。
 *
 * <p><b>邮箱必填与唯一都不在这里预判</b>，与建号同一理由：唯一性建在 {@code lower(email)} 的
 * 表达式唯一索引上，服务层「先查再改」在并发下无效——两个请求会同时查到「没被占用」，
 * 一个成功、一个拿到数据库异常，与不查的痛苦相同却多了一次往返。而「内部员工必须有邮箱」
 * 是 {@code ck_user_email_required} 的同一条规则，在应用层再写一遍就是两处定义，
 * 改一处忘一处时会表现成「接口放过了、数据库拦下了」的 500。两者都只在 UPDATE 时由数据库暴露，
 * 这里捕获后翻译成中文提示。
 *
 * <p>不开事务：只有一条 UPDATE。捕获约束冲突后转成业务异常再抛出，本来就没有「前一半写入需要撤销」
 * 的状态；加一个空事务只会让人以为这里还有别的东西需要原子提交。审计写入因此自建事务并随后立即提交：
 * 业务写成功而审计写失败时异常照常透出（可重试），不会变成一次没有留痕的成功。
 *
 * <p><b>字段一个都没变时不写审计：</b>提交了但没改动，记一条只会稀释信号——事后翻日志时，
 * 「谁把它改了」与「谁点了一次保存」看起来一模一样。判定用已落库的详情对象逐字段比较，
 * 不比较快照 {@code Map}：后者把「值为 null」与「键缺失」的区别也卷进了判定。
 * 库照常写（{@code updated_at} 是事实），只是不留痕。
 *
 * <p><b>存在性判定与变更前快照取自同一次读。</b>原先只判「这一行在不在」，现在还要它改前的值：
 * 分两次读会让快照里的值来自另一个时间点，写出来的日志前后不属于同一次提交。
 */
@Service
public class UserUpdateService {

    private final UserQuery userQuery;
    private final UserAccountUpdater userAccountUpdater;
    private final CurrentSession currentSession;
    private final AuditRecorder auditRecorder;

    public UserUpdateService(UserQuery userQuery,
                             UserAccountUpdater userAccountUpdater,
                             CurrentSession currentSession,
                             AuditRecorder auditRecorder) {
        this.userQuery = userQuery;
        this.userAccountUpdater = userAccountUpdater;
        this.currentSession = currentSession;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 覆盖某个账号的展示名、邮箱与企业微信 userid。
     *
     * @param command 编辑入参；{@code email} 与 {@code wecomUserId} 为 {@code null} 表示清空
     * @throws BizException 当前会话无效（40100）；用户不存在（40400）；
     *                      展示名为空、内部员工清空邮箱（40000）；邮箱已被占用（40903）
     */
    public void update(UpdateUserCommand command) {
        long operatorId = currentSession.currentUserId()
                // 鉴权过滤器已拦过未登录请求，走到这里取不到会话只能是账号在请求途中被删除或令牌已失效；
                // 此时不能继续：updated_by 会写成空值，事后无从定位这条资料是谁改的
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        long targetUserId = command.userId();
        UserProfile current = userQuery.findById(targetUserId)
                .orElseThrow(() -> BizException.notFound("用户不存在"));

        String displayName = stripToNull(command.displayName());
        if (displayName == null) {
            // 库里的 NOT NULL 只能给出约束名，变不成「展示名不能为空」这样的提示；
            // 请求体上的 @NotBlank 是同一口径的前置拦截，这里兜住非 HTTP 入口
            throw BizException.badRequest("展示名不能为空");
        }
        // 清洗一次、两处使用：落库的值与快照里的值必须是同一份，否则日志会与库里的实际状态对不上
        String email = stripToNull(command.email());
        String wecomUserId = stripToNull(command.wecomUserId());

        try {
            userAccountUpdater.updateProfile(targetUserId, displayName, email, wecomUserId,
                    operatorId, Instant.now());
        } catch (DataIntegrityViolationException e) {
            throw translateConstraintViolation(e);
        }

        boolean unchanged = displayName.equals(current.summary().displayName())
                && Objects.equals(email, current.email())
                && Objects.equals(wecomUserId, current.wecomUserId());
        if (unchanged) {
            // 库照常写（updated_at 是事实），但不留痕：空提交没有语义，记一条只会稀释信号
            return;
        }
        String loginName = current.summary().loginName();
        auditRecorder.record(new AuditEntry(AuditBizType.USER, loginName, AuditAction.UPDATE,
                "修改用户 " + loginName, UserAuditSnapshots.of(current),
                UserAuditSnapshots.ofUpdated(current, displayName, email, wecomUserId)));
    }

    /**
     * 把库约束冲突翻译成可展示的业务错误。
     *
     * <p>按约束名匹配而不是按 SQLState：违反唯一索引与 CHECK 的 SQLState 都是 23xxx，
     * 区分不了是哪一处；约束名不会随 {@code lc_messages} 或其他区域设置改变。
     *
     * <p>不认识的约束原样抛出：那是本用例的假设被打破了（例如新加了 CHECK），
     * 猜一个错误码返回给用户，等于把「我们不知道自己违反了哪条规则」伪装成一次正常的拒绝。
     *
     * @param e 数据库抛出的完整性约束冲突
     * @return 已翻译的业务异常；约束未知时为原始异常，由全局异常处理归到 50000
     */
    private static RuntimeException translateConstraintViolation(DataIntegrityViolationException e) {
        String cause = e.getMostSpecificCause().getMessage();
        if (cause == null) {
            return e;
        }
        if (cause.contains("ux_sys_user_email")) {
            // 用 40903 而不是泛化的 40900：前端要能把这个提示挂在邮箱字段上，
            // 与建号侧登录名占用（40901）同一模式
            return new BizException(ErrorCode.EMAIL_TAKEN);
        }
        if (cause.contains("ck_user_email_required")) {
            // 与建号同一文案：两条链路的同一条规则应当给出同一种说法，
            // 否则管理员会以为「建号时缺邮箱」和「编辑时清空邮箱」是两件事
            return BizException.badRequest("内部员工必须填写邮箱");
        }
        return e;
    }

    /**
     * 去掉两侧空白，空串按未填处理。
     *
     * <p>空串必须落成 {@code null}：{@code email = ''} 能绕过 {@code email IS NOT NULL}，
     * 让内部员工在没有邮箱的情况下改成功；而货主账号把邮箱清空时，库里该是 NULL 而不是一个空串，
     * 否则「有没有填邮箱」要分两种值判断。
     *
     * @param value 原始值，可为 {@code null}
     * @return 裁剪后的值；{@code null}、空串或全空白时为 {@code null}
     */
    private static String stripToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}

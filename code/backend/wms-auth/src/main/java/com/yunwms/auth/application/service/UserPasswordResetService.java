package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.ResetUserPasswordCommand;
import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditEntry;
import com.yunwms.auth.domain.model.PasswordPolicy;
import com.yunwms.auth.domain.model.PasswordRuleViolation;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.port.AuditRecorder;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.PasswordHasher;
import com.yunwms.auth.domain.port.SessionInvalidator;
import com.yunwms.auth.domain.port.UserAccountUpdater;
import com.yunwms.auth.domain.port.UserQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 管理员重置他人密码的用例。
 *
 * <p>与本人改密（{@link PasswordChangeService}）是两条链路，不合并：本人改密必须验旧密码且改完即用，
 * 管理员重置不验旧密码且必须强制再改一次。合并成一个方法会让「要不要验旧密码」变成一个参数，
 * 而那个参数一旦传错，动静是「任何人都能不验旧密码改自己密码」。
 *
 * <p>校验顺序固定为「取操作人 → 不重置自己 → 账号存在 → 密码策略 → 落库 → 失效会话」。
 * 不重置自己排在最前：它不需要查库就能判，而它防的是一条绕过旧密码校验的通道——
 * 本接口不验旧密码，若能作用于自己，本人改密那道旧密码校验就等于不存在。
 * 除此之外规格没有要求验旧密码，这里也不额外加：管理员本来就不知道别人的口令。
 *
 * <p>策略不通过时一次返回全部违规项：改一条错一条的提示等于让管理员反复试。
 *
 * <p>成功后排两件事：把「需先改密」标记置为 {@code true}（临时口令只能换一次登录），
 * 并失效该账号的全部会话。失效的理由与改密相同——凭据换了，别处手里的旧会话就不能继续用；
 * 差异在于本人改密保留当前会话（他正在用），管理员重置则把目标账号全部踢掉（其中没有「他自己」）。
 *
 * <p>不开事务：只有一条 UPDATE，会话失效与会话标记都不是事务资源。顺序上先落库再失效：
 * 反过来时，失效成功而密码没写进去，用户被踢下线、密码却还是旧的，只能由管理员再点一次。
 *
 * <p><b>留痕，但不留凭据。</b>审计动作是 {@code UPDATE}，摘要「重置密码 {登录名}」，
 * 而 {@code before} 与 {@code after} 都是 {@code null}：旧哈希与新哈希都不能进审计表
 * （只追加、不可修改，写进去就撤不回来），明文、长度、首字符这类能缩小搜索空间的信息同样不写。
 * 「改了什么」在这里只能是一句话，这是刻意的——能记的是「谁在什么时候重置了谁的密码」。
 */
@Service
public class UserPasswordResetService {

    /** 密码规则无状态、不依赖容器，随用例直接持有；放进容器只会让一份纯规则多出装配失败的可能。 */
    private static final PasswordPolicy PASSWORD_POLICY = new PasswordPolicy();

    private final UserQuery userQuery;
    private final UserAccountUpdater userAccountUpdater;
    private final PasswordHasher passwordHasher;
    private final SessionInvalidator sessionInvalidator;
    private final CurrentSession currentSession;
    private final AuditRecorder auditRecorder;

    public UserPasswordResetService(UserQuery userQuery,
                                    UserAccountUpdater userAccountUpdater,
                                    PasswordHasher passwordHasher,
                                    SessionInvalidator sessionInvalidator,
                                    CurrentSession currentSession,
                                    AuditRecorder auditRecorder) {
        this.userQuery = userQuery;
        this.userAccountUpdater = userAccountUpdater;
        this.passwordHasher = passwordHasher;
        this.sessionInvalidator = sessionInvalidator;
        this.currentSession = currentSession;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 用管理员指定的密码重置目标账号，并要求其下次登录后改密。
     *
     * @param command 重置入参
     * @throws BizException 当前会话无效（40100）；目标账号是操作人自己（40900）；
     *                      目标账号不存在（40400）；新密码不满足密码策略（40000）
     */
    public void reset(ResetUserPasswordCommand command) {
        long operatorId = currentSession.currentUserId()
                // 鉴权过滤器已拦过未登录请求，走到这里取不到会话只能是账号在请求途中被删除或令牌已失效；
                // 此时不能继续：updated_by 会写成空值，事后无从定位这次重置是谁做的
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        long targetUserId = command.userId();
        if (targetUserId == operatorId) {
            throw BizException.conflict("不能重置自己的密码，请通过「修改密码」并提供当前密码");
        }

        // 读详情只为拿登录名：审计按登录名追溯，而主键是一串在界面上不出现的数字。
        // 顺带回答「账号在不在」，因此不再单发一次存在性查询
        UserProfile current = userQuery.findById(targetUserId)
                .orElseThrow(() -> BizException.notFound("用户不存在"));

        List<PasswordRuleViolation> violations = PASSWORD_POLICY.check(command.newPassword());
        if (!violations.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, PASSWORD_POLICY.describe(violations));
        }

        Instant now = Instant.now();
        // 明文到此为止：跨层传递的只有哈希，日志与异常里都不会出现临时口令
        userAccountUpdater.resetPassword(targetUserId, passwordHasher.hash(command.newPassword()), now, operatorId);
        sessionInvalidator.invalidateAllOf(targetUserId);

        String loginName = current.summary().loginName();
        // 前后快照都为 null：审计里不留任何与密码有关的值，见类注释
        auditRecorder.record(new AuditEntry(AuditBizType.USER, loginName, AuditAction.UPDATE,
                "重置密码 " + loginName, null, null));
    }
}

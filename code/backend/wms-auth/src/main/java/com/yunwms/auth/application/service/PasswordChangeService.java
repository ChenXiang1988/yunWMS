package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.ChangePasswordCommand;
import com.yunwms.auth.domain.model.PasswordPolicy;
import com.yunwms.auth.domain.model.PasswordRuleViolation;
import com.yunwms.auth.domain.model.UserAccount;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.PasswordHasher;
import com.yunwms.auth.domain.port.UserAccountRepository;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 本人改密的用例，也是待改密状态下唯一被放行的写操作。
 *
 * <p>判定顺序固定为「取会话用户 → 查账号 → 判锁定期 → 验旧密码 → 校验新密码策略 → 新旧相同判定
 * → 落库 → 清改密标记 → 清失败状态」。
 *
 * <p>旧密码先于策略校验：密码策略的提示会暴露「哪些规则不满足」，若在验明本人之前就返回，
 * 拿着一张待改密令牌的人可以先用错误旧密码刷出全部规则，等于把口令策略当公共信息送出去。
 *
 * <p>锁定期先于验密码：锁定是主体级状态，不是登录接口专属。登录拦住了、这里不拦，
 * 持有有效令牌的人就多出一条不限次数的试密码通道，连续 5 次锁定形同虚设；
 * 规格要求锁定期间即使密码正确也拒绝，因此这里不看密码对错，一律拒绝。
 *
 * <p>旧密码错误与登录共用同一套失败计数（见 {@link FailedAttemptRecorder}）：
 * 只拒绝、不计数，正是本接口原先可以被用来穷举密码的原因。
 *
 * <p>不开事务：改密只写一条 UPDATE（哈希与改密标记同语句），失败计数与失败状态清理各是一条独立的单行 UPDATE，
 * 会话标记不是事务资源。加一个空事务只会让人以为这里还有别的东西需要原子提交。
 */
@Service
public class PasswordChangeService {

    /** 密码规则无状态、不依赖容器，随用例直接持有即可；放进容器反而让一份纯规则多出装配失败的可能。 */
    private static final PasswordPolicy PASSWORD_POLICY = new PasswordPolicy();

    private final UserAccountRepository userAccountRepository;
    private final PasswordHasher passwordHasher;
    private final CurrentSession currentSession;
    private final FailedAttemptRecorder failedAttemptRecorder;

    public PasswordChangeService(UserAccountRepository userAccountRepository,
                                 PasswordHasher passwordHasher,
                                 CurrentSession currentSession,
                                 FailedAttemptRecorder failedAttemptRecorder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordHasher = passwordHasher;
        this.currentSession = currentSession;
        this.failedAttemptRecorder = failedAttemptRecorder;
    }

    /**
     * 修改当前登录用户的密码，并解除「需先改密」状态。
     *
     * @param command 旧密码与新密码
     * @throws BizException 会话无效或账号已删除（40100）、账号处于锁定期（40103）、
     *                      旧密码错误（40101）、新密码不合规或与旧密码相同（40000）
     */
    public void change(ChangePasswordCommand command) {
        long userId = currentSession.currentUserId()
                // 能走到这里的请求都已通过登录校验，取不到会话说明账号在请求途中被删除或令牌已失效
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        // 判定锁定与记录本次失败必须同出一个时刻，否则两次取时间之间锁定刚好成立，计数就会落进锁定期里
        Instant now = Instant.now();
        if (account.isLockedAt(now)) {
            // 锁定期内不验密码、不计数：这一轮已经在计数，累加只会把锁定无限延长；
            // 不验密码则是不把「密码是否已改对」透露给一个本就不该继续尝试的人
            throw new BizException(ErrorCode.LOGIN_ACCOUNT_LOCKED);
        }

        if (!passwordHasher.matches(command.oldPassword(), account.passwordHash())) {
            // 先计数再抛：顺序反了会在计数失败时吞掉原始的「密码不正确」，
            // 用户看到的就成了一条数据库异常，而真正的拒绝原因丢失
            failedAttemptRecorder.record(userId, now);
            // 用 40101 与登录凭据错误对齐，前端可按同一类「凭据不对」处理；
            // 文案换成「当前密码不正确」：此处用户已登录，不存在账号枚举问题，笼统的「登录名或密码不正确」只会让人困惑
            throw new BizException(ErrorCode.LOGIN_FAILED, "当前密码不正确");
        }

        List<PasswordRuleViolation> violations = PASSWORD_POLICY.check(command.newPassword());
        if (!violations.isEmpty()) {
            // 一次返回全部违规项：改一条错一条的提示等于让用户反复试
            throw new BizException(ErrorCode.BAD_REQUEST, PASSWORD_POLICY.describe(violations));
        }

        if (passwordHasher.matches(command.newPassword(), account.passwordHash())) {
            // 必须拿新明文比旧哈希：BCrypt 每次加盐，新旧哈希串永不相等，比串会永远判成「不相同」
            throw new BizException(ErrorCode.BAD_REQUEST, "新密码不能与当前密码相同");
        }

        userAccountRepository.updatePassword(userId, passwordHasher.hash(command.newPassword()), now);
        // 先落库再清标记：写库失败时保持拦截，用户重试一次即可；顺序反过来会留下
        // 「会话已放行、库里仍是初始密码」的状态，初始密码从此不再被拦
        currentSession.clearMustChangePassword();
        // 改密成功是这一轮「连续失败」的终点：留着计数，用户下一次输错就离锁定更近一步。
        // 只清失败状态、不写 last_login_at——改密不是登录，写登录时间会把审计里的登录时刻改成改密时刻
        userAccountRepository.clearFailureState(userId);
    }
}

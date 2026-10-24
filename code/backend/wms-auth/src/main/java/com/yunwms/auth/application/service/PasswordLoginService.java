package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.PasswordLoginCommand;
import com.yunwms.auth.application.dto.AuthenticatedSubject;
import com.yunwms.auth.domain.model.FailedAttemptState;
import com.yunwms.auth.domain.model.SessionOrigin;
import com.yunwms.auth.domain.model.UserAccount;
import com.yunwms.auth.domain.port.PasswordHasher;
import com.yunwms.auth.domain.port.SessionTokenIssuer;
import com.yunwms.auth.domain.port.UserAccountRepository;
import com.yunwms.auth.domain.port.UserRoleQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 登录名加密码的登录用例。
 *
 * <p>两类主体走同一条链路，主体类型在查到账号后由 {@code sys_user.subject_type} 决定，
 * 因此不存在「走错入口就能绕开规则」的可能。
 *
 * <p>不开事务：失败计数与锁定截止时间在同一条 UPDATE 里完成，成功登录也只写一处，
 * 没有任何跨语句需要原子提交的状态；加一个空事务只会让人误以为登录做了更多持久化。
 */
@Service
public class PasswordLoginService {

    private static final Logger log = LoggerFactory.getLogger(PasswordLoginService.class);

    /** 连续失败达到该次数即锁定，对应规格「连续 5 次密码错误后锁定」。阈值只在这里定义，SQL 通过参数接收。 */
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    /**
     * 失败计数窗口，对应规格「同一主体在 30 分钟内连续 5 次输入错误密码」。
     *
     * <p>与锁定时长是两个独立的规则，只是当前取值相同：把窗口写成 {@link #LOCK_DURATION}
     * 会让调整锁定时长时顺手改掉窗口，而这两条规则在规格里是分开表述的。
     */
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(30);

    /** 锁定时长，对应规格「锁定该主体 30 分钟」。 */
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private final UserAccountRepository userAccountRepository;
    private final UserRoleQuery userRoleQuery;
    private final PasswordHasher passwordHasher;
    private final SessionTokenIssuer sessionTokenIssuer;

    public PasswordLoginService(UserAccountRepository userAccountRepository,
                                UserRoleQuery userRoleQuery,
                                PasswordHasher passwordHasher,
                                SessionTokenIssuer sessionTokenIssuer) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleQuery = userRoleQuery;
        this.passwordHasher = passwordHasher;
        this.sessionTokenIssuer = sessionTokenIssuer;
    }

    /**
     * 校验凭据并签发令牌。
     *
     * <p>判定顺序固定为「查账号 → 清已过期的锁定 → 验密码 → 查状态 → 查锁定」，不能调换：
     * 一旦先报状态，「账号已停用」「账号已锁定」就成了账号是否存在的探针，登录框会被用来枚举账号。
     * 先验密码，状态类提示就只对拿到正确密码的人可见——锁定期内提交错误密码同样只得到凭据错误。
     *
     * @param command 登录入参
     * @return 令牌与主体信息
     * @throws BizException 凭据错误、账号停用或账号处于锁定期；凭据错误与账号不存在共用同一错误码与文案
     */
    public AuthenticatedSubject login(PasswordLoginCommand command) {
        Instant now = Instant.now();
        // 登录名两侧空格一律忽略；密码不处理，空格在密码里是合法字符
        UserAccount account = userAccountRepository.findByLoginName(command.loginName().strip())
                .orElseThrow(PasswordLoginService::credentialsRejected);

        if (account.lockedUntil() != null && !account.isLockedAt(now)) {
            // 上一轮锁定已过期：先归零再校验。不清零的话，用户熬过 30 分钟后只要再错一次就立刻又被锁 30 分钟，
            // 惩罚没有停止条件；规格的「连续 5 次」应随这一次锁定结束而重新开始。
            // 归零同时清掉失败窗口起点，下一轮的 30 分钟从这里重新算。
            userAccountRepository.clearExpiredLock(account.id(), now);
        }

        if (!passwordHasher.matches(command.password(), account.passwordHash())) {
            // 锁定期内不再累加：这一轮已经在计数，此刻的失败不属于任何新一轮，累加只会把锁定无限延长
            if (!account.isLockedAt(now)) {
                recordFailure(account.id(), now);
            }
            throw credentialsRejected();
        }
        if (account.isDisabled()) {
            throw new BizException(ErrorCode.LOGIN_ACCOUNT_DISABLED);
        }
        if (account.isLockedAt(now)) {
            throw new BizException(ErrorCode.LOGIN_ACCOUNT_LOCKED);
        }

        List<String> roles = userRoleQuery.findActiveRoleCodesByUserId(account.id());
        // 先落库再签发：写失败时客户端拿不到令牌，避免出现「登录时间没记上但会话已建立」
        userAccountRepository.recordSuccessfulLogin(account.id(), now);
        String token = sessionTokenIssuer.issue(account,
                new SessionOrigin(now, command.sourceAddress(), command.clientIdentifier()));
        return new AuthenticatedSubject(token, account.subjectType(), account.ownerId(),
                account.displayName(), roles);
    }

    /**
     * 累加一次连续失败，并在本次尝试触发锁定时留日志。
     *
     * <p>用数据库返回的累加结果判断是否触发锁定，而不是用应用侧算出的次数：并发下应用侧的数字可能落后，
     * 日志里就会写错「是谁把账号试锁的」。
     *
     * <p>窗口起点由本方法算好后传给数据库，而「是否还在窗口内」由数据库在同一条语句里判定：
     * 应用侧先查上次失败时间再决定加不加，并发下两个请求会读到同一个旧时间、各自从 1 重新计数。
     * 三个时间参数同出一个时钟（本方法的 {@code now}），避免与库内 {@code now()} 混用后窗口被算偏。
     */
    private void recordFailure(long userId, Instant now) {
        FailedAttemptState failure = userAccountRepository.recordFailedAttempt(
                userId, MAX_CONSECUTIVE_FAILURES, now.minus(FAILURE_WINDOW), now, now.plus(LOCK_DURATION));
        if (failure.lockedUntil() != null && failure.lockedUntil().isAfter(now)) {
            log.warn("连续密码错误达到 {} 次，账号已锁定：userId={}, failedAttempts={}, lockedUntil={}",
                    MAX_CONSECUTIVE_FAILURES, userId, failure.failedAttempts(), failure.lockedUntil());
        }
    }

    private static BizException credentialsRejected() {
        return new BizException(ErrorCode.LOGIN_FAILED);
    }
}

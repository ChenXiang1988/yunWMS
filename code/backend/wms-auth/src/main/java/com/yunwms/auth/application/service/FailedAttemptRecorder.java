package com.yunwms.auth.application.service;

import com.yunwms.auth.domain.model.FailedAttemptState;
import com.yunwms.auth.domain.model.LoginProtectionPolicy;
import com.yunwms.auth.domain.port.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 把一次密码校验失败累加进主体的连续失败次数，并在本次尝试触发锁定时留日志。
 *
 * <p>登录与本人改密共用这一处实现：两条链路都在拿用户输入的密码与库内哈希比对，任何一条不计入，
 * 锁定策略就多一条旁路。参数也由本类统一装配——「阈值、窗口起点、锁定截止时间分别取哪个规则」
 * 一旦在两个用例里各写一遍，两处迟早对不上，而这类偏差在代码阅读时几乎看不出来。
 *
 * <p>用数据库返回的累加结果判断是否触发锁定，而不是用应用侧算出的次数：并发下应用侧的数字可能落后，
 * 日志里就会写错「是谁把账号试锁的」。
 *
 * <p>窗口判定由数据库在同一条语句里完成，不给应用侧先读再判的机会：
 * 应用侧先查上次失败时间再决定加不加，并发下两个请求会读到同一个旧时间、各自从 1 重新计数。
 * 三个时间参数同出一个时钟（本方法的 {@code now}），避免与库内 {@code now()} 混用后窗口被算偏。
 */
@Service
public class FailedAttemptRecorder {

    private static final Logger log = LoggerFactory.getLogger(FailedAttemptRecorder.class);

    private final UserAccountRepository userAccountRepository;

    public FailedAttemptRecorder(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * 累加一次连续失败。
     *
     * <p>「锁定期内不再累加」由调用方判定：这一轮已经在计数，此刻的失败不属于任何新一轮，
     * 累加只会把锁定无限延长。调用方在验密码之前就知道是否处于锁定期，本类不再查一次库，
     * 否则同一次请求里会出现两个时刻的锁定状态。
     *
     * @param userId 用户标识
     * @param now    本次失败时刻，也必须用于锁定状态判定，保证「判定是否已锁」与「记录本次失败」是同一时刻
     */
    public void record(long userId, Instant now) {
        FailedAttemptState failure = userAccountRepository.recordFailedAttempt(
                userId,
                LoginProtectionPolicy.MAX_CONSECUTIVE_FAILURES,
                LoginProtectionPolicy.windowStartOf(now),
                now,
                LoginProtectionPolicy.lockUntilOf(now));
        if (failure.lockedUntil() != null && failure.lockedUntil().isAfter(now)) {
            log.warn("连续密码错误达到 {} 次，账号已锁定：userId={}, failedAttempts={}, lockedUntil={}",
                    LoginProtectionPolicy.MAX_CONSECUTIVE_FAILURES, userId,
                    failure.failedAttempts(), failure.lockedUntil());
        }
    }
}

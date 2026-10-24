package com.yunwms.auth.domain.port;

import com.yunwms.auth.domain.model.FailedAttemptState;
import com.yunwms.auth.domain.model.UserAccount;

import java.time.Instant;
import java.util.Optional;

/**
 * 登录主体的读写入口。
 *
 * <p>只暴露登录链路需要的动作。密码变更、启停用属于用户管理，不在本接口里扩张，
 * 否则这里会变成整张表的 CRUD 入口，谁都能绕过用例直接改库。
 */
public interface UserAccountRepository {

    /**
     * 按登录名查账号，忽略大小写（登录名唯一索引建在 {@code lower(login_name)} 上）。
     *
     * <p>软删除的账号视为不存在：删除后的账号不允许再登录。
     *
     * @param loginName 登录名，调用方需保证非空
     * @return 账号；不存在时为空
     */
    Optional<UserAccount> findByLoginName(String loginName);

    /**
     * 清理已过期的锁定：连续失败次数归零、锁定截止时间与失败窗口起点一并清空。
     *
     * <p>只清理确实过期的：锁定仍在有效期内时不得改动，否则一次误调用就等于提前解锁。
     * 并发下第二次清理命中零行是正常结果，不按失败处理。
     *
     * <p>失败窗口起点必须一起清空：留着上一轮的残留时间，下一轮的第一次失败会被判成「仍在窗口内」，
     * 在一轮锁定结束时本应重新计数的窗口就没有重新开始。
     *
     * @param userId 用户标识
     * @param now    判定时刻
     */
    void clearExpiredLock(long userId, Instant now);

    /**
     * 记录一次密码失败；连续失败达到阈值时，在同一语句内写入锁定截止时间。
     *
     * <p>计数由数据库原子累加，本接口不提供「读出来加一写回去」的办法：后者在并发下会丢计数，
     * 实际允许的尝试次数会多于阈值。
     *
     * <p>「上一次失败是否还在窗口内」同样由数据库判定，不接受应用侧算好的次数：
     * 应用侧要先读时间再决定加不加，并发下两个请求会读到同一个旧时间、各自从 1 重新计数。
     *
     * @param userId            用户标识
     * @param lockAfterFailures 触发锁定的连续失败次数
     * @param windowStart       计数窗口起点；上次失败早于该时刻则本次从 1 重新计数
     * @param failedAt          本次失败时刻，作为下一轮的窗口判定依据
     * @param lockUntil         达到阈值时写入的锁定截止时间
     * @return 累加后的失败次数与锁定截止时间
     * @throws IllegalStateException 账号在登录过程中已不存在，没有可累加的行
     */
    FailedAttemptState recordFailedAttempt(long userId, int lockAfterFailures,
                                           Instant windowStart, Instant failedAt, Instant lockUntil);

    /**
     * 记录成功登录：写登录时间，并把连续失败次数归零、锁定截止时间与失败窗口起点清空。
     *
     * <p>归零与清空放在同一条语句里，因为成功登录就是「连续失败」这一轮的终点：
     * 留着旧计数，用户下一次输错就会被立刻锁定，惩罚没有起点也没有终点；
     * 留着旧的失败时间，下一轮的窗口会从上一轮算起，等于把一个早就过期的窗口续上。
     *
     * @param userId  用户标识
     * @param loginAt 登录时刻
     * @throws IllegalStateException 账号在登录过程中已不存在，此时不允许继续签发令牌
     */
    void recordSuccessfulLogin(long userId, Instant loginAt);
}

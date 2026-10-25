package com.yunwms.auth.domain.model;

import java.time.Duration;
import java.time.Instant;

/**
 * 登录保护规则：连续密码错误的阈值、计数窗口与锁定时长。
 *
 * <p>数值来自规格「密码策略与登录保护」：同一主体在 30 分钟内连续 5 次输入错误密码，锁定 30 分钟。
 * 锁定是主体级状态而不是登录接口专属：登录与本人改密都在拿密码做校验，两处必须按同一套阈值与窗口计数。
 * 只在一处计数的话，另一个入口就是现成的旁路——改密接口本来就在让用户反复输入密码，
 * 不计数等于给了一张不限次数的试密码凭据。规则因此只在这里定义一次，两处用例都从本类取值。
 *
 * <p>窗口与锁定时长是两个独立字段，虽然当前取值相同：它们在规格里是两句独立的话，
 * 合并成一个字段之后，调整锁定时长会顺手改掉计数窗口，而窗口一变，「连续 5 次」的边界就悄悄松了。
 * 两个字段各自带推导方法，调用方不再自己写 {@code now.minus(...)}，也就没有传错字段的机会。
 */
public final class LoginProtectionPolicy {

    /** 连续失败达到该次数即锁定，对应规格「连续 5 次密码错误后锁定」。 */
    public static final int MAX_CONSECUTIVE_FAILURES = 5;

    /** 失败计数窗口，对应规格「同一主体在 30 分钟内连续 5 次输入错误密码」。与 {@link #LOCK_DURATION} 独立。 */
    public static final Duration FAILURE_WINDOW = Duration.ofMinutes(30);

    /** 锁定时长，对应规格「锁定该主体 30 分钟」。与 {@link #FAILURE_WINDOW} 独立。 */
    public static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    private LoginProtectionPolicy() {
        // 只承载规则与推导，不持有状态：一旦可实例化，就会出现「按实例传阈值」的写法，规则又变成可变的
    }

    /**
     * 计算本次失败对应的计数窗口起点。
     *
     * @param failedAt 本次失败时刻，必须与写入库内的失败时刻同源，否则窗口会被算偏
     * @return 窗口起点；库内上次失败早于该时刻时，本次从 1 重新计数
     */
    public static Instant windowStartOf(Instant failedAt) {
        return failedAt.minus(FAILURE_WINDOW);
    }

    /**
     * 计算本次失败触发锁定时写入的截止时间。
     *
     * @param failedAt 本次失败时刻，需与 {@link #windowStartOf(Instant)} 使用同一个时刻
     * @return 锁定截止时间
     */
    public static Instant lockUntilOf(Instant failedAt) {
        return failedAt.plus(LOCK_DURATION);
    }
}

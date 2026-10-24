package com.yunwms.auth.domain.model;

import java.time.Instant;

/**
 * 一次密码失败的累加结果。
 *
 * <p>由数据库在累加的同一条语句里返回，不做「再查一次」：事后查询会读到别的并发请求写过的新值，
 * 拿到的是别人那一次的锁定状态，而不是本次尝试造成的状态。
 *
 * @param failedAttempts 累加后的连续失败次数
 * @param lockedUntil    锁定截止时间；本次未触发锁定时为空
 */
public record FailedAttemptState(int failedAttempts, Instant lockedUntil) {
}

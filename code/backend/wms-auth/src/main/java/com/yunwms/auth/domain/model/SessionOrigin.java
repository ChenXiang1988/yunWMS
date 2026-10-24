package com.yunwms.auth.domain.model;

import java.time.Instant;

/**
 * 会话来源事实。
 *
 * <p>规格要求记录登录时间、来源地址与客户端标识。库里没有会话表，这组事实随令牌存在会话里：
 * 同一次登录的审计信息与令牌同生共死，不会出现「令牌还在但查不到是哪台机器登的」。
 *
 * @param loginAt          登录发生的时刻，按 UTC 记录
 * @param sourceAddress    发起登录的来源地址，取自 TCP 连接对端
 * @param clientIdentifier 客户端标识，取自 User-Agent；非浏览器客户端可能为空
 */
public record SessionOrigin(Instant loginAt, String sourceAddress, String clientIdentifier) {
}

package com.yunwms.auth.application.command;

/**
 * 管理员重置他人密码的入参。
 *
 * <p>没有旧密码：管理员不知道也不需要知道本人的口令。正因如此，被重置的对象不能是操作人自己
 * ——那会变成一条「不用验旧密码就能改自己密码」的通道，把本人改密的旧密码校验整个绕过去。
 *
 * @param userId      被重置的用户标识
 * @param newPassword 新密码明文，仅在本进程内存中短暂存在，通过策略校验后只落哈希
 */
public record ResetUserPasswordCommand(long userId, String newPassword) {
}

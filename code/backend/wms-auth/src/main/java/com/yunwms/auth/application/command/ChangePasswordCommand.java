package com.yunwms.auth.application.command;

/**
 * 本人改密的入参。
 *
 * <p>不含用户标识：改谁的密码由会话决定，不由请求体决定。让前端传标识等于让前端选择目标账号，
 * 一个拼错的字段就能把「改自己的密码」变成「改别人的密码」。
 *
 * @param oldPassword 当前密码明文，用于确认改密的是本人；只在内存中存活，不写日志、不落库
 * @param newPassword 新密码明文，通过策略校验后才生成哈希落库
 */
public record ChangePasswordCommand(String oldPassword, String newPassword) {
}

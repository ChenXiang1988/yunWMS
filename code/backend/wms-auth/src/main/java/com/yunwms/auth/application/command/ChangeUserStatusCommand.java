package com.yunwms.auth.application.command;

import com.yunwms.auth.domain.model.UserStatus;

/**
 * 启用或停用账号的入参。
 *
 * <p>目标状态由请求体给出，而不是拆成 {@code /enable} 与 {@code /disable} 两个接口：
 * 两者改的是同一列、做的是同一件事，拆开只是让前端多一个分支，并且要想清楚「已经启用时再启用」
 * 该算什么。
 *
 * <p>同一状态重复提交返回成功而不是报错（与 {@code docs/api-convention.md} 第七节的状态流转
 * 口径一致）：管理员看到的是「已经是这个状态」，那正是他想要的结果。
 *
 * @param userId 目标用户标识
 * @param status 目标状态：{@code ACTIVE} 启用，{@code DISABLED} 停用
 */
public record ChangeUserStatusCommand(long userId, UserStatus status) {
}

package com.yunwms.auth.interfaces.controller.response;

/**
 * 角色列表的一行。
 *
 * <p>{@code userCount} 与 {@code permissionCount} 由同一条 SQL 聚合得出（不是逐行再查），
 * 前端可以用 {@code userCount} 判断「该角色是否还能删除」的提示文案，不必再发一次请求。
 *
 * @param id              角色标识，字符串形式
 * @param code            角色编码
 * @param name            角色名称
 * @param builtin         是否内置角色；内置角色不可编辑、不可删除
 * @param status          角色状态编码，取值见后端的角色状态枚举名
 * @param userCount       已分配该角色的用户数
 * @param permissionCount 已勾选的菜单与权限点数量
 * @param remark          备注，可为 {@code null}
 */
public record RoleListItemResponse(String id,
                                   String code,
                                   String name,
                                   boolean builtin,
                                   String status,
                                   long userCount,
                                   long permissionCount,
                                   String remark) {
}

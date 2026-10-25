package com.yunwms.auth.domain.model;

/**
 * 角色的最小身份信息：标识，以及它是否内置。
 *
 * <p>只在「分配角色」这条链路上用：那里要判定的只有两件事——请求里的角色标识是否都存在，
 * 以及替换之后目标账号是否还持有内置角色。为此去取 {@link RoleSummary} 会顺带算出占用人数与
 * 授权数量（两次聚合子查询），而这两个数字在这条链路上没有消费者。
 *
 * @param id      角色标识
 * @param builtin 是否内置角色；内置角色是超级管理员权限的唯一来源
 */
public record RoleIdentity(long id, boolean builtin) {
}

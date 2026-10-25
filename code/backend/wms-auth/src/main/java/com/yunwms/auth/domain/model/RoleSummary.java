package com.yunwms.auth.domain.model;

/**
 * 角色列表与详情共用的读模型：角色本身，加上它当前的占用人数与授权数量。
 *
 * <p>三个数量放在同一个模型里，是因为它们必须来自同一次查询：分成「先读角色、再逐个读计数」
 * 会让列表页的 SQL 条数随角色数量线性增长（10 个角色 21 次查询），而且分页边界上两次读的时间点
 * 不同，可能出现「这一页有 11 行、总数写 10」这种无法解释的结果。
 *
 * <p>{@code userCount} 按 {@code sys_user_role} 的关联行计。当前没有任何入口会逻辑删除用户，
 * 因此它等于「已分配该角色的用户数」；若将来引入用户逻辑删除，本口径需要连同
 * 「角色被占用时不可删除」的判定一起复核（见 {@code RoleRegistrar} 的说明）。
 *
 * <p>{@code permissionCount} 计的是 {@code sys_role_menu} 的行数，即菜单与操作两级的授权总数；
 * 规格把「菜单与操作两级」都称为权限点，所以它与详情里 {@code menuIds} 的条数一致。
 *
 * @param id              角色标识
 * @param code            角色编码
 * @param name            角色名称
 * @param builtin         是否内置角色；内置角色不可编辑、不可删除
 * @param status          角色状态
 * @param userCount       已分配该角色的用户数
 * @param permissionCount 已勾选的菜单与权限点数量
 * @param remark          备注，可为 {@code null}
 */
public record RoleSummary(long id,
                          String code,
                          String name,
                          boolean builtin,
                          RoleStatus status,
                          long userCount,
                          long permissionCount,
                          String remark) {
}

package com.yunwms.auth.domain.model;

/**
 * 角色的最小身份信息：标识、编码，以及它是否内置。
 *
 * <p>只在「分配角色」这条链路上用：那里要判定请求里的角色标识是否都存在、替换之后目标账号是否还
 * 持有内置角色，并把「改成了哪些角色」写进审计。三件事只需要这些字段——为此去取
 * {@link RoleSummary} 会顺带算出占用人数与授权数量（两次聚合子查询），而这两个数字在这条链路上
 * 没有消费者。
 *
 * <p>{@code code} 与 {@code id} 来自同一次查询：审计要的是编码，而分配要的是标识，
 * 分成两次查询只会让两个字段之间多出一个「谁先谁后」的不一致窗口。
 *
 * @param id      角色标识
 * @param code    角色编码；写进审计快照，使日志答得出「改成了哪些角色」
 * @param builtin 是否内置角色；内置角色是超级管理员权限的唯一来源
 */
public record RoleIdentity(long id, String code, boolean builtin) {
}

package com.yunwms.auth.domain.model;

/**
 * 一条「用户 → 角色」的授权记录，带角色的编码与标识。
 *
 * <p>列表需要编码（展示给管理员看的标识），编辑表单需要标识（回填勾选状态），
 * 因此一次查询同时带回两者：分两次查会让「列表显示的角色」与「表单勾选的角色」有时间差，
 * 而管理员正是在比对这两处时才需要它们一致。
 *
 * <p>已逻辑删除的角色不出现在这里（对管理界面已无意义），停用中的角色<b>会出现</b>：
 * 列表要能看出「这个账号的角色已被停用」，那正是权限失效的原因。
 *
 * @param userId   用户标识
 * @param roleId   角色标识
 * @param roleCode 角色编码
 */
public record UserRoleGrant(long userId, long roleId, String roleCode) {
}

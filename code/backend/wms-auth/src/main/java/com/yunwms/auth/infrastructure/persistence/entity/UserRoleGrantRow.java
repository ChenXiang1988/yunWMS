package com.yunwms.auth.infrastructure.persistence.entity;

/**
 * 「用户 → 角色」授权的行投影：一次查询带回整页用户的全部授权。
 *
 * <p>查询投影，不声明 {@code @TableName}、不参与写入。{@code sys_user_role} 本身没有 {@code code} 列，
 * 它来自与 {@code sys_role} 的连接——这条 SQL 的存在理由就是「一页用户的角色用一次查询取完」，
 * 而不是每行用户再查一次。
 */
public class UserRoleGrantRow {

    private Long userId;
    private Long roleId;
    private String roleCode;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }
}

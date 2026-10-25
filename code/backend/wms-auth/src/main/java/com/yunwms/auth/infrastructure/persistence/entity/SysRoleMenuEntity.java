package com.yunwms.auth.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * {@code sys_role_menu} 的表映射，字段与列一一对应。
 *
 * <p>主键是 (role_id, menu_id) 复合主键，而 MyBatis-Plus 只支持单主键。这里刻意不声明
 * {@code @TableId}，与 {@link SysUserRoleEntity} 同一处理：随便挑一个列当主键会让
 * {@code deleteById} 之类似是而非的方法删掉整片授权。本类只用于「按角色查授权」与「按角色写授权」。
 *
 * <p>代价是启动时 MyBatis-Plus 会打 WARN 说明本表没有主键。
 * <strong>不要为了消除告警补一个 {@code @TableId}</strong>：那等于把删错整片授权的口子重新打开。
 */
@TableName("sys_role_menu")
public class SysRoleMenuEntity {

    private Long roleId;
    private Long menuId;
    private Instant createdAt;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

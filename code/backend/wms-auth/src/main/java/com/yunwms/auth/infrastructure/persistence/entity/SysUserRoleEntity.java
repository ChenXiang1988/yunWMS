package com.yunwms.auth.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * {@code sys_user_role} 的表映射，字段与列一一对应。
 *
 * <p>主键是 (user_id, role_id) 复合主键，而 MyBatis-Plus 只支持单主键。这里刻意不声明
 * {@code @TableId}：随便挑一个列当主键，会让 {@code deleteById} 之类似是而非的方法删掉整片关联，
 * 本类只用于按用户查关联，不参与按主键的增删改。
 *
 * <p>代价是启动时 MyBatis-Plus 会打两条 WARN，说明「本表没有主键、不可用 xxById 系列方法」。
 * 这正是这里想要的效果：<strong>不要为了消除告警补一个 {@code @TableId}</strong>，
 * 那等于把「删错整片关联」的口子重新打开。
 */
@TableName("sys_user_role")
public class SysUserRoleEntity {

    private Long userId;
    private Long roleId;
    private Instant createdAt;

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

package com.yunwms.auth.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * {@code sys_data_scope_owner} 的表映射：{@code CUSTOM} 范围下的指定货主明细。
 *
 * <p><b>不映射主键 {@code id}</b>，与 {@link SysRoleMenuEntity} 同一处理，理由有两条：
 * <ul>
 *   <li>业务上唯一的是 {@code (role_id|user_id, owner_id)}，{@code id} 只是自增代理键；
 *       映射了它，{@code deleteById} 之类似是而非的方法就能删掉一行明细，而明细少一行的表现是
 *       「范围悄悄变小」，没有任何报错；</li>
 *   <li>库里的 {@code id} 是 {@code GENERATED ALWAYS AS IDENTITY}，映射成实体字段后
 *       MyBatis-Plus 会把它写进 INSERT 的列清单（值 {@code null}），PostgreSQL 直接拒绝
 *       「往 GENERATED ALWAYS 列插非默认值」。</li>
 * </ul>
 * 因此全部删除都必须显式按 {@code role_id} 或 {@code user_id} 条件进行。
 *
 * <p><b>没有 {@code deleted_at} 字段，这是表结构的事实：</b>该表没有软删除列，因此本类不参与
 * MyBatis-Plus 的逻辑删除，删除语句就是物理 {@code DELETE}。这与 {@code sys_role} 相反，
 * 替换明细时不能照抄那边的软删除写法。
 *
 * <p>{@code role_id} 与 {@code user_id} 互斥（库里有 {@code ck_scope_owner_subject} 兜底）：
 * 一行只能属于角色或用户之一，写入时两个字段只设其中一个。
 */
@TableName("sys_data_scope_owner")
public class SysDataScopeOwnerEntity {

    private Long roleId;
    private Long userId;
    private Long ownerId;
    private Instant createdAt;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

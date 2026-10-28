package com.yunwms.master.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * {@code owner_contact} 的表映射，字段与 {@code V007__owner_contact.sql} 的列一一对应。
 *
 * <p>时间列一律用 {@link Instant}：库里是 {@code timestamptz}、以 UTC 存储，
 * 只有 Instant 不携带隐式时区，换成 LocalDateTime 会在转换时悄悄丢掉偏移（与
 * {@link OwnerEntity} 同一理由）。
 *
 * <p>{@code deleted_at} 必须映射：它参与 MyBatis-Plus 的逻辑删除（见 {@code application.yml} 的
 * {@code logic-delete-field}）。漏掉它，已软删除的联系人会通过 {@code selectById} 重新出现在详情里，
 * 而 {@code OwnerContactMapper} 的三条写语句都带 {@code deleted_at IS NULL}——那次编辑既不生效
 * 也不报错，是最难排查的一类静默失败。
 *
 * <p>主键用 {@link IdType#AUTO}：列是 {@code GENERATED ALWAYS AS IDENTITY}，号由库生成。
 * 声明成其它类型会让 MyBatis-Plus 自己造一个号，回填到实体上的主键与库里那一行对不上，
 * 调用方随即拿着一个不存在的标识继续写关联数据。
 *
 * <p>{@code is_primary} 用 {@link Boolean} 而不是 {@code boolean}：本类的另一半用途是承载
 * 「待插入 / 待更新的值」，包装类型与其余列同构；而读侧取到 {@code null} 也不会被基本类型的
 * 默认 {@code false} 吞掉——列在库里是 {@code NOT NULL DEFAULT false}，取不到值说明这一行或
 * 这次投影有问题，由仓储显式决定怎么处理（与 {@code SysRoleEntity#builtin} 的选择一致）。
 */
@TableName("owner_contact")
public class OwnerContactEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private String name;
    private String roleType;
    private String phone;
    private String email;
    private Boolean isPrimary;
    private String remark;
    private Instant createdAt;
    private Long createdBy;
    private Instant updatedAt;
    private Long updatedBy;
    private Instant deletedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRoleType() {
        return roleType;
    }

    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}

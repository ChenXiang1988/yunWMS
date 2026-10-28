package com.yunwms.master.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * {@code owner_tax_registration} 的表映射，字段与 {@code V009__owner_tax_registration.sql} 的列一一对应。
 *
 * <p>时间列一律用 {@link Instant}：库里是 {@code timestamptz}、以 UTC 存储，只有 Instant 不携带
 * 隐式时区，换成 LocalDateTime 会在转换时悄悄丢掉偏移（与 {@link OwnerEntity} 同一理由）。
 *
 * <p>{@code deleted_at} 必须映射：它参与 MyBatis-Plus 的逻辑删除（见 {@code application.yml} 的
 * {@code logic-delete-field}）。漏掉它，已软删除的税号会通过 {@code selectById} 重新出现在详情里，
 * 而 {@code OwnerTaxMapper} 的三条写语句都带 {@code deleted_at IS NULL}——那次编辑既不生效
 * 也不报错，是最难排查的一类静默失败。
 *
 * <p>主键用 {@link IdType#AUTO}：列是 {@code GENERATED ALWAYS AS IDENTITY}，号由库生成。
 * 声明成其它类型会让 MyBatis-Plus 自己造一个号，回填到实体上的主键与库里那一行对不上，
 * 调用方随即拿着一个不存在的标识继续写关联数据。
 *
 * <p>{@code is_default} 用 {@link Boolean} 而不是 {@code boolean}：取不到值说明投影漏了列或
 * 数据库被绕过约束写坏，宁可让仓储在重建时当场失败，也不要用基本类型的默认 {@code false}
 * 把「默认税号」悄悄降级成「非默认」——那是页签上看不出来、却会让下游取错号码的一类错误。
 * 这一位与 {@link OwnerContactEntity#getIsPrimary()} 的处理相反，因为那一处降级成「非主」
 * 没有业务后果，而这里降级会让「默认税号」这一事实消失。
 */
@TableName("owner_tax_registration")
public class OwnerTaxEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private String country;
    private String province;
    private String taxType;
    private String taxNumber;
    private Boolean isDefault;
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

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getTaxType() {
        return taxType;
    }

    public void setTaxType(String taxType) {
        this.taxType = taxType;
    }

    public String getTaxNumber() {
        return taxNumber;
    }

    public void setTaxNumber(String taxNumber) {
        this.taxNumber = taxNumber;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
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

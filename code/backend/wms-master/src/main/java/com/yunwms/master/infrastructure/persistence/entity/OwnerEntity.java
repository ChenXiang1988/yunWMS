package com.yunwms.master.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * {@code owner} 的表映射，字段与 {@code V001__owner.sql} 的列一一对应。
 *
 * <p>时间列一律用 {@link Instant}：库里是 {@code timestamptz}、以 UTC 存储，
 * 只有 Instant 不携带隐式时区，换成 LocalDateTime 会在转换时悄悄丢掉偏移。
 *
 * <p>{@code deleted_at} 必须映射：它参与 MyBatis-Plus 的逻辑删除（见 {@code application.yml} 的
 * {@code logic-delete-field}）。漏掉它，软删除的货主会通过 {@code selectById} 重新出现在详情里。
 *
 * <p>注册地址在本类里是六个平铺字段（与列一一对应），而不是一个地址值对象：
 * 表映射类的职责是把列搬进搬出，任何嵌套结构都要额外说明「哪几列属于它」，
 * 而 address 的组装与校验属于 {@code com.yunwms.master.domain.model.OwnerAddress}。
 */
@TableName("owner")
public class OwnerEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String shortName;
    private String nameEn;
    private String externalCode;
    private String skuPrefix;
    private String ownerType;
    private String settlementEntity;
    private String status;
    private String country;
    private String province;
    private String city;
    private String district;
    private String postalCode;
    private String addressLine;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public String getExternalCode() {
        return externalCode;
    }

    public void setExternalCode(String externalCode) {
        this.externalCode = externalCode;
    }

    public String getSkuPrefix() {
        return skuPrefix;
    }

    public void setSkuPrefix(String skuPrefix) {
        this.skuPrefix = skuPrefix;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public String getSettlementEntity() {
        return settlementEntity;
    }

    public void setSettlementEntity(String settlementEntity) {
        this.settlementEntity = settlementEntity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
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

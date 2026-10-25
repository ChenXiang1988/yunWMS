package com.yunwms.auth.infrastructure.persistence.entity;

import java.time.Instant;

/**
 * {@code sys_user} 列表查询的行投影。
 *
 * <p>与 {@link SysUserEntity} 分开，且不声明 {@code @TableName}：本类只承接查询结果，不参与写入。
 * 列表页刻意不取 {@code password_hash}、{@code failed_attempts} 这些与展示无关的列，
 * 用完整实体承接会让「查了哪些列」退化成「实体有哪些字段」这种隐式约定。
 *
 * <p>{@code subjectType} 与 {@code status} 用 {@code String} 而不是枚举：MyBatis 的枚举映射遇到
 * 不认识的编码只会抛一条没有上下文的转换异常，而这里要的是「库里的编码是什么」这句话出现在
 * 错误信息里（转换与校验在仓储层做，见 {@code UserQueryImpl}）。
 */
public class UserSummaryRow {

    private Long id;
    private String loginName;
    private String displayName;
    private String subjectType;
    private Long ownerId;
    private String status;
    private Instant lastLoginAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLoginName() {
        return loginName;
    }

    public void setLoginName(String loginName) {
        this.loginName = loginName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}

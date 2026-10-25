package com.yunwms.auth.infrastructure.persistence.entity;

/**
 * 角色列表与详情查询的行投影：{@code sys_role} 的列，加上两个聚合计数。
 *
 * <p>与 {@link SysRoleEntity} 分开，且不声明 {@code @TableName}：本类只承接查询结果，不参与写入。
 * 用整行实体冒充投影会让「{@code userCount} 是算出来的、不是表里的列」这件事看不出来，
 * 下一个改这条 SQL 的人会以为它可以直接读。
 *
 * <p>字段一律用包装类型：计数在没有任何关联行时为 0，但「SQL 里漏了 coalesce」与「真的是 0」
 * 在基本类型上无法区分，包装类型让转换处能各自处理（见 {@code RoleQueryImpl}）。
 *
 * <p>{@code status} 用 {@code String} 而不是 {@code RoleStatus}：MyBatis 的枚举映射遇到不认识的
 * 编码只会抛一条没有上下文的转换异常，而这里要的是「库里的状态编码是什么」这句话出现在错误信息里。
 */
public class RoleSummaryRow {

    private Long id;
    private String code;
    private String name;
    private Boolean builtin;
    private String status;
    private String remark;
    private Long userCount;
    private Long permissionCount;

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

    public Boolean getBuiltin() {
        return builtin;
    }

    public void setBuiltin(Boolean builtin) {
        this.builtin = builtin;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getUserCount() {
        return userCount;
    }

    public void setUserCount(Long userCount) {
        this.userCount = userCount;
    }

    public Long getPermissionCount() {
        return permissionCount;
    }

    public void setPermissionCount(Long permissionCount) {
        this.permissionCount = permissionCount;
    }
}

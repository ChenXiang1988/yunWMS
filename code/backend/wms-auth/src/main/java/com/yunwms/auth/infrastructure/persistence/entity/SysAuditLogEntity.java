package com.yunwms.auth.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

/**
 * {@code sys_audit_log} 的表映射，字段与列一一对应。
 *
 * <p>同一类型兼作写入参数与查询结果：这张表只有一套列，写入与读取要填的就是同一批字段，
 * 拆成「写入实体 + 查询投影」只会得到两个必须同步修改的类，而漏改的那一处不会有编译错误。
 *
 * <p>{@code beforeValue} / {@code afterValue} 用 {@link String} 承接：{@code jsonb} 经 JDBC 以文本返回，
 * 用 {@code Map} 承接需要一个只服务这张表的 TypeHandler；由仓储层用已有的 {@code ObjectMapper}
 * 解析，解析失败还能带上日志标识（见 {@code AuditQueryImpl}）。
 *
 * <p>时间列用 {@link Instant}：库里是 {@code timestamptz}，只有 Instant 不携带隐式时区。
 *
 * <p>没有 {@code updated_at} / {@code deleted_at}：本表只追加，不修改、不删除，因此表结构里也没有
 * 这两列（见 {@code V021} 的注释）。也正因为如此，本类没有逻辑删除字段——{@code application.yml} 的
 * 全局逻辑删除配置对它不起作用，这是期望行为。
 */
@TableName("sys_audit_log")
public class SysAuditLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private String bizId;
    private String action;
    private Long operatorId;
    private String operatorName;
    private String operatorIp;
    private String sourcePortal;
    private String summary;
    private String beforeValue;
    private String afterValue;
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getOperatorIp() {
        return operatorIp;
    }

    public void setOperatorIp(String operatorIp) {
        this.operatorIp = operatorIp;
    }

    public String getSourcePortal() {
        return sourcePortal;
    }

    public void setSourcePortal(String sourcePortal) {
        this.sourcePortal = sourcePortal;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public void setBeforeValue(String beforeValue) {
        this.beforeValue = beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public void setAfterValue(String afterValue) {
        this.afterValue = afterValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

package com.yunwms.master.infrastructure.persistence.entity;

/**
 * {@code owner} 表的名称投影：只取 {@code id} 与 {@code name} 两列。
 *
 * <p>查询投影，不声明 {@code @TableName}、不参与写入（{@code docs/coding-java.md}「MyBatis-Plus」）。
 *
 * <p><b>为什么不复用 {@link OwnerEntity}：</b>需求只是「标识 → 名称」，而 {@code owner} 有二十多列，
 * 且整行读回要经过 {@code Owner} 聚合的重建校验。已软删除的历史数据可能在重建时因状态、前缀等
 * 不变量当场失败，而名称恰恰是这类数据仍然需要被显示的一个字段——把投影收窄到两列，
 * 显示用的查询就不会被与显示无关的规则拦住。
 */
public class OwnerNameRow {

    private Long id;
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

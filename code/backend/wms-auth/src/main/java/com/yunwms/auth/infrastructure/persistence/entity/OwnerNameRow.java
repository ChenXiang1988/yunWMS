package com.yunwms.auth.infrastructure.persistence.entity;

/**
 * {@code owner} 表的名称投影：只取 {@code id} 与 {@code name} 两列。
 *
 * <p>本模块没有 {@code owner} 的表映射，也不需要：这里要的只是「标识 → 名称」，
 * 而 {@code owner} 表有二十多列，造一个完整实体会让人误以为本模块可以按货主读写。
 *
 * <p>查询投影，不声明 {@code @TableName}、不参与写入。越界读取的边界见
 * {@link com.yunwms.auth.domain.port.OwnerNameQuery}。
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

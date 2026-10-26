package com.yunwms.auth.infrastructure.persistence.entity;

/**
 * 字典类型列表的行投影：{@code sys_dict} 的列，加上该类下的项数。
 *
 * <p>不声明 {@code @TableName}、不参与写入：本类只承接查询结果。{@code itemCount} 是聚合出来的、
 * 不是表里的列，用整行实体冒充投影会让下一个改这条 SQL 的人以为它可以直接读。
 *
 * <p>字段用包装类型：聚合计数在没有任何项时为 0，而「SQL 里漏了 coalesce」与「真的是 0」
 * 在基本类型上无法区分。
 *
 * <p>{@code status} 用 {@code String} 而不是枚举：MyBatis 的枚举映射遇到不认识的编码只会抛一条
 * 没有上下文的转换异常，而这里要的是「库里的状态编码是什么」出现在错误信息里。
 */
public class DictTypeRow {

    private String dictType;
    private String name;
    private String nameEn;
    private Long itemCount;
    private String status;

    public String getDictType() {
        return dictType;
    }

    public void setDictType(String dictType) {
        this.dictType = dictType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public Long getItemCount() {
        return itemCount;
    }

    public void setItemCount(Long itemCount) {
        this.itemCount = itemCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

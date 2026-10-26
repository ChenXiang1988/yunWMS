package com.yunwms.auth.infrastructure.persistence.entity;

/**
 * 字典项查询的行投影：{@code sys_dict_item} 的列。
 *
 * <p>不声明 {@code @TableName}、不参与写入：本批只有读，2.7b 的写入会另建与表一一对应的实体。
 * 现在就造一个只有读路径在用的实体，等于给「这张表怎么写」提前押一个没人验证过的形状。
 *
 * <p>{@code ext} 用 {@code String} 而不是 {@code Map}：{@code jsonb} 经 JDBC 以文本形态返回，
 * 用 {@code Map} 承接需要一个只服务这张表的 TypeHandler；由仓储层解析成 {@code Map} 只需
 * 一个已经存在的 {@code ObjectMapper}，解析失败还能带上项标识（见 {@code DictQueryImpl}）。
 *
 * <p>字段用包装类型，理由同 {@link DictTypeRow}：`null` 与默认值在转换处要能区分开。
 */
public class DictItemRow {

    private Long id;
    private String dictType;
    private String label;
    private String labelEn;
    private String value;
    private Integer sortNo;
    private String status;
    private String ext;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDictType() {
        return dictType;
    }

    public void setDictType(String dictType) {
        this.dictType = dictType;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getLabelEn() {
        return labelEn;
    }

    public void setLabelEn(String labelEn) {
        this.labelEn = labelEn;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getExt() {
        return ext;
    }

    public void setExt(String ext) {
        this.ext = ext;
    }
}

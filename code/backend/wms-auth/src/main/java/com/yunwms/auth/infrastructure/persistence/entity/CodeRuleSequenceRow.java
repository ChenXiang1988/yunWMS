package com.yunwms.auth.infrastructure.persistence.entity;

/**
 * {@code UPDATE ... RETURNING} 的行投影：一条规则本次分配到的流水号。
 *
 * <p>与整行实体分开，且不声明 {@code @TableName}：{@code sys_code_rule} 的
 * {@code name}、{@code remark}、审计列都不参与取号，用整行实体承接会让人分不清
 * 「哪些列是这次写操作的真值」。本类只出现取号真正要用的三列。
 *
 * <p>字段用包装类型而不是基本类型：语句命中零行时 MyBatis 返回 {@code null}，
 * 用基本类型就得先造一个对象再判空，反而多一步。
 */
public class CodeRuleSequenceRow {

    private String prefix;
    private Integer seqLength;
    private Long currentValue;

    /**
     * @return 编码前缀，可能为空串（规则允许不配前缀）
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * @param prefix 编码前缀，可能为空串
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    /**
     * @return 流水位数，库内由 {@code ck_rule_seq_length} 限定在 1..12
     */
    public Integer getSeqLength() {
        return seqLength;
    }

    /**
     * @param seqLength 流水位数
     */
    public void setSeqLength(Integer seqLength) {
        this.seqLength = seqLength;
    }

    /**
     * @return 本次分配到的流水号（即递增后的 {@code current_value}）
     */
    public Long getCurrentValue() {
        return currentValue;
    }

    /**
     * @param currentValue 本次分配到的流水号
     */
    public void setCurrentValue(Long currentValue) {
        this.currentValue = currentValue;
    }
}

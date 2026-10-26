package com.yunwms.auth.domain.exception;

/**
 * 取号失败。
 *
 * <p>两种原因都表示「这次没有拿到号」，但处置完全不同，因此用 {@link Reason} 区分：
 * 规则未配置是部署或数据缺失，补一条规则即可恢复；流水用尽是容量上限，必须人工调整规则
 * （加宽位数或调 {@code current_value}）才能继续。「编码规则没生效」这种笼统提示会把
 * 后者误导成前者，运维照着补一条规则只会撞上唯一索引。
 *
 * <p>为什么不用 {@code com.yunwms.web.exception.BizException}：
 * {@code docs/coding-project.md} 规定 {@code domain} 与 {@code infrastructure} 不得 import
 * {@code com.yunwms.web.*}——领域与持久化都不认识响应结构。这里表达的是「失败原因」，
 * 由调用方（接入接口的 application 层）翻译成错误码与 HTTP 响应。
 * 在该翻译落地前，本异常会被 {@code GlobalExceptionHandler} 的兜底分支当作未预期异常，
 * 以 50000 返回——这是「还没有接线」的症状，不是应该在本层兜住的东西。
 *
 * <p>放在 {@code domain/exception} 而不是 {@code domain/model} 或 {@code domain/port}：
 * 它不是聚合也不是值对象，更不是端口。新增这一个子包，是为了不让「domain 下再放什么」
 * 变成没有边界的问题。
 */
public class CodeRuleException extends RuntimeException {

    /**
     * 失败原因。
     *
     * <p>不入库、不出现在响应里，只用于调用方分支——枚举值改名不会影响任何已存数据。
     */
    public enum Reason {

        /** {@code sys_code_rule} 里没有这条规则，通常是规则未随迁移落库。 */
        NOT_CONFIGURED,

        /** 本次分配到的流水号位数超过 {@code seq_length}，继续生成会撞上已有编码。 */
        SEQUENCE_EXHAUSTED
    }

    private final String ruleCode;
    private final Reason reason;

    private CodeRuleException(String ruleCode, Reason reason, String message) {
        super(message);
        this.ruleCode = ruleCode;
        this.reason = reason;
    }

    /**
     * 规则不存在。
     *
     * @param ruleCode 规则编码
     * @return 携带规则编码与失败原因的异常
     */
    public static CodeRuleException notConfigured(String ruleCode) {
        return new CodeRuleException(ruleCode, Reason.NOT_CONFIGURED, "编码规则 " + ruleCode + " 未配置");
    }

    /**
     * 流水位数用尽。
     *
     * <p>消息里带上分配到的值与位数：只说「已用尽」时，运维无法判断该把位数加宽到多少，
     * 也看不出当前值已经越界多少。
     *
     * @param ruleCode     规则编码
     * @param currentValue 本次分配到的流水号，其位数已超出 {@code seqLength}
     * @param seqLength    规则配置的流水位数
     * @return 携带规则编码与失败原因的异常
     */
    public static CodeRuleException sequenceExhausted(String ruleCode, long currentValue, int seqLength) {
        return new CodeRuleException(ruleCode, Reason.SEQUENCE_EXHAUSTED,
                "规则 " + ruleCode + " 的流水号已用尽：本次分配到的流水号 " + currentValue
                        + " 超出配置的 " + seqLength + " 位");
    }

    /**
     * @return 出问题的规则编码
     */
    public String ruleCode() {
        return ruleCode;
    }

    /**
     * @return 失败原因
     */
    public Reason reason() {
        return reason;
    }
}

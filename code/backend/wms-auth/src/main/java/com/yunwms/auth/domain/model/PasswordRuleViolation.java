package com.yunwms.auth.domain.model;

/**
 * 密码未通过的规则项。
 *
 * <p>用枚举而不是布尔返回值：规格要求「提示具体不满足的规则」，一次输入的密码可能同时违反多条，
 * 调用方需要拿到全部违规项才能一次说清，而不是让用户改一次错一次。
 *
 * <p>文案直接写中文，与 {@code docs/coding-java.md} 的校验 message 约定一致：提示直接进前端，
 * 不做国际化键。
 */
public enum PasswordRuleViolation {

    /** 长度不在规定区间内。 */
    LENGTH("密码长度需为 " + PasswordPolicy.MIN_LENGTH + " 至 " + PasswordPolicy.MAX_LENGTH + " 位"),

    /** 缺大写字母。 */
    UPPERCASE("密码必须包含大写字母"),

    /** 缺小写字母。 */
    LOWERCASE("密码必须包含小写字母"),

    /** 缺数字。 */
    DIGIT("密码必须包含数字");

    private final String message;

    PasswordRuleViolation(String message) {
        this.message = message;
    }

    /**
     * @return 面向用户的中文提示，说明该规则的具体要求
     */
    public String message() {
        return message;
    }
}

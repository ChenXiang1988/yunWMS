package com.yunwms.auth.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 密码强度规则。
 *
 * <p>规则来自规格「密码策略与登录保护」：长度 8 至 20 位，且同时包含大写字母、小写字母与数字。
 * 本类只做判定，不负责存储，也不决定错误码：改密、管理员重置、建号会走不同的用例与不同的错误码，
 * 但「什么算合规密码」只能有一处定义，否则各入口的强弱标准迟早不一致。
 *
 * <p>一次返回全部未通过的规则，不在第一条失败处短路：规格要求「提示具体不满足的规则」。
 *
 * <p>字母与数字按 ASCII 判定。换成 {@link Character#isUpperCase} 会让结果随 JVM 的区域与
 * Unicode 版本变化，「必须包含大写字母」的含义就不可预期；密码要跨键盘、跨系统输入，
 * 只有 ASCII 的判定是稳定的。
 */
public final class PasswordPolicy {

    /** 密码长度下限。 */
    public static final int MIN_LENGTH = 8;

    /** 密码长度上限。 */
    public static final int MAX_LENGTH = 20;

    /**
     * 校验候选密码。
     *
     * <p>空值不在本方法的职责内：调用链上游的 {@code @NotBlank} 已经拦掉空密码，
     * 这里再判一次会让「没填」与「太弱」两种失败的提示混在一起。
     *
     * @param rawPassword 明文密码，调用方需保证非空
     * @return 未通过的规则，顺序固定为长度、大写、小写、数字；空列表表示合规
     */
    public List<PasswordRuleViolation> check(String rawPassword) {
        List<PasswordRuleViolation> violations = new ArrayList<>(PasswordRuleViolation.values().length);
        if (rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            violations.add(PasswordRuleViolation.LENGTH);
        }
        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasDigit = false;
        for (int i = 0; i < rawPassword.length(); i++) {
            char current = rawPassword.charAt(i);
            if (current >= 'A' && current <= 'Z') {
                hasUppercase = true;
            } else if (current >= 'a' && current <= 'z') {
                hasLowercase = true;
            } else if (current >= '0' && current <= '9') {
                hasDigit = true;
            }
        }
        if (!hasUppercase) {
            violations.add(PasswordRuleViolation.UPPERCASE);
        }
        if (!hasLowercase) {
            violations.add(PasswordRuleViolation.LOWERCASE);
        }
        if (!hasDigit) {
            violations.add(PasswordRuleViolation.DIGIT);
        }
        return List.copyOf(violations);
    }

    /**
     * 把未通过的规则拼成一条可直接展示的中文提示。
     *
     * @param violations 未通过的规则，取自 {@link #check(String)}
     * @return 以「；」连接的提示；无违规项时为空字符串
     */
    public String describe(List<PasswordRuleViolation> violations) {
        return violations.stream()
                .map(PasswordRuleViolation::message)
                .collect(Collectors.joining("；"));
    }
}

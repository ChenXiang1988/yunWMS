package com.yunwms.auth.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 密码策略的单测。
 *
 * <p>纯内存规则，不依赖 Spring 与数据库：规格里「8 至 20 位且含大小写与数字」这句话的每一分句
 * 都必须有一条对应用例，否则「弱密码被拒绝」到底拒在哪一条上就说不清。
 */
@DisplayName("密码策略")
class PasswordPolicyTest {

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    @Test
    @DisplayName("长度 7 位：只报长度不合规")
    void shouldRejectPasswordShorterThanEight() {
        List<PasswordRuleViolation> violations = passwordPolicy.check("Abc1234");

        assertThat(violations).containsExactly(PasswordRuleViolation.LENGTH);
        assertThat(passwordPolicy.describe(violations)).isEqualTo("密码长度需为 8 至 20 位");
    }

    @Test
    @DisplayName("长度 21 位：只报长度不合规")
    void shouldRejectPasswordLongerThanTwenty() {
        String password = "Abcdefghij1234567890X";
        assertThat(password).hasSize(21);

        List<PasswordRuleViolation> violations = passwordPolicy.check(password);

        assertThat(violations).containsExactly(PasswordRuleViolation.LENGTH);
    }

    @Test
    @DisplayName("8 位与 20 位是合法边界")
    void shouldAcceptBoundaryLengths() {
        assertThat(passwordPolicy.check("Abcd1234")).isEmpty();
        assertThat(passwordPolicy.check("Abcdefghij1234567890")).isEmpty();
    }

    @Test
    @DisplayName("缺大写字母：只报大写缺失")
    void shouldRejectPasswordWithoutUppercase() {
        List<PasswordRuleViolation> violations = passwordPolicy.check("abcd1234");

        assertThat(violations).containsExactly(PasswordRuleViolation.UPPERCASE);
        assertThat(passwordPolicy.describe(violations)).isEqualTo("密码必须包含大写字母");
    }

    @Test
    @DisplayName("缺小写字母：只报小写缺失")
    void shouldRejectPasswordWithoutLowercase() {
        List<PasswordRuleViolation> violations = passwordPolicy.check("ABCD1234");

        assertThat(violations).containsExactly(PasswordRuleViolation.LOWERCASE);
        assertThat(passwordPolicy.describe(violations)).isEqualTo("密码必须包含小写字母");
    }

    @Test
    @DisplayName("缺数字：只报数字缺失")
    void shouldRejectPasswordWithoutDigit() {
        List<PasswordRuleViolation> violations = passwordPolicy.check("Abcdefgh");

        assertThat(violations).containsExactly(PasswordRuleViolation.DIGIT);
        assertThat(passwordPolicy.describe(violations)).isEqualTo("密码必须包含数字");
    }

    @Test
    @DisplayName("合法密码：无违规项，提示为空")
    void shouldAcceptPasswordMeetingAllRules() {
        List<PasswordRuleViolation> violations = passwordPolicy.check("Abcd1234");

        assertThat(violations).isEmpty();
        assertThat(passwordPolicy.describe(violations)).isEmpty();
    }

    @Test
    @DisplayName("同时违反多条：一次返回全部违规项，提示逐条列出")
    void shouldReportAllViolationsAtOnce() {
        List<PasswordRuleViolation> violations = passwordPolicy.check("abc");

        assertThat(violations).containsExactly(
                PasswordRuleViolation.LENGTH,
                PasswordRuleViolation.UPPERCASE,
                PasswordRuleViolation.DIGIT);
        assertThat(passwordPolicy.describe(violations))
                .isEqualTo("密码长度需为 8 至 20 位；密码必须包含大写字母；密码必须包含数字");
    }

    @Test
    @DisplayName("非 ASCII 的大写字母不算大写：判定不随区域与 Unicode 版本漂移")
    void shouldTreatOnlyAsciiLettersAsUppercase() {
        List<PasswordRuleViolation> violations = passwordPolicy.check("Äbcd1234");

        assertThat(violations).containsExactly(PasswordRuleViolation.UPPERCASE);
    }
}

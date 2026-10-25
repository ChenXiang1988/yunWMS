package com.yunwms.auth.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录保护规则的取值测试。
 *
 * <p>阈值与两个时长原先写死在登录用例里，现在被登录与本人改密共用。这里钉住「提取时没有顺手改数」：
 * 5 次、30 分钟窗口、30 分钟锁定都来自规格原文，改任何一个都必须先改规格。
 *
 * <p>第三个用例钉住「窗口与锁定时长是两个独立声明」：两者当前取值相同，合并成一个字段不会让任何
 * 行为断言失败，但下一次调整锁定时长就会连计数窗口一起改掉，而这类偏差在功能测试里看不出来。
 */
@DisplayName("登录保护规则")
class LoginProtectionPolicyTest {

    @Test
    @DisplayName("阈值与两个时长与规格一致")
    void shouldKeepThresholdAndDurationsFromSpec() {
        assertThat(LoginProtectionPolicy.MAX_CONSECUTIVE_FAILURES).isEqualTo(5);
        assertThat(LoginProtectionPolicy.FAILURE_WINDOW).isEqualTo(Duration.ofMinutes(30));
        assertThat(LoginProtectionPolicy.LOCK_DURATION).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("窗口起点与锁定截止时间各自从失败时刻推导")
    void shouldDeriveWindowStartAndLockUntilFromFailedAt() {
        Instant failedAt = Instant.parse("2025-01-01T08:00:00Z");

        assertThat(LoginProtectionPolicy.windowStartOf(failedAt))
                .isEqualTo(failedAt.minus(Duration.ofMinutes(30)));
        assertThat(LoginProtectionPolicy.lockUntilOf(failedAt))
                .isEqualTo(failedAt.plus(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("窗口与锁定时长是两个独立声明，不合并为一个字段")
    void shouldDeclareWindowAndLockDurationSeparately() {
        List<String> declaredFields = Arrays.stream(LoginProtectionPolicy.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        assertThat(declaredFields).contains("FAILURE_WINDOW", "LOCK_DURATION");
    }
}

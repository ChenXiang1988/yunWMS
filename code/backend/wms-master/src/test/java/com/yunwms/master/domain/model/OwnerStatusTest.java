package com.yunwms.master.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 货主状态流转表的单测：把规格里的五条流向逐条钉住。
 *
 * <p><b>期望值写成字面量，不读 {@code allowedTargets()}：</b>从被测实现里取期望值，表改错了测试也一起改错，
 * 那是一条永远绿的断言。这里把规格原文抄成一张独立的表，两处一旦不一致就红——
 * 这正是「规则只一处」与「测试独立陈述规格」的分工。
 *
 * <p><b>为什么覆盖 5×5 全部组合而不是只测允许的那几条：</b>非法组合才是状态机的价值所在。
 * 只测合法流向，把 {@code TERMINATED} 放出到 {@code ACTIVE} 也不会被发现，而那正是规格
 * 「终止后不可恢复」要拦的东西。
 */
@DisplayName("货主状态流转")
class OwnerStatusTest {

    /**
     * 规格原文的流转表，不含自身（同状态是幂等提交，不是流转）。
     *
     * <p>{@link EnumMap} + {@link EnumSet} 保证遍历顺序稳定，失败信息因此可读、可复现。
     */
    private static final Map<OwnerStatus, Set<OwnerStatus>> EXPECTED_TARGETS = new EnumMap<>(OwnerStatus.class);

    static {
        EXPECTED_TARGETS.put(OwnerStatus.DRAFT, EnumSet.of(OwnerStatus.POTENTIAL, OwnerStatus.ACTIVE));
        EXPECTED_TARGETS.put(OwnerStatus.POTENTIAL, EnumSet.of(OwnerStatus.ACTIVE, OwnerStatus.TERMINATED));
        EXPECTED_TARGETS.put(OwnerStatus.ACTIVE, EnumSet.of(OwnerStatus.SUSPENDED, OwnerStatus.TERMINATED));
        EXPECTED_TARGETS.put(OwnerStatus.SUSPENDED, EnumSet.of(OwnerStatus.ACTIVE, OwnerStatus.TERMINATED));
        EXPECTED_TARGETS.put(OwnerStatus.TERMINATED, EnumSet.noneOf(OwnerStatus.class));
    }

    @Test
    @DisplayName("流转表：5×5 全组合逐条对照规格，允许的通过、不允许的被拒")
    void shouldAllowExactlyTheTransitionsSpecifiedByTheSpec() {
        for (OwnerStatus from : OwnerStatus.values()) {
            for (OwnerStatus to : OwnerStatus.values()) {
                boolean idempotent = from == to;
                boolean expectedAllowed = idempotent || EXPECTED_TARGETS.get(from).contains(to);
                String transition = from.name() + " -> " + to.name();

                assertThat(from.canTransitionTo(to))
                        .as("流转 %s 的判定与规格不符", transition)
                        .isEqualTo(expectedAllowed);

                if (expectedAllowed) {
                    assertThatCode(() -> from.requireTransitionTo(to))
                            .as("规格允许 %s，不应该被拒绝", transition)
                            .doesNotThrowAnyException();
                } else {
                    assertThatThrownBy(() -> from.requireTransitionTo(to))
                            .as("规格不允许 %s，必须被拒绝", transition)
                            .isInstanceOf(IllegalStateException.class);
                }
            }
        }
    }

    @Test
    @DisplayName("同状态提交幂等：五个状态（含终止）都按成功处理")
    void shouldTreatSameStatusAsIdempotentSuccess() {
        for (OwnerStatus status : OwnerStatus.values()) {
            assertThat(status.canTransitionTo(status))
                    .as("%s -> %s 是重复提交，不是非法流转", status, status)
                    .isTrue();
            assertThatCode(() -> status.requireTransitionTo(status))
                    .as("%s -> %s 是重复提交，不应该抛异常", status, status)
                    .doesNotThrowAnyException();
        }

        // 单独点名终止：它的 allowedTargets() 是空集，若实现忘了先判自身，
        // 这条会变成「重复提交终止报错」——与幂等口径相反
        assertThat(OwnerStatus.TERMINATED.allowedTargets()).isEmpty();
        assertThat(OwnerStatus.TERMINATED.canTransitionTo(OwnerStatus.TERMINATED)).isTrue();
    }

    @Test
    @DisplayName("对外暴露的流转表与规格一致，终止为空集")
    void shouldExposeTheSameTable() {
        for (OwnerStatus from : OwnerStatus.values()) {
            assertThat(from.allowedTargets())
                    .as("%s 的允许目标状态与规格不符", from)
                    .containsExactlyInAnyOrderElementsOf(EXPECTED_TARGETS.get(from));
        }
        assertThat(OwnerStatus.TERMINATED.allowedTargets()).as("终止为终态").isEmpty();
    }

    @Test
    @DisplayName("非法流转的提示含当前状态与全部允许的目标状态")
    void shouldDescribeCurrentStateAndAllowedTargetsInMessage() {
        assertThatThrownBy(() -> OwnerStatus.ACTIVE.requireTransitionTo(OwnerStatus.DRAFT))
                .as("提示里必须列出 ACTIVE 真正允许的目标，否则用户不知道该改成什么")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE")
                .hasMessageContaining("DRAFT")
                .hasMessageContaining("SUSPENDED")
                .hasMessageContaining("TERMINATED");

        // 终态的提示不能以「允许的目标状态：」结尾留空：要说明它只能保持原状态
        assertThatThrownBy(() -> OwnerStatus.TERMINATED.requireTransitionTo(OwnerStatus.ACTIVE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TERMINATED")
                .hasMessageContaining("ACTIVE")
                .hasMessageContaining("终态");
    }

    @Test
    @DisplayName("目标状态为空：拒绝，不静默当作「不改」")
    void shouldRejectNullTarget() {
        assertThatThrownBy(() -> OwnerStatus.DRAFT.canTransitionTo(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OwnerStatus.DRAFT.requireTransitionTo(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("未知状态编码：抛异常，不回落成草稿")
    void shouldRejectUnknownStatusCode() {
        assertThatThrownBy(() -> OwnerStatus.fromCode("FOO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FOO");
    }
}

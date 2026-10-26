package com.yunwms.master.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SKU 前缀格式规则的单测。
 *
 * <p>纯内存规则，不依赖 Spring 与数据库：规格里「4 位大写字母或数字，排除 {@code 0}、{@code O}、
 * {@code 1}、{@code I}」这句话的每个分句都必须有一条对应用例，否则「非法前缀被拒绝」到底拒在哪一条上
 * 就说不清——把 {@code O} 排除与把长度限死是两条独立的规则，缺哪条测试都不会红。
 *
 * <p>被排除的四个字符各立一条用例，而不是合成一条「含易混淆字符」：{@code [A-HJ-NP-Z2-9]} 这个
 * 字符类写错一个区间时（例如误写成 {@code [A-Z2-9]}），合并的用例仍然全绿。
 */
@DisplayName("SKU 前缀格式")
class SkuPrefixTest {

    @Test
    @DisplayName("含字母 O：拒绝")
    void shouldRejectUppercaseO() {
        assertThatThrownBy(() -> new SkuPrefix("ABCO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SkuPrefix.FORMAT_HINT);
    }

    @Test
    @DisplayName("含数字 0：拒绝")
    void shouldRejectZero() {
        assertThatThrownBy(() -> new SkuPrefix("ABC0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SkuPrefix.FORMAT_HINT);
    }

    @Test
    @DisplayName("含数字 1：拒绝")
    void shouldRejectOne() {
        assertThatThrownBy(() -> new SkuPrefix("ABC1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SkuPrefix.FORMAT_HINT);
    }

    @Test
    @DisplayName("含字母 I：拒绝")
    void shouldRejectUppercaseI() {
        assertThatThrownBy(() -> new SkuPrefix("ABCI"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SkuPrefix.FORMAT_HINT);
    }

    @Test
    @DisplayName("小写字母：拒绝，且不做大小写转换")
    void shouldRejectLowercase() {
        assertThatThrownBy(() -> new SkuPrefix("abcd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SkuPrefix.FORMAT_HINT);
    }

    @Test
    @DisplayName("长度 5 位：拒绝")
    void shouldRejectFiveCharacters() {
        assertThatThrownBy(() -> new SkuPrefix("ABCDE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SkuPrefix.FORMAT_HINT);
    }

    @Test
    @DisplayName("长度 3 位：拒绝")
    void shouldRejectThreeCharacters() {
        assertThatThrownBy(() -> new SkuPrefix("ABC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SkuPrefix.FORMAT_HINT);
    }

    @Test
    @DisplayName("空值：拒绝")
    void shouldRejectNull() {
        assertThatThrownBy(() -> new SkuPrefix(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SkuPrefix.FORMAT_HINT);
    }

    @Test
    @DisplayName("合法 4 位：接受，且原样保留")
    void shouldAcceptWellFormedPrefix() {
        assertThat(new SkuPrefix("WXYZ").value()).isEqualTo("WXYZ");
        // 数字与字母混排合法，2-9 全在允许区间内
        assertThat(new SkuPrefix("A2B9").value()).isEqualTo("A2B9");
    }
}

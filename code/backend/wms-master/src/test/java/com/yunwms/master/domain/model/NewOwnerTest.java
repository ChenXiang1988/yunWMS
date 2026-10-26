package com.yunwms.master.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 建档入参的领域规则单测。
 *
 * <p>这些规则是领域不变量，不是 HTTP 参数校验：将来的批量导入、初始化脚本同样要受它们约束，
 * 因此用例直接构造 {@link NewOwner} 来驱动，不经过任何接口层。
 *
 * <p>「不自动替换」单独立一条：它是规格里唯一一条**禁止**修正用户输入的要求，而「帮用户修一下」
 * 恰好是这类实现最容易顺手加上的东西——把 {@code O} 改成 {@code 0} 之后，校验当然通过，
 * 所有其他用例也全绿，只有这条用例能发现异常信息里混进了用户没有提交过的字符。
 */
@DisplayName("新建货主的领域规则")
class NewOwnerTest {

    @Test
    @DisplayName("SKU 前缀含 O：拒绝，且异常信息里不出现替换后的 0")
    void shouldRejectSkuPrefixWithLetterWithoutReplacingIt() {
        assertThatThrownBy(() -> newOwner(skuPrefixOf("ABCO"), "BRAND", addressOf("CN", "CN-SH")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SkuPrefix.FORMAT_HINT)
                // 断言文案里没有 "ABC0"：把 O 静默换成 0 会让用户以为库里存的是自己填的字符
                .hasMessageNotContaining("ABC0");
    }

    @Test
    @DisplayName("美国地址缺州省：拒绝")
    void shouldRejectUnitedStatesAddressWithoutProvince() {
        assertThatThrownBy(() -> newOwner(skuPrefixOf("WXYZ"), "BRAND", addressOf("US", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("州省");
    }

    @Test
    @DisplayName("美国地址带州省：通过")
    void shouldAcceptUnitedStatesAddressWithProvince() {
        assertThatCode(() -> newOwner(skuPrefixOf("WXYZ"), "BRAND", addressOf("US", "US-CA")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("非美国地址缺州省：通过")
    void shouldAcceptOtherCountryAddressWithoutProvince() {
        assertThatCode(() -> newOwner(skuPrefixOf("WXYZ"), "BRAND", addressOf("SG", null)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("客户类型为空：拒绝")
    void shouldRejectBlankOwnerType() {
        assertThatThrownBy(() -> newOwner(skuPrefixOf("WXYZ"), " ", addressOf("CN", "CN-SH")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("客户类型");
    }

    @Test
    @DisplayName("客户名称为空或全空白：拒绝")
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> new NewOwner(
                "  ", null, null, null, skuPrefixOf("WXYZ"), "BRAND", "测试结算主体",
                addressOf("CN", "CN-SH"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("客户名称");
    }

    /**
     * 构造一个合法的 SKU 前缀。
     *
     * @param value 前缀原文
     * @return 前缀值对象
     */
    private static SkuPrefix skuPrefixOf(String value) {
        return new SkuPrefix(value);
    }

    /**
     * 构造一条注册地址，城市与详细地址取合法值，只让调用方控制国家与州省。
     *
     * @param country  国家代码
     * @param province 州省取值，可为 {@code null}
     * @return 地址值对象
     */
    private static OwnerAddress addressOf(String country, String province) {
        return new OwnerAddress(country, province, "测试城市", null, "200000", "测试路 1 号");
    }

    /**
     * 构造一个合法的新货主，只让调用方控制前缀、客户类型与地址。
     *
     * @param skuPrefix SKU 前缀
     * @param ownerType 客户类型
     * @param address   注册地址
     * @return 待落库的货主
     */
    private static NewOwner newOwner(SkuPrefix skuPrefix, String ownerType, OwnerAddress address) {
        return new NewOwner("测试客户", null, null, null, skuPrefix, ownerType, "测试结算主体", address, null);
    }
}

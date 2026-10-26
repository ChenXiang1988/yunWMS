package com.yunwms.master.domain.service;

import com.yunwms.master.domain.model.NewOwner;
import com.yunwms.master.domain.model.OwnerAddress;
import com.yunwms.master.domain.model.SkuPrefix;
import com.yunwms.master.domain.port.DictLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 字典规则的单测：端口替换为 mock，不起 Spring、不连库。
 *
 * <p>要钉住的是四件事的**判定顺序**与**不该发生的那一步**：客户类型不在字典里时不该再问国家；
 * 国家不在字典里时不该再判州省归属；没有填州省时不该发起一次「空值属于哪个国家」的查询；
 * 邮编已填时不该再问该地区要不要邮编。前三条里最后一条尤其容易错：字典侧把
 * {@code parentValue = null} 解释为「不按层级筛选」，空州省会被判成「属于任何国家」，
 * 于是「没填州省」变成了通过。
 *
 * <p>邮编必填性本身也在这里覆盖全部分支：豁免地区允许为空、默认必填地区拒绝为空、
 * 未填州省时同样拒绝为空（省州可留空不等于邮编可留空）。
 */
@DisplayName("货主的字典规则")
class OwnerDictionaryValidatorTest {

    private DictLookup dictLookup;
    private OwnerDictionaryValidator validator;

    @BeforeEach
    void setUp() {
        dictLookup = mock(DictLookup.class);
        validator = new OwnerDictionaryValidator(dictLookup);
    }

    @Test
    @DisplayName("客户类型不在启用项中：拒绝，且不再查国家")
    void shouldRejectOwnerTypeOutsideDictionary() {
        given(dictLookup.isActiveItem("OWNER_TYPE", "THIRD_PARTY")).willReturn(false);

        NewOwner owner = ownerOf("THIRD_PARTY", "CN", "CN-SH");

        assertThatThrownBy(() -> validator.validate(owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OWNER_TYPE");
        verify(dictLookup, never()).isActiveItem("COUNTRY", "CN");
    }

    @Test
    @DisplayName("国家不在启用项中：拒绝，且不再判州省归属")
    void shouldRejectCountryOutsideDictionary() {
        given(dictLookup.isActiveItem("OWNER_TYPE", "BRAND")).willReturn(true);
        given(dictLookup.isActiveItem("COUNTRY", "XX")).willReturn(false);

        NewOwner owner = ownerOf("BRAND", "XX", "XX-AA");

        assertThatThrownBy(() -> validator.validate(owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("注册国家");
        verify(dictLookup, never()).isProvinceOfCountry(anyString(), anyString());
    }

    @Test
    @DisplayName("州省不属于所选国家：拒绝")
    void shouldRejectProvinceOfAnotherCountry() {
        given(dictLookup.isActiveItem("OWNER_TYPE", "BRAND")).willReturn(true);
        given(dictLookup.isActiveItem("COUNTRY", "US")).willReturn(true);
        given(dictLookup.isProvinceOfCountry("CN-SH", "US")).willReturn(false);

        NewOwner owner = ownerOf("BRAND", "US", "CN-SH");

        assertThatThrownBy(() -> validator.validate(owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("州省");
    }

    @Test
    @DisplayName("三项都命中字典：通过")
    void shouldPassWhenAllLookupsHit() {
        given(dictLookup.isActiveItem("OWNER_TYPE", "BRAND")).willReturn(true);
        given(dictLookup.isActiveItem("COUNTRY", "US")).willReturn(true);
        given(dictLookup.isProvinceOfCountry("US-CA", "US")).willReturn(true);

        assertThatCode(() -> validator.validate(ownerOf("BRAND", "US", "US-CA")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("未填州省：不发起归属查询")
    void shouldNotLookupProvinceWhenAbsent() {
        given(dictLookup.isActiveItem("OWNER_TYPE", "FORWARDER")).willReturn(true);
        given(dictLookup.isActiveItem("COUNTRY", "SG")).willReturn(true);

        assertThatCode(() -> validator.validate(ownerOf("FORWARDER", "SG", null)))
                .doesNotThrowAnyException();
        verify(dictLookup, never()).isProvinceOfCountry(anyString(), anyString());
    }

    @Test
    @DisplayName("港澳台没有邮政编码：缺邮编时通过")
    void shouldAcceptMissingPostalCodeWhereRegionHasNone() {
        given(dictLookup.isActiveItem("OWNER_TYPE", "BRAND")).willReturn(true);
        given(dictLookup.isActiveItem("COUNTRY", "CN")).willReturn(true);
        given(dictLookup.isProvinceOfCountry("CN-HK", "CN")).willReturn(true);
        given(dictLookup.isPostalCodeRequired("CN-HK")).willReturn(false);

        assertThatCode(() -> validator.validate(ownerOf("BRAND", "CN", "CN-HK", null)))
                .as("豁免地区不填邮编也应能建档")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("大陆省份未写 postalCodeRequired：缺邮编拒绝")
    void shouldRejectMissingPostalCodeWhereRequired() {
        given(dictLookup.isActiveItem("OWNER_TYPE", "BRAND")).willReturn(true);
        given(dictLookup.isActiveItem("COUNTRY", "CN")).willReturn(true);
        given(dictLookup.isProvinceOfCountry("CN-SH", "CN")).willReturn(true);
        // 大陆各省的字典项不写该键，端口按缺失即必填回 true
        given(dictLookup.isPostalCodeRequired("CN-SH")).willReturn(true);

        assertThatThrownBy(() -> validator.validate(ownerOf("BRAND", "CN", "CN-SH", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("邮政编码");
    }

    @Test
    @DisplayName("未填州省又缺邮编：默认必填，拒绝")
    void shouldRejectMissingPostalCodeWhenProvinceAbsent() {
        given(dictLookup.isActiveItem("OWNER_TYPE", "FORWARDER")).willReturn(true);
        given(dictLookup.isActiveItem("COUNTRY", "SG")).willReturn(true);
        // 空州省的入参形态由端口负责解释：省州可留空，但邮编在规格里是必填
        given(dictLookup.isPostalCodeRequired(isNull())).willReturn(true);

        assertThatThrownBy(() -> validator.validate(ownerOf("FORWARDER", "SG", null, " ")))
                .as("空白邮编与未填同口径")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("邮政编码");
        verify(dictLookup, never()).isProvinceOfCountry(anyString(), anyString());
    }

    @Test
    @DisplayName("邮编有值：无论该地区是否要求都通过，且不发必填性查询")
    void shouldAcceptPresentPostalCodeRegardlessOfRegion() {
        given(dictLookup.isActiveItem("OWNER_TYPE", "BRAND")).willReturn(true);
        given(dictLookup.isActiveItem("COUNTRY", "US")).willReturn(true);
        given(dictLookup.isActiveItem("COUNTRY", "CN")).willReturn(true);
        given(dictLookup.isProvinceOfCountry("US-CA", "US")).willReturn(true);
        given(dictLookup.isProvinceOfCountry("CN-HK", "CN")).willReturn(true);

        assertThatCode(() -> validator.validate(ownerOf("BRAND", "US", "US-CA", "95110")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(ownerOf("BRAND", "CN", "CN-HK", "999077")))
                .doesNotThrowAnyException();
        // 填了邮编就不必问地区：答案不影响结论，多一次查询只会让人怀疑这里漏判了什么
        verify(dictLookup, never()).isPostalCodeRequired(any());
    }

    /**
     * 构造一个填了邮编的合法新货主。
     *
     * @param ownerType 客户类型
     * @param country   国家代码
     * @param province  州省取值，可为 {@code null}
     * @return 待建档的货主
     */
    private static NewOwner ownerOf(String ownerType, String country, String province) {
        return ownerOf(ownerType, country, province, "200000");
    }

    /**
     * 构造一个合法的新货主。
     *
     * @param ownerType  客户类型
     * @param country    国家代码
     * @param province   州省取值，可为 {@code null}
     * @param postalCode 邮政编码，可为 {@code null} 或空白
     * @return 待建档的货主
     */
    private static NewOwner ownerOf(String ownerType, String country, String province, String postalCode) {
        return new NewOwner(
                "测试客户",
                null,
                null,
                null,
                new SkuPrefix("WXYZ"),
                ownerType,
                "测试结算主体",
                new OwnerAddress(country, province, "测试城市", null, postalCode, "测试路 1 号"),
                null);
    }
}

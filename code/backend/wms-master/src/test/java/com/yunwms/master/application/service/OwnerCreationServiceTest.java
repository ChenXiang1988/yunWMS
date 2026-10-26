package com.yunwms.master.application.service;

import com.yunwms.master.application.command.CreateOwnerCommand;
import com.yunwms.master.application.dto.CreatedOwner;
import com.yunwms.master.domain.model.NewOwner;
import com.yunwms.master.domain.model.OwnerStatus;
import com.yunwms.master.domain.model.SkuPrefix;
import com.yunwms.master.domain.port.CurrentOperator;
import com.yunwms.master.domain.port.DictLookup;
import com.yunwms.master.domain.port.OwnerCodeGenerator;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 货主建档用例的单测：四个端口全部替换为 mock，不起 Spring、不连库。
 *
 * <p>要钉住的是三件事：
 * <ul>
 *   <li><b>校验顺序</b>——会话无效、初始状态不可用于建档时，不该取号、不该查字典、不该落库。
 *       顺序错了不会报错，只会表现成「一次本来该被拒绝的请求消耗了一个号」或
 *       「字典停用项在错误提示里排在前缀格式前面」；</li>
 *   <li><b>唯一性只由数据库判定</b>——落库前的两次「先查再查」在本用例里不许出现（并发下无效），
 *       三个唯一约束各自翻译成 40904 / 40905 / 40906；</li>
 *   <li><b>不自动替换被排除字符</b>——前缀含 {@code O}/{@code 0}/{@code 1}/{@code I} 时，
 *       提示里回显用户提交的原文，且不出现被替换后的那个字符。</li>
 * </ul>
 */
@DisplayName("货主建档")
class OwnerCreationServiceTest {

    /** 操作人标识，取自会话；用例把它写进 created_by / updated_by。 */
    private static final long OPERATOR_ID = 42L;

    private OwnerRepository ownerRepository;
    private OwnerCodeGenerator ownerCodeGenerator;
    private DictLookup dictLookup;
    private CurrentOperator currentOperator;
    private OwnerCreationService service;

    @BeforeEach
    void setUp() {
        ownerRepository = mock(OwnerRepository.class);
        ownerCodeGenerator = mock(OwnerCodeGenerator.class);
        dictLookup = mock(DictLookup.class);
        currentOperator = mock(CurrentOperator.class);
        service = new OwnerCreationService(ownerRepository, ownerCodeGenerator, dictLookup, currentOperator);
        given(currentOperator.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
    }

    @Test
    @DisplayName("初始状态为 POTENTIAL / SUSPENDED / TERMINATED：拒绝，且不取号、不查字典、不落库")
    void shouldRejectStatusThatIsNotACreationEntry() {
        for (OwnerStatus status : List.of(OwnerStatus.POTENTIAL, OwnerStatus.SUSPENDED, OwnerStatus.TERMINATED)) {
            assertThatThrownBy(() -> service.create(command(status)))
                    .as("建档只允许 DRAFT 与 ACTIVE，%s 只能由状态流转产生", status)
                    .isInstanceOfSatisfying(BizException.class, e -> {
                        assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                        assertThat(e.getMessage()).contains(status.name());
                    });
        }

        // 顺序证据：状态不合法时后面的三步一次都不该发生
        verifyNoInteractions(dictLookup, ownerCodeGenerator, ownerRepository);
    }

    @Test
    @DisplayName("初始状态缺失：拒绝，且不取号、不落库")
    void shouldRejectMissingInitialStatus() {
        assertThatThrownBy(() -> service.create(command(null)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("初始状态");
                });

        verifyNoInteractions(dictLookup, ownerCodeGenerator, ownerRepository);
    }

    @Test
    @DisplayName("会话无效：拒绝，且不查字典、不取号、不落库")
    void shouldRejectWhenSessionIsMissing() {
        given(currentOperator.currentUserId()).willReturn(OptionalLong.empty());

        assertThatThrownBy(() -> service.create(command(OwnerStatus.ACTIVE)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(dictLookup, ownerCodeGenerator, ownerRepository);
    }

    @Test
    @DisplayName("SKU 前缀含 0 / O / 1 / I：拒绝，回显原文且不出现被替换后的字符")
    void shouldRejectExcludedCharactersWithoutReplacingThem() {
        // 键是提交值，值是「若系统擅自替换就会出现在提示里的那个串」
        Map<String, String> cases = Map.of(
                "AOBC", "A0BC",
                "A0BC", "AOBC",
                "A1BC", "AIBC",
                "AIBC", "A1BC");

        cases.forEach((submitted, replaced) ->
                assertThatThrownBy(() -> service.create(command(OwnerStatus.ACTIVE, submitted, "CN", "CN-SH", "518000")))
                        .as("提交 %s 时必须拒绝，而不是自动替换成 %s", submitted, replaced)
                        .isInstanceOfSatisfying(BizException.class, e -> {
                            assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                            assertThat(e.getMessage())
                                    .as("提示必须带上用户提交的原文，他据此才能知道该改哪一位")
                                    .contains(SkuPrefix.FORMAT_HINT)
                                    .contains(submitted)
                                    .doesNotContain(replaced);
                        }));

        verifyNoInteractions(dictLookup, ownerCodeGenerator, ownerRepository);
    }

    @Test
    @DisplayName("前缀格式错误且地址残缺：报前缀，不查字典")
    void shouldReportSkuPrefixBeforeAddress() {
        // 城市为空与后缀含被排除字符同时存在时，先暴露的是前缀
        assertThatThrownBy(() -> service.create(
                new CreateOwnerCommand("测试客户", null, null, null, "AOBC", "BRAND", "测试结算主体",
                        "CN", "CN-SH", null, null, "518000", null, null, OwnerStatus.ACTIVE)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(e.getMessage()).contains(SkuPrefix.FORMAT_HINT);
                });

        verifyNoInteractions(dictLookup, ownerCodeGenerator, ownerRepository);
    }

    @Test
    @DisplayName("国家为美国但缺州省：拒绝，且不查字典")
    void shouldRejectUnitedStatesWithoutProvince() {
        assertThatThrownBy(() -> service.create(command(OwnerStatus.ACTIVE, "AMZ2", "US", null, "95110")))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("州省");
                });

        // 美国必填州省是聚合自己就能判的，不该先跑一遍字典
        verifyNoInteractions(dictLookup, ownerCodeGenerator, ownerRepository);
    }

    @Test
    @DisplayName("港澳台没有邮编：缺邮编也建档成功，编码取自取号端口")
    void shouldCreateWhenExemptRegionHasNoPostalCode() {
        given(dictLookup.isActiveItem("OWNER_TYPE", "BRAND")).willReturn(true);
        given(dictLookup.isActiveItem("COUNTRY", "CN")).willReturn(true);
        given(dictLookup.isProvinceOfCountry("CN-HK", "CN")).willReturn(true);
        given(dictLookup.isPostalCodeRequired("CN-HK")).willReturn(false);
        given(ownerCodeGenerator.next()).willReturn("OWNER0001");
        given(ownerRepository.insert(any(NewOwner.class), anyString(), any(OwnerStatus.class), anyLong()))
                .willReturn(5L);

        CreatedOwner created = service.create(command(OwnerStatus.ACTIVE, "AMZ2", "CN", "CN-HK", null));

        assertThat(created).isEqualTo(new CreatedOwner(5L, "OWNER0001"));
    }

    @Test
    @DisplayName("大陆省份缺邮编：拒绝，且不取号、不落库")
    void shouldRejectMainlandWithoutPostalCode() {
        given(dictLookup.isActiveItem("OWNER_TYPE", "BRAND")).willReturn(true);
        given(dictLookup.isActiveItem("COUNTRY", "CN")).willReturn(true);
        given(dictLookup.isProvinceOfCountry("CN-SH", "CN")).willReturn(true);
        // 大陆各省的字典项不写 postalCodeRequired，端口按缺失即必填回 true
        given(dictLookup.isPostalCodeRequired("CN-SH")).willReturn(true);

        assertThatThrownBy(() -> service.create(command(OwnerStatus.ACTIVE, "AMZ2", "CN", "CN-SH", null)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("邮政编码");
                });

        verifyNoInteractions(ownerCodeGenerator, ownerRepository);
    }

    @Test
    @DisplayName("落库：编码来自取号端口，初始状态与操作人原样交给仓储，且不做任何先查后插")
    void shouldPersistWithGeneratedCodeAndOperator() {
        stubValidMainlandDictionary();
        given(ownerCodeGenerator.next()).willReturn("OWNER0001");
        given(ownerRepository.insert(any(NewOwner.class), anyString(), any(OwnerStatus.class), anyLong()))
                .willReturn(5L);

        CreatedOwner created = service.create(command(OwnerStatus.ACTIVE));

        assertThat(created).isEqualTo(new CreatedOwner(5L, "OWNER0001"));
        verify(ownerRepository).insert(
                any(NewOwner.class), eq("OWNER0001"), eq(OwnerStatus.ACTIVE), eq(OPERATOR_ID));
        // 唯一性一律交给数据库：并发下「先查再插」拦不住重复，只会多一次往返
        verify(ownerRepository, never()).existsBySkuPrefix(anyString());
        verify(ownerRepository, never()).existsByExternalCode(anyString());
    }

    @Test
    @DisplayName("货主编码撞唯一索引：40904，提示要求重新提交")
    void shouldTranslateOwnerCodeConflict() {
        stubValidMainlandDictionary();
        given(ownerCodeGenerator.next()).willReturn("OWNER0001");
        given(ownerRepository.insert(any(NewOwner.class), anyString(), any(OwnerStatus.class), anyLong()))
                .willThrow(constraintViolation("ux_owner_code"));

        assertThatThrownBy(() -> service.create(command(OwnerStatus.ACTIVE)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.OWNER_CODE_TAKEN);
                    // 编码由系统生成，用户没有别的动作可做，提示必须说清「重新提交」
                    assertThat(e.getMessage()).contains("重新提交");
                });
    }

    @Test
    @DisplayName("SKU 前缀撞唯一索引：40905，提示带上被占用的前缀原文")
    void shouldTranslateSkuPrefixConflict() {
        stubValidMainlandDictionary();
        given(ownerCodeGenerator.next()).willReturn("OWNER0001");
        given(ownerRepository.insert(any(NewOwner.class), anyString(), any(OwnerStatus.class), anyLong()))
                .willThrow(constraintViolation("ux_owner_sku_prefix"));

        assertThatThrownBy(() -> service.create(command(OwnerStatus.ACTIVE)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.OWNER_SKU_PREFIX_TAKEN);
                    // 不带值的话，用户只知道「这个前缀不行」，4 位的空间里无从判断该换什么
                    assertThat(e.getMessage()).contains("AMZ2");
                });
    }

    @Test
    @DisplayName("外部码撞唯一索引：40906")
    void shouldTranslateExternalCodeConflict() {
        stubValidMainlandDictionary();
        given(ownerCodeGenerator.next()).willReturn("OWNER0001");
        given(ownerRepository.insert(any(NewOwner.class), anyString(), any(OwnerStatus.class), anyLong()))
                .willThrow(constraintViolation("ux_owner_external_code"));

        assertThatThrownBy(() -> service.create(command(OwnerStatus.ACTIVE)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OWNER_EXTERNAL_CODE_TAKEN));
    }

    @Test
    @DisplayName("不认识的约束：原样上抛，不猜一个错误码")
    void shouldRethrowUnknownConstraint() {
        stubValidMainlandDictionary();
        given(ownerCodeGenerator.next()).willReturn("OWNER0001");
        DataIntegrityViolationException unknown = constraintViolation("ck_owner_country");
        given(ownerRepository.insert(any(NewOwner.class), anyString(), any(OwnerStatus.class), anyLong()))
                .willThrow(unknown);

        assertThatThrownBy(() -> service.create(command(OwnerStatus.ACTIVE)))
                .as("违反了一条本用例没预期到的约束，说明假设被打破，应当在 50000 上暴露")
                .isSameAs(unknown);
    }

    /**
     * 打桩一个「大陆地址、字典全部命中」的环境，供落库与冲突用例复用。
     */
    private void stubValidMainlandDictionary() {
        given(dictLookup.isActiveItem("OWNER_TYPE", "BRAND")).willReturn(true);
        given(dictLookup.isActiveItem("COUNTRY", "CN")).willReturn(true);
        given(dictLookup.isProvinceOfCountry("CN-SH", "CN")).willReturn(true);
        given(dictLookup.isPostalCodeRequired("CN-SH")).willReturn(true);
    }

    /**
     * 构造一条合法的建档入参。
     *
     * @param initialStatus 初始状态，可为 {@code null}
     * @return 建档入参
     */
    private static CreateOwnerCommand command(OwnerStatus initialStatus) {
        return command(initialStatus, "AMZ2", "CN", "CN-SH", "518000");
    }

    /**
     * 按需替换前缀与地址段的建档入参。
     *
     * @param initialStatus 初始状态，可为 {@code null}
     * @param skuPrefix     SKU 前缀原文
     * @param country       国家代码
     * @param province      州省，可为 {@code null}
     * @param postalCode    邮政编码，可为 {@code null}
     * @return 建档入参
     */
    private static CreateOwnerCommand command(OwnerStatus initialStatus, String skuPrefix,
                                              String country, String province, String postalCode) {
        return new CreateOwnerCommand(
                "深圳某某贸易",
                "某某贸易",
                "Shenzhen ABC Trading",
                "EXT-001",
                skuPrefix,
                "BRAND",
                "深圳某某贸易有限公司",
                country,
                province,
                "深圳",
                "南山区",
                postalCode,
                "科技园路 1 号",
                "",
                initialStatus);
    }

    /**
     * 构造一段与 PostgreSQL 报错同形的约束冲突。
     *
     * @param constraintName 约束名
     * @return 数据库异常，消息里带约束名
     */
    private static DataIntegrityViolationException constraintViolation(String constraintName) {
        return new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"" + constraintName + "\"");
    }
}

package com.yunwms.master.application.service;

import com.yunwms.master.application.command.UpdateOwnerCommand;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerAddress;
import com.yunwms.master.domain.model.OwnerAuditEvent;
import com.yunwms.master.domain.model.OwnerProfile;
import com.yunwms.master.domain.model.OwnerStatus;
import com.yunwms.master.domain.model.SkuPrefix;
import com.yunwms.master.domain.port.AuditTrail;
import com.yunwms.master.domain.port.CurrentOperator;
import com.yunwms.master.domain.port.DictLookup;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 货主编辑用例的单测：端口替换为 mock，不起 Spring、不连库。
 *
 * <p>要钉住的是「编辑这条路能改什么、不能改什么」：
 * <ul>
 *   <li>会话无效与货主不存在都在最前面，且都不碰字典、不写库——顺序反了会把一次 404
 *       报成一次字段错误；</li>
 *   <li>可编辑范围由 {@link OwnerProfile} 决定：编码、SKU 前缀与状态根本不在类型里，
 *       因此本类不需要（也无法）在写语句前删掉它们；</li>
 *   <li>外部码冲突翻译成 40906；编码与前缀的唯一索引在这条链路上不可能命中，
 *       真命中了属于假设被打破，必须原样上抛。</li>
 *   <li>信噪比：改了就留痕，一个字都没改就不留——空提交与真改动在日志里必须能分开。</li>
 * </ul>
 */
@DisplayName("货主编辑")
class OwnerUpdateServiceTest {

    /** 操作人标识，取自会话；用例把它写进 updated_by。 */
    private static final long OPERATOR_ID = 7L;

    /** 被编辑的货主标识。 */
    private static final long OWNER_ID = 5L;

    private OwnerRepository ownerRepository;
    private DictLookup dictLookup;
    private CurrentOperator currentOperator;
    private AuditTrail auditTrail;
    private OwnerUpdateService service;

    @BeforeEach
    void setUp() {
        ownerRepository = mock(OwnerRepository.class);
        dictLookup = mock(DictLookup.class);
        currentOperator = mock(CurrentOperator.class);
        auditTrail = mock(AuditTrail.class);
        service = new OwnerUpdateService(ownerRepository, dictLookup, currentOperator, auditTrail);
        given(currentOperator.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
    }

    @Test
    @DisplayName("会话无效：拒绝，且不读库、不查字典")
    void shouldRejectWhenSessionIsMissing() {
        given(currentOperator.currentUserId()).willReturn(OptionalLong.empty());

        assertThatThrownBy(() -> service.update(command()))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(dictLookup, ownerRepository);
    }

    @Test
    @DisplayName("货主不存在或已删除：40400，且不查字典、不写库")
    void shouldRejectUnknownOwner() {
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(command()))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verifyNoInteractions(dictLookup);
        verify(ownerRepository, never()).update(anyLong(), any(OwnerProfile.class), anyLong());
    }

    @Test
    @DisplayName("国家为美国但缺州省：拒绝，不写库")
    void shouldRejectUnitedStatesWithoutProvince() {
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(existingOwner()));

        UpdateOwnerCommand command = command();
        UpdateOwnerCommand withoutProvince = new UpdateOwnerCommand(
                command.ownerId(), command.name(), command.shortName(), command.nameEn(), command.externalCode(),
                command.ownerType(), command.settlementEntity(), "US", null, command.city(),
                command.district(), command.postalCode(), command.addressLine(), command.remark());

        assertThatThrownBy(() -> service.update(withoutProvince))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("州省");
                });

        verify(ownerRepository, never()).update(anyLong(), any(OwnerProfile.class), anyLong());
    }

    @Test
    @DisplayName("大陆省份缺邮编：拒绝，不写库")
    void shouldRejectMainlandWithoutPostalCode() {
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(existingOwner()));
        given(dictLookup.isActiveItem("OWNER_TYPE", "BRAND")).willReturn(true);
        given(dictLookup.isActiveItem("COUNTRY", "CN")).willReturn(true);
        given(dictLookup.isProvinceOfCountry("CN-SH", "CN")).willReturn(true);
        given(dictLookup.isPostalCodeRequired("CN-SH")).willReturn(true);

        UpdateOwnerCommand command = command();
        UpdateOwnerCommand withoutPostalCode = new UpdateOwnerCommand(
                command.ownerId(), command.name(), command.shortName(), command.nameEn(), command.externalCode(),
                command.ownerType(), command.settlementEntity(), command.country(), command.province(),
                command.city(), command.district(), null, command.addressLine(), command.remark());

        assertThatThrownBy(() -> service.update(withoutPostalCode))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("邮政编码");
                });

        verify(ownerRepository, never()).update(anyLong(), any(OwnerProfile.class), anyLong());
    }

    @Test
    @DisplayName("外部码撞唯一索引：40906")
    void shouldTranslateExternalCodeConflict() {
        stubValidMainlandDictionary();
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(existingOwner()));
        willThrow(constraintViolation("ux_owner_external_code"))
                .given(ownerRepository).update(anyLong(), any(OwnerProfile.class), anyLong());

        assertThatThrownBy(() -> service.update(command()))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.OWNER_EXTERNAL_CODE_TAKEN));
    }

    @Test
    @DisplayName("编辑不改前缀与状态时命中它们的唯一索引：原样上抛，不猜错误码")
    void shouldRethrowConstraintThatUpdateCannotProduce() {
        stubValidMainlandDictionary();
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(existingOwner()));
        DataIntegrityViolationException unexpected = constraintViolation("ux_owner_sku_prefix");
        willThrow(unexpected).given(ownerRepository).update(anyLong(), any(OwnerProfile.class), anyLong());

        assertThatThrownBy(() -> service.update(command()))
                .as("写语句的 SET 列表里没有 sku_prefix，命中该索引说明语句被改坏了")
                .isSameAs(unexpected);
    }

    @Test
    @DisplayName("落库：写入覆盖后的档案与操作人，编码 / 前缀 / 状态不在写入模型里")
    void shouldUpdateProfileWithOperator() {
        stubValidMainlandDictionary();
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(existingOwner()));

        service.update(command());

        ArgumentCaptor<OwnerProfile> captor = ArgumentCaptor.forClass(OwnerProfile.class);
        verify(ownerRepository).update(eq(OWNER_ID), captor.capture(), eq(OPERATOR_ID));

        OwnerProfile profile = captor.getValue();
        assertThat(profile.name()).isEqualTo("深圳某某贸易（改名）");
        assertThat(profile.externalCode()).isEqualTo("EXT-002");
        assertThat(profile.address().country()).isEqualTo("CN");
        assertThat(profile.address().city()).isEqualTo("深圳");
        assertThat(profile.address().postalCode()).isEqualTo("518000");
        // 空串一律落成 null：'' 在 ux_owner_external_code 眼里是一个具体取值，会让第二个不填外部码的货主撞车
        assertThat(profile.remark()).isNull();
        assertThat(profile.shortName()).isNull();
    }

    @Test
    @DisplayName("审计：编辑写一条 OWNER_UPDATED，before / after 都是完整快照且状态一致")
    void shouldRecordUpdateWithBothSnapshots() {
        stubValidMainlandDictionary();
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(existingOwner()));

        service.update(command());

        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.captor();
        ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.captor();
        verify(auditTrail).record(
                eq(OwnerAuditEvent.OWNER_UPDATED), eq("OWNER0005"), before.capture(), after.capture());

        // before 取自 findById 的结果：再查一次只可能读到别人刚改的值，前后就不属于同一次提交了
        assertThat(before.getValue())
                .containsEntry("name", "深圳某某贸易")
                .containsEntry("externalCode", "EXT-001")
                .containsEntry("status", "DRAFT");
        assertThat(after.getValue())
                .containsEntry("name", "深圳某某贸易（改名）")
                .containsEntry("externalCode", "EXT-002")
                .containsEntry("status", "DRAFT");
        assertThat(after.getValue().get("status"))
                .as("编辑不改状态：前后必须一致，否则读日志的人会以为发生了一次流转")
                .isEqualTo(before.getValue().get("status"));
    }

    @Test
    @DisplayName("审计：提交的字段与库里完全一致时不写审计（业务照常写库）")
    void shouldNotRecordWhenNothingChanged() {
        stubValidMainlandDictionary();
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(existingOwner()));

        service.update(unchangedCommand());

        // 空提交仍然是一次保存：updated_at 照常前进，但不留审计——记一条「没改动」只会稀释信号
        verify(ownerRepository).update(eq(OWNER_ID), any(OwnerProfile.class), eq(OPERATOR_ID));
        verifyNoInteractions(auditTrail);
    }

    /**
     * 打桩一个字典全部命中的环境。
     */
    private void stubValidMainlandDictionary() {
        given(dictLookup.isActiveItem("OWNER_TYPE", "BRAND")).willReturn(true);
        given(dictLookup.isActiveItem("COUNTRY", "CN")).willReturn(true);
        given(dictLookup.isProvinceOfCountry("CN-SH", "CN")).willReturn(true);
        given(dictLookup.isPostalCodeRequired("CN-SH")).willReturn(true);
    }

    /**
     * 库中已存在的那条货主记录，供 {@code findById} 返回。
     *
     * <p>状态取 {@code DRAFT} 是有意的：草稿同样不许改前缀，而编辑用例对两种状态一视同仁。
     *
     * @return 已存在的货主
     */
    private static Owner existingOwner() {
        return new Owner(
                OWNER_ID,
                "OWNER0005",
                "深圳某某贸易",
                "某某贸易",
                null,
                "EXT-001",
                new SkuPrefix("AMZ2"),
                "BRAND",
                "深圳某某贸易有限公司",
                OwnerStatus.DRAFT,
                new OwnerAddress("CN", "CN-SH", "深圳", "南山区", "518000", "科技园路 1 号"),
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                OPERATOR_ID,
                Instant.parse("2026-01-01T00:00:00Z"),
                OPERATOR_ID);
    }

    /**
     * 一条合法的编辑入参：改名、换外部码、清空简称与备注（空串按未填处理）。
     *
     * @return 编辑入参
     */
    private static UpdateOwnerCommand command() {
        return new UpdateOwnerCommand(
                OWNER_ID,
                "深圳某某贸易（改名）",
                "",
                null,
                "EXT-002",
                "BRAND",
                "深圳某某贸易有限公司",
                "CN",
                "CN-SH",
                "深圳",
                "南山区",
                "518000",
                "科技园路 1 号",
                "");
    }

    /**
     * 一条「与库里现有记录完全一致」的编辑入参：每个字段都与 {@link #existingOwner()} 相同。
     *
     * <p>备注用空串提交：入参归一化会把空串落成 {@code null}，而库里正是 {@code null}——
     * 这正是要验的情形，「没填」与「没填」不该被当成一次改动。
     *
     * @return 编辑入参
     */
    private static UpdateOwnerCommand unchangedCommand() {
        return new UpdateOwnerCommand(
                OWNER_ID,
                "深圳某某贸易",
                "某某贸易",
                null,
                "EXT-001",
                "BRAND",
                "深圳某某贸易有限公司",
                "CN",
                "CN-SH",
                "深圳",
                "南山区",
                "518000",
                "科技园路 1 号",
                "");
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

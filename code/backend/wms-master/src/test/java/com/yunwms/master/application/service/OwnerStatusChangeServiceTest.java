package com.yunwms.master.application.service;

import com.yunwms.master.application.command.ChangeOwnerStatusCommand;
import com.yunwms.master.application.dto.TerminationImpact;
import com.yunwms.master.application.query.OwnerImpactQueryService;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerAddress;
import com.yunwms.master.domain.model.OwnerStatus;
import com.yunwms.master.domain.model.SkuPrefix;
import com.yunwms.master.domain.port.CurrentOperator;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 货主状态流转用例的单测：端口替换为 mock，不起 Spring、不连库。
 *
 * <p>要钉住的是这条链路的四件事：
 * <ul>
 *   <li>失败顺序——会话、存在性、目标状态、流转判定、确认，顺序反了会把一次 404 报成一次状态错误；</li>
 *   <li>流转判定交给 {@link OwnerStatus}，用例层不自己写状态组合（这里只验证它确实被调用到）；</li>
 *   <li>终止未确认必须被拒，且提示里带真实的商品数与账号数——数字来自影响清单，不是写死的；</li>
 *   <li>同状态提交幂等成功，包含 {@code TERMINATED → TERMINATED}：它不查影响清单、不要求确认。</li>
 * </ul>
 */
@DisplayName("货主状态流转")
class OwnerStatusChangeServiceTest {

    /** 操作人标识，取自会话；用例把它写进 updated_by。 */
    private static final long OPERATOR_ID = 7L;

    /** 被流转的货主标识。 */
    private static final long OWNER_ID = 5L;

    private OwnerRepository ownerRepository;
    private OwnerImpactQueryService ownerImpactQueryService;
    private CurrentOperator currentOperator;
    private OwnerStatusChangeService service;

    @BeforeEach
    void setUp() {
        ownerRepository = mock(OwnerRepository.class);
        ownerImpactQueryService = mock(OwnerImpactQueryService.class);
        currentOperator = mock(CurrentOperator.class);
        service = new OwnerStatusChangeService(ownerRepository, ownerImpactQueryService, currentOperator);
        given(currentOperator.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
    }

    @Test
    @DisplayName("会话无效：拒绝，且不读库、不查影响清单")
    void shouldRejectWhenSessionIsMissing() {
        given(currentOperator.currentUserId()).willReturn(OptionalLong.empty());

        assertThatThrownBy(() -> service.change(command(OwnerStatus.ACTIVE, true)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(ownerRepository, ownerImpactQueryService);
    }

    @Test
    @DisplayName("货主不存在或已删除：40400，且不查影响清单、不写库")
    void shouldRejectUnknownOwner() {
        given(ownerRepository.findByIdForUpdate(OWNER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.change(command(OwnerStatus.ACTIVE, true)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verifyNoInteractions(ownerImpactQueryService);
        verify(ownerRepository, never()).updateStatus(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("目标状态编码未知：40000，提示里带原值；不查影响清单、不写库")
    void shouldRejectUnknownTargetStatus() {
        given(ownerRepository.findByIdForUpdate(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.ACTIVE)));

        assertThatThrownBy(() -> service.change(new ChangeOwnerStatusCommand(OWNER_ID, "FOO", true)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("FOO");
                });

        verifyNoInteractions(ownerImpactQueryService);
        verify(ownerRepository, never()).updateStatus(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("目标状态为空：40000，不当作「不改」处理")
    void shouldRejectBlankTargetStatus() {
        given(ownerRepository.findByIdForUpdate(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.ACTIVE)));

        assertThatThrownBy(() -> service.change(new ChangeOwnerStatusCommand(OWNER_ID, "  ", true)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        verify(ownerRepository, never()).updateStatus(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("不允许的流转：40900，提示含当前状态与允许目标，且不查影响清单、不写库")
    void shouldRejectIllegalTransition() {
        given(ownerRepository.findByIdForUpdate(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.ACTIVE)));

        assertThatThrownBy(() -> service.change(command(OwnerStatus.DRAFT, true)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    // 目标状态本身合法，被拒绝的是它相对当前状态的关系：落在 409 段位
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(e.getMessage())
                            .contains("ACTIVE")
                            .contains("DRAFT")
                            .contains("SUSPENDED")
                            .contains("TERMINATED");
                });

        verifyNoInteractions(ownerImpactQueryService);
        verify(ownerRepository, never()).updateStatus(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("终止未确认影响清单：40900，提示带商品数与账号数，不写库")
    void shouldRejectTerminationWithoutAcknowledgedImpact() {
        given(ownerRepository.findByIdForUpdate(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.ACTIVE)));
        given(ownerImpactQueryService.terminationImpact(OWNER_ID)).willReturn(new TerminationImpact(3L, 2L));

        assertThatThrownBy(() -> service.change(command(OwnerStatus.TERMINATED, false)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    // 数字必须来自影响清单：写死一个「N 个」等于让用户拿不到任何信息
                    assertThat(e.getMessage())
                            .contains("终止将影响")
                            .contains("3")
                            .contains("个商品")
                            .contains("2")
                            .contains("个账号");
                });

        verify(ownerImpactQueryService).terminationImpact(OWNER_ID);
        verify(ownerRepository, never()).updateStatus(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("终止已确认：写库，且不再为提示去查影响清单")
    void shouldTerminateWhenImpactAcknowledged() {
        given(ownerRepository.findByIdForUpdate(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.ACTIVE)));

        service.change(command(OwnerStatus.TERMINATED, true));

        verify(ownerRepository).updateStatus(OWNER_ID, OwnerStatus.TERMINATED, OPERATOR_ID);
        // 已确认的路径不需要数字：多查一次等于让每次终止都多两条无关的 SQL
        verifyNoInteractions(ownerImpactQueryService);
    }

    @Test
    @DisplayName("重复提交终止：幂等成功，不要求确认、不查影响清单")
    void shouldTreatRepeatedTerminationAsIdempotent() {
        given(ownerRepository.findByIdForUpdate(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.TERMINATED)));

        // 未带确认也按成功处理：这次调用不会产生任何新的后果，确认一次没有意义
        assertThatCode(() -> service.change(command(OwnerStatus.TERMINATED, false)))
                .doesNotThrowAnyException();

        verify(ownerRepository).updateStatus(OWNER_ID, OwnerStatus.TERMINATED, OPERATOR_ID);
        verifyNoInteractions(ownerImpactQueryService);
    }

    @Test
    @DisplayName("非终止目标忽略确认字段：暂停不要求确认影响清单")
    void shouldIgnoreAcknowledgementForNonTerminationTargets() {
        given(ownerRepository.findByIdForUpdate(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.ACTIVE)));

        service.change(command(OwnerStatus.SUSPENDED, false));

        verify(ownerRepository).updateStatus(OWNER_ID, OwnerStatus.SUSPENDED, OPERATOR_ID);
        verifyNoInteractions(ownerImpactQueryService);
    }

    @Test
    @DisplayName("暂停后恢复合作中：写库，且沿用同一个流转表")
    void shouldResumeFromSuspension() {
        given(ownerRepository.findByIdForUpdate(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.SUSPENDED)));

        service.change(command(OwnerStatus.ACTIVE, false));

        verify(ownerRepository).updateStatus(OWNER_ID, OwnerStatus.ACTIVE, OPERATOR_ID);
    }

    @Test
    @DisplayName("仅变更货主状态：不触碰商品与账号（本用例没有那两个端口可调）")
    void shouldOnlyChangeOwnerStatus() {
        given(ownerRepository.findByIdForUpdate(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.ACTIVE)));

        service.change(command(OwnerStatus.TERMINATED, true));

        // 用「只调用了这一个写方法」把规格「终止时仅变更货主状态、不得自动修改商品档案状态」钉住：
        // 将来有人加一个「顺手停用账号」的调用，这条断言会失败
        verify(ownerRepository).updateStatus(eq(OWNER_ID), eq(OwnerStatus.TERMINATED), eq(OPERATOR_ID));
        verifyNoInteractions(ownerImpactQueryService);
        verify(ownerRepository, never()).update(anyLong(), any(), anyLong());
        verify(ownerRepository, never()).softDelete(anyLong(), anyLong());
    }

    /**
     * 流转入参：目标状态取枚举名。
     *
     * @param target             目标状态
     * @param acknowledgedImpact 是否已确认终止影响清单
     * @return 流转入参
     */
    private static ChangeOwnerStatusCommand command(OwnerStatus target, boolean acknowledgedImpact) {
        return new ChangeOwnerStatusCommand(OWNER_ID, target.name(), acknowledgedImpact);
    }

    /**
     * 库中已存在的那条货主记录，供 {@code findByIdForUpdate} 返回。
     *
     * @param status 当前状态
     * @return 已存在的货主
     */
    private static Owner owner(OwnerStatus status) {
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
                status,
                new OwnerAddress("CN", "CN-SH", "深圳", "南山区", "518000", "科技园路 1 号"),
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                OPERATOR_ID,
                Instant.parse("2026-01-01T00:00:00Z"),
                OPERATOR_ID);
    }
}

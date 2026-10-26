package com.yunwms.master.application.service;

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
 * 货主删除用例的单测：端口替换为 mock，不起 Spring、不连库。
 *
 * <p>要钉住三件事：
 * <ul>
 *   <li>只有草稿能删，其余四个状态一律拒绝，且提示里带出当前状态——只说「不可删除」时
 *       用户只会反复点同一个按钮；</li>
 *   <li>删除是软删除：走 {@code softDelete}，不碰 {@code updateStatus}（删除一个草稿之后它仍是草稿，
 *       只是看不见了）；</li>
 *   <li>读到之后这一行被并发删掉时如实回 404，而不是回一个看不出来发生过的成功。</li>
 * </ul>
 */
@DisplayName("货主删除")
class OwnerDeletionServiceTest {

    /** 操作人标识，取自会话；用例把它写进 updated_by。 */
    private static final long OPERATOR_ID = 7L;

    /** 被删除的货主标识。 */
    private static final long OWNER_ID = 5L;

    private OwnerRepository ownerRepository;
    private CurrentOperator currentOperator;
    private OwnerDeletionService service;

    @BeforeEach
    void setUp() {
        ownerRepository = mock(OwnerRepository.class);
        currentOperator = mock(CurrentOperator.class);
        service = new OwnerDeletionService(ownerRepository, currentOperator);
        given(currentOperator.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
    }

    @Test
    @DisplayName("会话无效：拒绝，且不读库、不写库")
    void shouldRejectWhenSessionIsMissing() {
        given(currentOperator.currentUserId()).willReturn(OptionalLong.empty());

        assertThatThrownBy(() -> service.delete(OWNER_ID))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(ownerRepository);
    }

    @Test
    @DisplayName("货主不存在或已删除：40400，不写库")
    void shouldRejectUnknownOwner() {
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(OWNER_ID))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verify(ownerRepository, never()).softDelete(anyLong(), anyLong());
    }

    @Test
    @DisplayName("非草稿删除：40900，提示带当前状态并指向状态流转；不写库")
    void shouldRejectDeletingNonDraft() {
        for (OwnerStatus status : new OwnerStatus[]{
                OwnerStatus.POTENTIAL, OwnerStatus.ACTIVE, OwnerStatus.SUSPENDED, OwnerStatus.TERMINATED}) {
            given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(owner(status)));

            assertThatThrownBy(() -> service.delete(OWNER_ID))
                    .as("状态 %s 不是草稿，必须被拒绝", status)
                    .isInstanceOfSatisfying(BizException.class, e -> {
                        assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                        assertThat(e.getMessage())
                                .as("提示必须说清当前状态，否则用户不知道该先做什么")
                                .contains(status.name())
                                .contains("草稿")
                                .contains("终止");
                    });
        }

        verify(ownerRepository, never()).softDelete(anyLong(), anyLong());
    }

    @Test
    @DisplayName("草稿删除：置 deleted_at，带操作人；不改状态")
    void shouldSoftDeleteDraft() {
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.DRAFT)));
        given(ownerRepository.softDelete(OWNER_ID, OPERATOR_ID)).willReturn(true);

        service.delete(OWNER_ID);

        verify(ownerRepository).softDelete(eq(OWNER_ID), eq(OPERATOR_ID));
        // 删除一个草稿之后它仍是草稿（只是看不见了）：顺带改状态会让「终止过吗」多出第三种答案
        verify(ownerRepository, never()).updateStatus(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("读到之后被并发删除：如实回 40400，不报成功也不报 500")
    void shouldReportNotFoundWhenRowDisappeared() {
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.DRAFT)));
        given(ownerRepository.softDelete(OWNER_ID, OPERATOR_ID)).willReturn(false);

        assertThatThrownBy(() -> service.delete(OWNER_ID))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    /**
     * 库中已存在的那条货主记录，供 {@code findById} 返回。
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

package com.yunwms.master.application.query;

import com.yunwms.master.application.dto.TerminationImpact;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerAddress;
import com.yunwms.master.domain.model.OwnerStatus;
import com.yunwms.master.domain.model.SkuPrefix;
import com.yunwms.master.domain.port.OwnerAccountCounter;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.master.domain.port.OwnerSkuCounter;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 终止影响清单读取用例的单测：两个计数端口替换为 mock。
 *
 * <p>要钉住的是「两个数字各自从哪来、有没有被接反」：两个计数都是 {@code long}，接反了不会报错，
 * 只会让确认弹窗显示成「影响 2 个商品与 3 个账号」，而管理员据此判断终止的后果。
 *
 * <p>货主不存在时必须 404，且两个计数端口一次都不该被调用——为一条不存在的货主去数商品与账号，
 * 得到的两个 0 会被界面当成「没有影响，可以放心终止」。
 */
@DisplayName("终止影响清单")
class OwnerImpactQueryServiceTest {

    private static final long OWNER_ID = 5L;

    private OwnerRepository ownerRepository;
    private OwnerSkuCounter ownerSkuCounter;
    private OwnerAccountCounter ownerAccountCounter;
    private OwnerImpactQueryService service;

    @BeforeEach
    void setUp() {
        ownerRepository = mock(OwnerRepository.class);
        ownerSkuCounter = mock(OwnerSkuCounter.class);
        ownerAccountCounter = mock(OwnerAccountCounter.class);
        service = new OwnerImpactQueryService(ownerRepository, ownerSkuCounter, ownerAccountCounter);
    }

    @Test
    @DisplayName("货主不存在或已删除：40400，且不去数商品与账号")
    void shouldRejectUnknownOwner() {
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.terminationImpact(OWNER_ID))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));

        verifyNoInteractions(ownerSkuCounter, ownerAccountCounter);
    }

    @Test
    @DisplayName("两个计数分别来自商品端口与账号端口，不接反")
    void shouldReturnBothCountsFromTheirOwnPorts() {
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(owner()));
        given(ownerSkuCounter.countByOwnerId(OWNER_ID)).willReturn(3L);
        given(ownerAccountCounter.countByOwnerId(OWNER_ID)).willReturn(2L);

        TerminationImpact impact = service.terminationImpact(OWNER_ID);

        // 两个数字故意取不同的值：取 0 或相同值时，字段接反也测不出来
        assertThat(impact.skuCount()).as("商品数来自 sku 计数端口").isEqualTo(3L);
        assertThat(impact.accountCount()).as("账号数来自 sys_user 计数端口").isEqualTo(2L);
    }

    @Test
    @DisplayName("没有商品也没有账号：返回两个 0，而不是空对象或 null")
    void shouldReturnZerosWhenNothingLeft() {
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(owner()));

        TerminationImpact impact = service.terminationImpact(OWNER_ID);

        assertThat(impact.skuCount()).isZero();
        assertThat(impact.accountCount()).isZero();
    }

    /**
     * 库中已存在的那条货主记录，供 {@code findById} 返回。
     *
     * @return 已存在的货主
     */
    private static Owner owner() {
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
                OwnerStatus.ACTIVE,
                new OwnerAddress("CN", "CN-SH", "深圳", "南山区", "518000", "科技园路 1 号"),
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                7L,
                Instant.parse("2026-01-01T00:00:00Z"),
                7L);
    }
}

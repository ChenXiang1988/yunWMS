package com.yunwms.master.application.query;

import com.yunwms.master.application.dto.OwnerDetail;
import com.yunwms.master.application.dto.OwnerPage;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerAddress;
import com.yunwms.master.domain.model.OwnerStatus;
import com.yunwms.master.domain.model.SkuPrefix;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 货主列表与详情用例的单测：仓储端口替换为 mock，不起 Spring、不连库。
 *
 * <p>要钉住的是四件事：
 * <ul>
 *   <li><b>分页越界一条 SQL 都不发</b>——「拒绝」与「拒绝但先查了一次库」对用户是同一件事，
 *       对数据库不是。用 {@code verifyNoInteractions} 而不是断言返回码，是因为返回码对两种情况相同；</li>
 *   <li><b>状态编码在本层解析</b>——非法值要在这里变成 40000，而不是被当成「不筛选」放过去；</li>
 *   <li><b>空白筛选条件按未填处理</b>——一个只有空格的关键字会变成 {@code LIKE '% %'}，把名称里
 *       带空格的货主筛出来，而操作人的本意是不筛选；</li>
 *   <li><b>详情不存在时是 40400 而不是空对象</b>——返回一个字段全 {@code null} 的详情，界面会渲染成
 *       「这个货主什么都没有」，而它其实已经不存在了。</li>
 * </ul>
 */
@DisplayName("货主列表与详情")
class OwnerQueryServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-23T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-10-24T08:30:00Z");

    private OwnerRepository ownerRepository;
    private OwnerQueryService service;

    @BeforeEach
    void setUp() {
        ownerRepository = mock(OwnerRepository.class);
        service = new OwnerQueryService(ownerRepository);
    }

    @Test
    @DisplayName("页码小于 1：拒绝，且一条 SQL 不发")
    void shouldRejectPageNoBelowOne() {
        for (Integer pageNo : List.of(0, -1, -100)) {
            assertThatThrownBy(() -> service.page(pageNo, 20, null, null, null))
                    .as("页码 %s 必须被拒绝", pageNo)
                    .isInstanceOfSatisfying(BizException.class, e -> {
                        assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                        assertThat(e.getMessage()).contains("页码");
                    });
        }

        // 顺序证据：参数不合法时计数与分页查询一次都不该发生
        verifyNoInteractions(ownerRepository);
    }

    @Test
    @DisplayName("每页条数为 0 或超过 200：拒绝而不是静默截断，且一条 SQL 不发")
    void shouldRejectPageSizeOutOfRange() {
        for (Integer pageSize : List.of(0, -5, 201, 1000)) {
            assertThatThrownBy(() -> service.page(1, pageSize, null, null, null))
                    .as("每页条数 %s 必须被拒绝", pageSize)
                    .isInstanceOfSatisfying(BizException.class, e -> {
                        assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                        assertThat(e.getMessage()).contains("200");
                    });
        }

        verifyNoInteractions(ownerRepository);
    }

    @Test
    @DisplayName("状态编码未知：拒绝，且一条 SQL 不发")
    void shouldRejectUnknownStatus() {
        assertThatThrownBy(() -> service.page(1, 20, null, null, "FOO"))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                    // 提示里带上实际提交的值：只说「状态不合法」，用户不知道是哪个值不对
                    assertThat(e.getMessage()).contains("FOO");
                });

        verifyNoInteractions(ownerRepository);
    }

    @Test
    @DisplayName("状态编码按枚举名解析：合法值原样下传，空白按不筛选处理")
    void shouldResolveStatusByEnumName() {
        given(ownerRepository.count(any(), any(), any())).willReturn(0L);
        given(ownerRepository.findPage(any(), any(), any(), anyInt(), anyLong())).willReturn(List.of());

        service.page(1, 20, null, null, " ACTIVE ");
        verify(ownerRepository).count(OwnerStatus.ACTIVE, null, null);

        service.page(1, 20, null, null, "  ");
        // 空白不是「未知编码」：下拉框留空的表单会提交空串，把它当非法值拒绝会让人以为什么都没选也报错
        verify(ownerRepository).count(isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("全空白的筛选条件按未填处理：不拼 LIKE '% %'")
    void shouldNormalizeBlankFilters() {
        given(ownerRepository.count(any(), any(), any())).willReturn(0L);
        given(ownerRepository.findPage(any(), any(), any(), anyInt(), anyLong())).willReturn(List.of());

        service.page(1, 20, "   ", " ", "");

        verify(ownerRepository).count(null, null, null);
        verify(ownerRepository).findPage(null, null, null, 20, 0L);
    }

    @Test
    @DisplayName("分页：offset 按页码与条数折算，总数取计数的结果，行字段原样带出")
    void shouldMapPageWithOffsetAndTotal() {
        Owner owner = sampleOwner();
        given(ownerRepository.count(eq(OwnerStatus.ACTIVE), eq("BRAND"), eq("某某"))).willReturn(41L);
        given(ownerRepository.findPage(eq(OwnerStatus.ACTIVE), eq("BRAND"), eq("某某"), eq(20), eq(40L)))
                .willReturn(List.of(owner));

        OwnerPage page = service.page(3, 20, "某某", "BRAND", "ACTIVE");

        assertThat(page.pageNo()).isEqualTo(3);
        assertThat(page.pageSize()).isEqualTo(20);
        assertThat(page.total()).as("total 是满足条件的总数，不是本页条数").isEqualTo(41L);
        assertThat(page.records()).hasSize(1);
        OwnerPage.Summary row = page.records().get(0);
        assertThat(row.id()).isEqualTo(7L);
        assertThat(row.code()).isEqualTo("OWNER0007");
        assertThat(row.name()).isEqualTo("深圳某某贸易");
        assertThat(row.ownerType()).isEqualTo("BRAND");
        assertThat(row.status()).isEqualTo(OwnerStatus.ACTIVE);
        assertThat(row.updatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    @DisplayName("筛选结果为空：records 是空列表而不是 null")
    void shouldReturnEmptyRecordsInsteadOfNull() {
        given(ownerRepository.count(any(), any(), any())).willReturn(0L);
        given(ownerRepository.findPage(any(), any(), any(), anyInt(), anyLong())).willReturn(List.of());

        OwnerPage page = service.page(null, null, null, null, null);

        // 省略参数时取默认值：页码 1、每页 20
        assertThat(page.pageNo()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(20);
        assertThat(page.records()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("详情不存在：40400，文案与编辑接口一致")
    void shouldRejectMissingDetail() {
        given(ownerRepository.findById(anyLong())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(999_999L))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("货主不存在");
                });
    }

    @Test
    @DisplayName("详情：摘要字段与基本信息页签的字段全部带出，含前缀原文与地址六字段")
    void shouldMapDetail() {
        given(ownerRepository.findById(7L)).willReturn(Optional.of(sampleOwner()));

        OwnerDetail detail = service.detail(7L);

        assertThat(detail.summary().id()).isEqualTo(7L);
        assertThat(detail.summary().code()).isEqualTo("OWNER0007");
        assertThat(detail.summary().name()).isEqualTo("深圳某某贸易");
        assertThat(detail.summary().ownerType()).isEqualTo("BRAND");
        assertThat(detail.summary().status()).isEqualTo(OwnerStatus.ACTIVE);
        assertThat(detail.summary().updatedAt()).isEqualTo(UPDATED_AT);
        assertThat(detail.nameEn()).isEqualTo("Shenzhen ABC Trading");
        assertThat(detail.shortName()).isEqualTo("某某贸易");
        assertThat(detail.externalCode()).isEqualTo("EXT-001");
        // 前缀带的是值对象里的原文，没有大小写转换或字符替换
        assertThat(detail.skuPrefix()).isEqualTo("AMZ2");
        assertThat(detail.settlementEntity()).isEqualTo("深圳某某贸易有限公司");
        assertThat(detail.address().country()).isEqualTo("CN");
        assertThat(detail.address().province()).isEqualTo("CN-GD");
        assertThat(detail.address().city()).isEqualTo("深圳");
        assertThat(detail.address().district()).isEqualTo("南山区");
        assertThat(detail.address().postalCode()).isEqualTo("518000");
        assertThat(detail.address().addressLine()).isEqualTo("科技园路 1 号");
        assertThat(detail.remark()).isEqualTo("集成测试备注");
        assertThat(detail.createdAt()).isEqualTo(CREATED_AT);
    }

    /**
     * 构造一条已落库形态的货主，字段值与断言一一对应。
     *
     * @return 货主聚合
     */
    private static Owner sampleOwner() {
        return new Owner(
                7L,
                "OWNER0007",
                "深圳某某贸易",
                "某某贸易",
                "Shenzhen ABC Trading",
                "EXT-001",
                new SkuPrefix("AMZ2"),
                "BRAND",
                "深圳某某贸易有限公司",
                OwnerStatus.ACTIVE,
                new OwnerAddress("CN", "CN-GD", "深圳", "南山区", "518000", "科技园路 1 号"),
                "集成测试备注",
                CREATED_AT,
                42L,
                UPDATED_AT,
                42L);
    }
}

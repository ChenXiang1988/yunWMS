package com.yunwms.auth.application.query;

import com.yunwms.auth.application.dto.RoleDetail;
import com.yunwms.auth.application.dto.RolePage;
import com.yunwms.auth.domain.model.DataScopeSetting;
import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.model.RoleStatus;
import com.yunwms.auth.domain.model.RoleSummary;
import com.yunwms.auth.domain.port.DataScopeQuery;
import com.yunwms.auth.domain.port.RoleQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 角色查询用例的单测。
 *
 * <p>端口替换为 mock，不起 Spring、不连库。这里钉住的是用例自己的职责：分页参数的合法范围、
 * 关键字归一、页码到偏移量的换算，以及「角色不存在按 404 处理」。
 * 聚合计数的正确性属于 SQL，只能在集成测试里验证。
 */
@DisplayName("角色查询")
class RoleQueryServiceTest {

    private static final long ROLE_ID = 2L;

    private RoleQuery roleQuery;
    private DataScopeQuery dataScopeQuery;
    private RoleQueryService roleQueryService;

    @BeforeEach
    void setUp() {
        roleQuery = mock(RoleQuery.class);
        dataScopeQuery = mock(DataScopeQuery.class);
        roleQueryService = new RoleQueryService(roleQuery, dataScopeQuery);
    }

    @Test
    @DisplayName("分页：省略参数时取第 1 页每页 20 条，偏移量为 0")
    void shouldUseDefaultsWhenPagingParametersMissing() {
        given(roleQuery.count(null, null)).willReturn(0L);
        given(roleQuery.findPage(null, null, 20, 0L)).willReturn(List.of());

        RolePage page = roleQueryService.page(null, null, null, null);

        assertThat(page.pageNo()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(20);
        assertThat(page.total()).isZero();
        assertThat(page.records()).isEmpty();
    }

    @Test
    @DisplayName("分页：偏移量按 (页码 - 1) × 每页条数换算，筛选条件与归一后的关键字原样下传")
    void shouldConvertPageNoToOffset() {
        given(roleQuery.count("客服", RoleStatus.ACTIVE)).willReturn(45L);
        given(roleQuery.findPage("客服", RoleStatus.ACTIVE, 20, 40L)).willReturn(List.of(summary()));

        RolePage page = roleQueryService.page(3, 20, "  客服  ", RoleStatus.ACTIVE);

        verify(roleQuery).findPage("客服", RoleStatus.ACTIVE, 20, 40L);
        assertThat(page.pageNo()).isEqualTo(3);
        assertThat(page.total()).isEqualTo(45L);
        assertThat(page.records()).hasSize(1);
    }

    @Test
    @DisplayName("分页：关键字全空白按不筛选处理")
    void shouldTreatBlankKeywordAsNoFilter() {
        given(roleQuery.count(null, null)).willReturn(0L);
        given(roleQuery.findPage(null, null, 20, 0L)).willReturn(List.of());

        roleQueryService.page(1, 20, "   ", null);

        // 不归一会变成 LIKE '% %'，把编码或名称里恰好带空格的记录筛出来
        verify(roleQuery).count(null, null);
    }

    @Test
    @DisplayName("分页参数越界：页码小于 1 与每页条数超出 1 到 200 都被拒绝，且一条 SQL 都不发")
    void shouldRejectOutOfRangePaging() {
        assertThat(failureOf(0, null).getMessage()).isEqualTo("页码必须从 1 开始");
        assertThat(failureOf(-1, null).getMessage()).isEqualTo("页码必须从 1 开始");
        assertThat(failureOf(null, 0).getMessage()).isEqualTo("每页条数必须在 1 到 200 之间");
        assertThat(failureOf(null, 201).getMessage()).isEqualTo("每页条数必须在 1 到 200 之间");

        // 静默截断成 200 会让前端把「只看到 200 条」当成「一共 200 条」，所以必须拒绝而不是修正
        verifyNoInteractions(roleQuery);
    }

    @Test
    @DisplayName("分页：每页 200 条是允许的边界，不被截断")
    void shouldAllowMaximumPageSize() {
        given(roleQuery.count(null, null)).willReturn(0L);
        given(roleQuery.findPage(null, null, 200, 0L)).willReturn(List.of());

        RolePage page = roleQueryService.page(1, 200, null, null);

        assertThat(page.pageSize()).isEqualTo(200);
        verify(roleQuery).findPage(null, null, 200, 0L);
    }

    @Test
    @DisplayName("详情：返回摘要与勾选的菜单，菜单按标识升序")
    void shouldReturnDetailWithMenuIds() {
        given(roleQuery.findById(ROLE_ID)).willReturn(Optional.of(summary()));
        given(roleQuery.findMenuIds(ROLE_ID)).willReturn(List.of(3L, 4L));
        given(dataScopeQuery.findRoleSetting(ROLE_ID))
                .willReturn(Optional.of(new DataScopeSetting(DataScopeType.CUSTOM, Set.of(5L, 6L))));

        RoleDetail detail = roleQueryService.detail(ROLE_ID);

        assertThat(detail.summary().code()).isEqualTo("CS_STAFF");
        assertThat(detail.summary().userCount()).isZero();
        assertThat(detail.menuIds()).containsExactly(3L, 4L);
        // 数据范围随详情回显：配置页要按它回填类型与已选货主
        assertThat(detail.dataScopeType()).isEqualTo(DataScopeType.CUSTOM);
        assertThat(detail.ownerIds()).containsExactly(5L, 6L);
    }

    @Test
    @DisplayName("详情：角色不存在按 404 处理，不再去查菜单")
    void shouldRejectMissingRole() {
        given(roleQuery.findById(ROLE_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> roleQueryService.detail(ROLE_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(roleQuery, never()).findMenuIds(ROLE_ID);
    }

    private static RoleSummary summary() {
        return new RoleSummary(ROLE_ID, "CS_STAFF", "客服", false, RoleStatus.ACTIVE, 0L, 2L, "对接货主");
    }

    private BizException failureOf(Integer pageNo, Integer pageSize) {
        try {
            roleQueryService.page(pageNo, pageSize, null, null);
        } catch (BizException e) {
            return e;
        }
        throw new AssertionError("期望分页参数被拒绝，但通过了");
    }
}

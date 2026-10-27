package com.yunwms.auth.application.query;

import com.yunwms.auth.application.dto.UserPage;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.domain.model.UserSummary;
import com.yunwms.auth.domain.port.UserQuery;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 用户查询用例的单测。
 *
 * <p>端口替换为 mock，不起 Spring、不连库。这里钉住的是用例自己的职责：分页参数的合法范围、
 * 关键字归一、页码到偏移量的换算、筛选条件原样下传，以及「用户不存在按 404 处理」。
 * 货主名称与角色的拼装属于仓储实现与 SQL，只能在集成测试里验证。
 */
@DisplayName("用户查询")
class UserQueryServiceTest {

    private static final long USER_ID = 7L;

    private UserQuery userQuery;
    private UserQueryService userQueryService;

    @BeforeEach
    void setUp() {
        userQuery = mock(UserQuery.class);
        userQueryService = new UserQueryService(userQuery);
    }

    @Test
    @DisplayName("分页：省略参数时取第 1 页每页 20 条，偏移量为 0，且不带任何筛选")
    void shouldUseDefaultsWhenPagingParametersMissing() {
        given(userQuery.count(null, null, null)).willReturn(0L);
        given(userQuery.findPage(null, null, null, 20, 0L)).willReturn(List.of());

        UserPage page = userQueryService.page(null, null, null, null, null);

        assertThat(page.pageNo()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(20);
        assertThat(page.total()).isZero();
        assertThat(page.records()).isEmpty();
    }

    @Test
    @DisplayName("分页：偏移量按 (页码 - 1) × 每页条数换算，三个筛选条件与归一后的关键字原样下传")
    void shouldConvertPageNoToOffset() {
        given(userQuery.count(SubjectType.OWNER, UserStatus.ACTIVE, "zhang")).willReturn(45L);
        given(userQuery.findPage(SubjectType.OWNER, UserStatus.ACTIVE, "zhang", 20, 40L))
                .willReturn(List.of(summary()));

        UserPage page = userQueryService.page(3, 20, SubjectType.OWNER, UserStatus.ACTIVE, "  zhang  ");

        verify(userQuery).findPage(SubjectType.OWNER, UserStatus.ACTIVE, "zhang", 20, 40L);
        assertThat(page.pageNo()).isEqualTo(3);
        assertThat(page.total()).isEqualTo(45L);
        assertThat(page.records()).hasSize(1);
    }

    @Test
    @DisplayName("分页：关键字全空白按不筛选处理")
    void shouldTreatBlankKeywordAsNoFilter() {
        given(userQuery.count(null, null, null)).willReturn(0L);
        given(userQuery.findPage(null, null, null, 20, 0L)).willReturn(List.of());

        userQueryService.page(1, 20, null, null, "   ");

        // 不归一会变成 LIKE '% %'，把登录名或姓名里恰好带空格的记录筛出来
        verify(userQuery).count(null, null, null);
    }

    @Test
    @DisplayName("分页参数越界：页码小于 1 与每页条数超出 1 到 200 都被拒绝，且一条 SQL 都不发")
    void shouldRejectOutOfRangePaging() {
        assertThat(failureOf(0, null).getMessage()).isEqualTo("页码必须从 1 开始");
        assertThat(failureOf(-1, null).getMessage()).isEqualTo("页码必须从 1 开始");
        assertThat(failureOf(null, 0).getMessage()).isEqualTo("每页条数必须在 1 到 200 之间");
        assertThat(failureOf(null, 201).getMessage()).isEqualTo("每页条数必须在 1 到 200 之间");

        // 静默截断成 200 会让前端把「只看到 200 条」当成「一共 200 条」，所以必须拒绝而不是修正
        verifyNoInteractions(userQuery);
    }

    @Test
    @DisplayName("分页：每页 200 条是允许的边界，不被截断")
    void shouldAllowMaximumPageSize() {
        given(userQuery.count(null, null, null)).willReturn(0L);
        given(userQuery.findPage(null, null, null, 200, 0L)).willReturn(List.of());

        UserPage page = userQueryService.page(1, 200, null, null, null);

        assertThat(page.pageSize()).isEqualTo(200);
        verify(userQuery).findPage(null, null, null, 200, 0L);
    }

    @Test
    @DisplayName("详情：原样返回端口给出的详情，不做二次拼装")
    void shouldReturnDetail() {
        UserProfile profile = profile();
        given(userQuery.findById(USER_ID)).willReturn(Optional.of(profile));

        UserProfile detail = userQueryService.detail(USER_ID);

        assertThat(detail).isSameAs(profile);
        assertThat(detail.summary().loginName()).isEqualTo("zhangsan");
        assertThat(detail.roleIds()).containsExactly(3L);
    }

    @Test
    @DisplayName("详情：用户不存在按 404 处理")
    void shouldRejectMissingUser() {
        given(userQuery.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userQueryService.detail(USER_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private static UserSummary summary() {
        return new UserSummary(USER_ID, "zhangsan", "张三", SubjectType.STAFF, null, null,
                UserStatus.ACTIVE, List.of("CS_STAFF"), null);
    }

    private static UserProfile profile() {
        return new UserProfile(summary(), "zhangsan@example.com", "wx_zhangsan", null, List.of(),
                Instant.parse("2026-01-04T08:30:00Z"), List.of(3L));
    }

    private BizException failureOf(Integer pageNo, Integer pageSize) {
        try {
            userQueryService.page(pageNo, pageSize, null, null, null);
        } catch (BizException e) {
            return e;
        }
        throw new AssertionError("期望分页参数被拒绝，但通过了");
    }
}

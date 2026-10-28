package com.yunwms.auth.application.query;

import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditLog;
import com.yunwms.auth.domain.model.AuditPage;
import com.yunwms.auth.domain.port.AuditQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 审计查询用例的单测。
 *
 * <p>端口替换为 mock，不起 Spring、不连库。这里钉住的是用例自己的职责：必填项判定、分页参数的合法范围、
 * 对象标识的归一。排序与分页在 SQL 上的效果属于 {@link AuditQuery} 的实现，由集成测试验证。
 */
@DisplayName("审计查询用例")
class AuditQueryServiceTest {

    private static final String BIZ_ID = "OWNER0001";

    private AuditQuery auditQuery;
    private AuditQueryService auditQueryService;

    @BeforeEach
    void setUp() {
        auditQuery = mock(AuditQuery.class);
        auditQueryService = new AuditQueryService(auditQuery);
    }

    @Test
    @DisplayName("分页：省略参数时取第 1 页每页 20 条")
    void shouldUseDefaultsWhenPagingParametersMissing() {
        given(auditQuery.findByObject(AuditBizType.OWNER, BIZ_ID, 1, 20))
                .willReturn(new AuditPage(1, 20, 0, List.of()));

        AuditPage page = auditQueryService.page(AuditBizType.OWNER, BIZ_ID, null, null);

        assertThat(page.pageNo()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(20);
        assertThat(page.records()).isEmpty();
    }

    @Test
    @DisplayName("分页：对象标识两侧空白被裁掉，其余原样下传")
    void shouldStripBizId() {
        given(auditQuery.findByObject(AuditBizType.OWNER, BIZ_ID, 2, 50))
                .willReturn(new AuditPage(2, 50, 1, List.of(log())));

        AuditPage page = auditQueryService.page(AuditBizType.OWNER, "  OWNER0001  ", 2, 50);

        verify(auditQuery).findByObject(AuditBizType.OWNER, BIZ_ID, 2, 50);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).hasSize(1);
    }

    @Test
    @DisplayName("必填项：对象类型或对象标识缺失被拒绝，且一条 SQL 都不发")
    void shouldRejectMissingRequiredFilters() {
        assertThat(failureOf(null, BIZ_ID, null, null).getMessage()).isEqualTo("审计对象类型不能为空");
        assertThat(failureOf(AuditBizType.OWNER, null, null, null).getMessage()).isEqualTo("审计对象标识不能为空");
        assertThat(failureOf(AuditBizType.OWNER, "   ", null, null).getMessage()).isEqualTo("审计对象标识不能为空");

        // 少任一条件就退化成全表分页，而审计表只会越来越大
        verifyNoInteractions(auditQuery);
    }

    @Test
    @DisplayName("分页参数越界：页码小于 1 与每页条数超出 1 到 200 都被拒绝，且一条 SQL 都不发")
    void shouldRejectOutOfRangePaging() {
        assertThat(failureOf(AuditBizType.OWNER, BIZ_ID, 0, null).getMessage()).isEqualTo("页码必须从 1 开始");
        assertThat(failureOf(AuditBizType.OWNER, BIZ_ID, -1, null).getMessage()).isEqualTo("页码必须从 1 开始");
        assertThat(failureOf(AuditBizType.OWNER, BIZ_ID, null, 0).getMessage())
                .isEqualTo("每页条数必须在 1 到 200 之间");
        assertThat(failureOf(AuditBizType.OWNER, BIZ_ID, null, 201).getMessage())
                .isEqualTo("每页条数必须在 1 到 200 之间");

        // 静默截断成 200 会让前端把「只看到 200 条」当成「一共 200 条」，所以必须拒绝而不是修正
        verifyNoInteractions(auditQuery);
    }

    @Test
    @DisplayName("分页：每页 200 条是允许的边界，不被截断")
    void shouldAllowMaximumPageSize() {
        given(auditQuery.findByObject(AuditBizType.OWNER, BIZ_ID, 1, 200))
                .willReturn(new AuditPage(1, 200, 0, List.of()));

        AuditPage page = auditQueryService.page(AuditBizType.OWNER, BIZ_ID, 1, 200);

        assertThat(page.pageSize()).isEqualTo(200);
        verify(auditQuery).findByObject(AuditBizType.OWNER, BIZ_ID, 1, 200);
    }

    private static AuditLog log() {
        return new AuditLog(9L, AuditBizType.OWNER, BIZ_ID, AuditAction.UPDATE, 3L, "张三",
                "10.0.0.8", null, "修改结算主体", null, Map.of("name", "新值"),
                Instant.parse("2026-10-28T02:03:04Z"));
    }

    /** 取一次失败的业务异常；通过则说明用例没有拒绝，直接让用例失败。 */
    private BizException failureOf(AuditBizType bizType, String bizId, Integer pageNo, Integer pageSize) {
        try {
            auditQueryService.page(bizType, bizId, pageNo, pageSize);
        } catch (BizException e) {
            assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
            return e;
        }
        throw new AssertionError("期望参数被拒绝，但通过了");
    }
}

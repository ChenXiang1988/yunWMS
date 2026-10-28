package com.yunwms.auth.infrastructure.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditEntry;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.infrastructure.persistence.entity.SysAuditLogEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysAuditLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 审计写入实现的单测。
 *
 * <p>要钉住的是实现自己补全的那部分：枚举怎么落库、操作人与来源端从哪来、取不到操作人时还写不写。
 * 内容与快照的其它字段由 {@link AuditEntry} 携带，正确性在集成测试里连库一起验证。
 *
 * <p>不起库：本模块的测试没有数据库依赖，真实 PostgreSQL 上 {@code jsonb} / {@code inet} 的
 * {@code CAST} 行为由 {@code wms-app} 的 {@code AuditLogIntegrationTest} 覆盖。两条测试分工：
 * 这里盯「填进去的是不是想要的值」，那边盯「这些值能不能落进这张表、读回来还是不是原来那些」。
 */
@DisplayName("审计写入")
class AuditRecorderImplTest {

    private static final long OPERATOR_ID = 42L;
    private static final String OPERATOR_NAME = "张三";

    private SysAuditLogMapper sysAuditLogMapper;
    private CurrentSession currentSession;
    private AuditRecorderImpl recorder;

    @BeforeEach
    void setUp() {
        sysAuditLogMapper = mock(SysAuditLogMapper.class);
        currentSession = mock(CurrentSession.class);
        recorder = new AuditRecorderImpl(sysAuditLogMapper, currentSession, new ObjectMapper());
    }

    /** 请求上下文是线程级的静态状态，不清掉会漏给同线程的下一个用例。 */
    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("写入：对象类型与动作按枚举名落库，操作人与来源端由实现补全")
    void shouldPersistEnumNamesAndOperatorFromSessionAndRequest() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
        given(currentSession.currentUserDisplayName()).willReturn(Optional.of(OPERATOR_NAME));
        bindRequest("/api/console/owners", "");

        recorder.record(entry(AuditBizType.OWNER, "OWNER0001", AuditAction.UPDATE));

        SysAuditLogEntity row = capturedRow();
        // 存 ordinal 会在枚举顺序调整后静默错位；库里该列是 varchar 且有 CHECK
        assertThat(row.getBizType()).isEqualTo("OWNER");
        assertThat(row.getAction()).isEqualTo("UPDATE");
        assertThat(row.getBizId()).isEqualTo("OWNER0001");
        assertThat(row.getOperatorId()).isEqualTo(OPERATOR_ID);
        // 姓名取自会话快照，不回查 sys_user：人员之后改名不该改写历史记录
        assertThat(row.getOperatorName()).isEqualTo(OPERATOR_NAME);
        assertThat(row.getOperatorIp()).isEqualTo("127.0.0.1");
        assertThat(row.getSourcePortal()).isEqualTo("CONSOLE");
        // 标识与时间交给数据库：id 是 IDENTITY，created_at 有 DEFAULT now()
        assertThat(row.getId()).isNull();
        assertThat(row.getCreatedAt()).isNull();
    }

    @Test
    @DisplayName("写入：货主端请求的来源端是 PORTAL，与操作人的主体类型无关")
    void shouldRecordPortalForPortalRequest() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
        given(currentSession.currentUserDisplayName()).willReturn(Optional.of(OPERATOR_NAME));
        bindRequest("/api/portal/owners/me", "");

        recorder.record(entry(AuditBizType.OWNER_CONTACT, "12", AuditAction.CREATE));

        assertThat(capturedRow().getSourcePortal()).isEqualTo("PORTAL");
    }

    @Test
    @DisplayName("写入：有 context path 的部署仍能认出端，不会把来源端记成空")
    void shouldIgnoreContextPathWhenResolvingPortal() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
        given(currentSession.currentUserDisplayName()).willReturn(Optional.of(OPERATOR_NAME));
        bindRequest("/wms/api/console/owners", "/wms");

        recorder.record(entry(AuditBizType.OWNER, "OWNER0001", AuditAction.UPDATE));

        assertThat(capturedRow().getSourcePortal()).isEqualTo("CONSOLE");
    }

    @Test
    @DisplayName("写入：共用路径且会话里没有主体类型时来源端为空，不凭空造一个端")
    void shouldLeavePortalEmptyForSharedPath() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
        given(currentSession.currentUserDisplayName()).willReturn(Optional.of(OPERATOR_NAME));
        bindRequest("/api/auth/login", "");

        recorder.record(entry(AuditBizType.USER, "7", AuditAction.LOGIN));

        // 登录失败就是这种形态：路径判不出端，又没有会话可回退。空值在语义上是「没认出来」，
        // 由调用方在摘要里说明，而不是按某个默认值猜一个端写进审计
        assertThat(capturedRow().getSourcePortal()).isNull();
    }

    @Test
    @DisplayName("写入：共用路径判不出来源端时回退到会话主体类型，员工记 CONSOLE")
    void shouldFallBackToStaffSubjectTypeForSharedPath() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
        given(currentSession.currentUserDisplayName()).willReturn(Optional.of(OPERATOR_NAME));
        given(currentSession.currentSubjectType()).willReturn(Optional.of(SubjectType.STAFF));
        // 登录成功就是这种形态：两端共用一个 URL，会话由本次请求刚刚建立
        bindRequest("/api/auth/login", "");

        recorder.record(entry(AuditBizType.USER, "admin", AuditAction.LOGIN));

        assertThat(capturedRow().getSourcePortal()).isEqualTo("CONSOLE");
    }

    @Test
    @DisplayName("写入：共用路径下货主账号登录的来源端是 PORTAL")
    void shouldFallBackToOwnerSubjectTypeForSharedPath() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
        given(currentSession.currentUserDisplayName()).willReturn(Optional.of(OPERATOR_NAME));
        given(currentSession.currentSubjectType()).willReturn(Optional.of(SubjectType.OWNER));
        bindRequest("/api/auth/login", "");

        recorder.record(entry(AuditBizType.USER, "OWNER0001", AuditAction.LOGIN));

        assertThat(capturedRow().getSourcePortal()).isEqualTo("PORTAL");
    }

    @Test
    @DisplayName("写入：路径能判定端时以路径为准，不被会话的主体类型覆盖")
    void shouldPreferPathOverSubjectType() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
        given(currentSession.currentUserDisplayName()).willReturn(Optional.of(OPERATOR_NAME));
        // 会话是货主账号、请求却走仓网端接口：来源端要记 CONSOLE，路径是事实，主体类型只是能力
        given(currentSession.currentSubjectType()).willReturn(Optional.of(SubjectType.OWNER));
        bindRequest("/api/console/owners", "");

        recorder.record(entry(AuditBizType.OWNER, "OWNER0001", AuditAction.UPDATE));

        assertThat(capturedRow().getSourcePortal()).isEqualTo("CONSOLE");
    }

    @Test
    @DisplayName("写入：没有请求上下文时不问会话，来源端留空")
    void shouldNotDerivePortalWithoutRequest() {
        // 定时任务、回调线程：不是「这次请求从哪个端来」的问题，而是根本没有端
        given(currentSession.currentSubjectType()).willReturn(Optional.of(SubjectType.STAFF));

        recorder.record(entry(AuditBizType.SKU, "SKU0001", AuditAction.UPDATE));

        assertThat(capturedRow().getSourcePortal()).isNull();
        verify(currentSession, never()).currentSubjectType();
    }

    @Test
    @DisplayName("写入：取不到操作人（系统内部触发）时仍然写入，操作人标识与来源地址为空")
    void shouldStillPersistWhenOperatorIsUnavailable() {
        // 不绑定请求上下文，就是定时任务 / 回调线程的形态：此时连会话都不该去问——
        // Sa-Token 在没有上下文的线程上抛的是「上下文尚未初始化」，接住它会把真正的上下文缺陷藏起来
        recorder.record(entry(AuditBizType.SKU, "SKU0001", AuditAction.UPDATE));

        SysAuditLogEntity row = capturedRow();
        // 审计的价值在于「记下了」：因为取不到人就不记，等于给审计留了一个可以被绕开的口子
        assertThat(row.getOperatorId()).isNull();
        assertThat(row.getOperatorIp()).isNull();
        assertThat(row.getSourcePortal()).isNull();
        // operator_name 是 NOT NULL 列；这条日志确实不是某个登录用户触发的
        assertThat(row.getOperatorName()).isEqualTo("系统");
        assertThat(row.getBizType()).isEqualTo("SKU");
    }

    @Test
    @DisplayName("写入：有会话但会话里没有姓名时，不把真人写成系统")
    void shouldNotBlameSystemWhenSessionHasNoDisplayName() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
        given(currentSession.currentUserDisplayName()).willReturn(Optional.empty());
        // 有请求上下文才谈得上「有会话」；旧版本签发的会话不带姓名，就是这条用例的形态
        bindRequest("/api/console/owners", "");

        recorder.record(entry(AuditBizType.OWNER, "OWNER0001", AuditAction.UPDATE));

        SysAuditLogEntity row = capturedRow();
        assertThat(row.getOperatorId()).isEqualTo(OPERATOR_ID);
        assertThat(row.getOperatorName()).isEqualTo("未知操作人");
    }

    @Test
    @DisplayName("写入：两份快照序列化成 JSON 文本，空值字段不被丢掉")
    void shouldSerializeSnapshotsIncludingNullValues() {
        Map<String, Object> before = new HashMap<>();
        before.put("weight", 12.5);
        before.put("remark", null);
        Map<String, Object> after = new HashMap<>();
        after.put("weight", 13.0);
        after.put("remark", null);

        recorder.record(new AuditEntry(AuditBizType.SKU, "SKU0001", AuditAction.UPDATE,
                "修改重量", before, after));

        SysAuditLogEntity row = capturedRow();
        // 显式的 null 是业务事实（这个字段被清空过），序列化时丢掉它会让前后快照看起来没差别
        assertThat(row.getBeforeValue()).contains("\"remark\":null").contains("\"weight\":12.5");
        assertThat(row.getAfterValue()).contains("\"weight\":13.0");
    }

    @Test
    @DisplayName("写入：没有变更前值时落 SQL NULL，而不是空对象")
    void shouldKeepMissingSnapshotAsNull() {
        recorder.record(new AuditEntry(AuditBizType.OWNER, "OWNER0001", AuditAction.CREATE,
                "新建货主", null, Map.of("name", "测试货主")));

        SysAuditLogEntity row = capturedRow();
        assertThat(row.getBeforeValue()).isNull();
        assertThat(row.getAfterValue()).isEqualTo("{\"name\":\"测试货主\"}");
    }

    @Test
    @DisplayName("写入：内容不合法时当场失败，不产生一条残缺的日志")
    void shouldRejectInvalidEntry() {
        // 断言在构造期：越过 AuditEntry 之后就是一条 INSERT，那时只剩列宽报错或静默截断
        assertThatThrownBy(() -> new AuditEntry(AuditBizType.OWNER, null, AuditAction.CREATE, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("对象标识");
        assertThatThrownBy(() -> new AuditEntry(AuditBizType.OWNER, "  ", AuditAction.CREATE, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("对象标识");
        assertThatThrownBy(() -> new AuditEntry(null, "OWNER0001", AuditAction.CREATE, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("对象类型");
        assertThatThrownBy(() -> new AuditEntry(AuditBizType.OWNER, "OWNER0001", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("动作");
        assertThatThrownBy(() -> new AuditEntry(AuditBizType.OWNER, "A".repeat(65), AuditAction.CREATE,
                null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超过 64");
        assertThatThrownBy(() -> new AuditEntry(AuditBizType.OWNER, "OWNER0001", AuditAction.CREATE,
                "摘".repeat(501), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("超过 500");
    }

    /** 把当前线程的「请求上下文」指向一个 Mock 请求，模拟 Servlet 线程里的审计写入。 */
    private static void bindRequest(String requestUri, String contextPath) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestUri);
        request.setContextPath(contextPath);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static AuditEntry entry(AuditBizType bizType, String bizId, AuditAction action) {
        return new AuditEntry(bizType, bizId, action, "变更摘要", Map.of("name", "旧值"), Map.of("name", "新值"));
    }

    private SysAuditLogEntity capturedRow() {
        ArgumentCaptor<SysAuditLogEntity> captor = ArgumentCaptor.forClass(SysAuditLogEntity.class);
        verify(sysAuditLogMapper).append(captor.capture());
        return captor.getValue();
    }
}

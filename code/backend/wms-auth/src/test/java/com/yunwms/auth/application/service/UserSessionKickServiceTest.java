package com.yunwms.auth.application.service;

import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditEntry;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.domain.model.UserSummary;
import com.yunwms.auth.domain.port.AuditRecorder;
import com.yunwms.auth.domain.port.SessionInvalidator;
import com.yunwms.auth.domain.port.UserQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 踢下线用例的单测：端口全部替换为 mock。
 *
 * <p>「会话真的失效了」只能由集成测试证明（那是 Sa-Token 的行为），这里钉住本类自己的判断：
 * 对不存在的账号必须拒绝（否则接口回 200 而没人被踢）、失效的是<b>全部</b>会话而不是「其他会话」、
 * 顺序是先留痕后失效（踢自己时会话一失效，审计就读不到操作人）、审计写失败时不出手。
 * 「允许踢自己」不在这里测——本类不读操作人身份，没有「目标和操作人是否同一人」这个判断可测，
 * 那条行为由集成测试按真实会话验证。
 */
@DisplayName("踢下线")
class UserSessionKickServiceTest {

    private static final long TARGET_USER_ID = 9L;
    private static final String LOGIN_NAME = "zhangsan";

    private UserQuery userQuery;
    private SessionInvalidator sessionInvalidator;
    private AuditRecorder auditRecorder;
    private UserSessionKickService service;

    @BeforeEach
    void setUp() {
        userQuery = mock(UserQuery.class);
        sessionInvalidator = mock(SessionInvalidator.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new UserSessionKickService(userQuery, sessionInvalidator, auditRecorder);
    }

    @Test
    @DisplayName("踢存在的账号：先留一条写明动作的痕，再失效它的全部会话")
    void shouldRecordAuditThenInvalidateAllSessions() {
        givenTargetUser();

        service.kick(TARGET_USER_ID);

        // 顺序：目标可能是操作人自己，先失效会让审计读不到会话、把操作人记成「系统」（见类注释）
        InOrder order = inOrder(auditRecorder, sessionInvalidator);
        order.verify(auditRecorder).record(any(AuditEntry.class));
        order.verify(sessionInvalidator).invalidateAllOf(TARGET_USER_ID);
        // 管理端踢人没有「保留发起请求的那台设备」的语义，那是本人改密才要的
        verify(sessionInvalidator, never()).invalidateOthersOfCurrent(anyLong());

        AuditEntry entry = captureSingleAudit();
        assertThat(entry.bizType()).isEqualTo(AuditBizType.USER);
        // 按登录名追溯：主键是一串界面上不出现的数字
        assertThat(entry.bizId()).isEqualTo(LOGIN_NAME);
        assertThat(entry.action()).isEqualTo(AuditAction.UPDATE);
        // 摘要写明动作：「修改用户」看不出这次是改资料还是强制下线
        assertThat(entry.summary()).isEqualTo("踢下线用户 " + LOGIN_NAME);
        // 账号一个字段都没变，用户快照里也没有「会话」这个维度：两份相同的快照只会让人以为「改了什么」
        assertThat(entry.beforeValue()).isNull();
        assertThat(entry.afterValue()).isNull();
    }

    @Test
    @DisplayName("账号不存在：404 拒绝，既不失会话也不留痕")
    void shouldRejectMissingUser() {
        given(userQuery.findById(TARGET_USER_ID)).willReturn(Optional.empty());

        BizException failure = failureOf(TARGET_USER_ID);

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(failure.getMessage()).isEqualTo("用户不存在");
        // invalidateAllOf 对不存在的标识静默成功：少了这一判，接口会回 200，调用方以为踢了人
        verifyNoInteractions(sessionInvalidator, auditRecorder);
    }

    @Test
    @DisplayName("审计写不进去：异常透出，且不接着把人的会话打掉")
    void shouldNotInvalidateSessionsWhenAuditFails() {
        givenTargetUser();
        willThrow(new IllegalStateException("审计库不可用"))
                .given(auditRecorder).record(any(AuditEntry.class));

        assertThatThrownBy(() -> service.kick(TARGET_USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("审计库不可用");
        // 顺序的代价写在类注释里：先留痕再失效，所以审计失败时踢不出去；可重试，而不是留下错误归因
        verifyNoInteractions(sessionInvalidator);
    }

    /**
     * 造一份内部员工的详情。
     *
     * <p>已逻辑删除的账号由 {@code UserQuery#findById} 同样返回空，与「不存在」走同一条分支。
     */
    private void givenTargetUser() {
        UserSummary summary = new UserSummary(TARGET_USER_ID, LOGIN_NAME, "张三", SubjectType.STAFF, null, null,
                UserStatus.ACTIVE, List.of(), null);
        given(userQuery.findById(TARGET_USER_ID)).willReturn(Optional.of(new UserProfile(
                summary, "z@example.com", null, false, null, List.of(),
                Instant.parse("2026-01-01T00:00:00Z"), List.of())));
    }

    private AuditEntry captureSingleAudit() {
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRecorder).record(captor.capture());
        return captor.getValue();
    }

    private BizException failureOf(long userId) {
        try {
            service.kick(userId);
        } catch (BizException e) {
            return e;
        }
        throw new AssertionError("期望请求被拒绝，但通过了");
    }
}

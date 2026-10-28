package com.yunwms.auth.application.service;

import com.yunwms.auth.application.audit.AuditSnapshotKeys;
import com.yunwms.auth.application.command.ChangeUserStatusCommand;
import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditEntry;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.domain.model.UserSummary;
import com.yunwms.auth.domain.port.AuditRecorder;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.SessionInvalidator;
import com.yunwms.auth.domain.port.UserAccountUpdater;
import com.yunwms.auth.domain.port.UserQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 启用停用用例的单测。
 *
 * <p>端口全部替换为 mock。这里钉住的是三件单测能证、集成测试证得慢的事：
 * 停用一定伴随会话失效、启用一定不失效会话；两条自我保护（自己、最后一个可用超管）
 * 在被拒绝时一条写入都不发；重复停用照常成功（状态流转幂等）。
 *
 * <p>「最后一个可用超管」的判定口径属于 SQL，由集成测试覆盖；这里只钉住
 * 「判定为真就拒绝、为假才放行」这层接线。
 *
 * <p>留痕单独钉住：摘要必须写明方向（停用还是启用），被拒绝时不记，而重复停用两次就记两条——
 * 那两次提交确实都把账号写成了停用。
 */
@DisplayName("启用停用")
class UserStatusChangeServiceTest {

    private static final long OPERATOR_ID = 1L;
    private static final long OTHER_USER_ID = 9L;
    private static final String LOGIN_NAME = "zhangsan";

    private UserQuery userQuery;
    private UserAccountUpdater userAccountUpdater;
    private SessionInvalidator sessionInvalidator;
    private CurrentSession currentSession;
    private AuditRecorder auditRecorder;
    private UserStatusChangeService service;

    @BeforeEach
    void setUp() {
        userQuery = mock(UserQuery.class);
        userAccountUpdater = mock(UserAccountUpdater.class);
        sessionInvalidator = mock(SessionInvalidator.class);
        currentSession = mock(CurrentSession.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new UserStatusChangeService(userQuery, userAccountUpdater, sessionInvalidator, currentSession,
                auditRecorder);

        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
    }

    @Test
    @DisplayName("停用他人：写入 DISABLED 后立即失效该账号的全部会话")
    void shouldDisableAndInvalidateSessions() {
        givenUser(OTHER_USER_ID, UserStatus.ACTIVE);
        given(userQuery.isOnlyAvailableSuperAdmin(OTHER_USER_ID)).willReturn(false);

        service.change(new ChangeUserStatusCommand(OTHER_USER_ID, UserStatus.DISABLED));

        verify(userAccountUpdater).updateStatus(eq(OTHER_USER_ID), eq(UserStatus.DISABLED),
                eq(OPERATOR_ID), any(Instant.class));
        // 规格「停用用户」的后半句：既有会话立即失效。少了这一步，令牌还能用到过期
        verify(sessionInvalidator).invalidateAllOf(OTHER_USER_ID);
    }

    @Test
    @DisplayName("启用他人：只写状态，不碰会话——启用时把在线用户踢下线没有任何理由")
    void shouldEnableWithoutInvalidatingSessions() {
        givenUser(OTHER_USER_ID, UserStatus.DISABLED);

        service.change(new ChangeUserStatusCommand(OTHER_USER_ID, UserStatus.ACTIVE));

        verify(userAccountUpdater).updateStatus(eq(OTHER_USER_ID), eq(UserStatus.ACTIVE),
                eq(OPERATOR_ID), any(Instant.class));
        verifyNoInteractions(sessionInvalidator);
        // 启用不涉及「还剩几个超管」，不该为此付一次查询
        verify(userQuery, never()).isOnlyAvailableSuperAdmin(anyLong());
    }

    @Test
    @DisplayName("重复停用：返回成功，且照常补一次会话失效")
    void shouldTreatRepeatedDisableAsSuccess() {
        givenUser(OTHER_USER_ID, UserStatus.DISABLED);
        given(userQuery.isOnlyAvailableSuperAdmin(OTHER_USER_ID)).willReturn(false);

        service.change(new ChangeUserStatusCommand(OTHER_USER_ID, UserStatus.DISABLED));
        service.change(new ChangeUserStatusCommand(OTHER_USER_ID, UserStatus.DISABLED));

        verify(userAccountUpdater, times(2))
                .updateStatus(eq(OTHER_USER_ID), eq(UserStatus.DISABLED), eq(OPERATOR_ID), any(Instant.class));
        // 上一次停用若没失效成功（进程在两步之间挂掉），重复提交是唯一的补救入口，
        // 因此这里不能因为「状态没变」而跳过失效
        verify(sessionInvalidator, times(2)).invalidateAllOf(OTHER_USER_ID);
        // 两次提交都真的把账号写成了停用，因此各留一条痕：不按「状态没变」去猜哪次没有副作用
        verify(auditRecorder, times(2)).record(any(AuditEntry.class));
    }

    @Test
    @DisplayName("停用自己：409 拒绝，不查超管、不写入、不失效会话")
    void shouldRejectDisablingSelf() {
        givenUser(OPERATOR_ID, UserStatus.ACTIVE);

        BizException failure = failureOf(new ChangeUserStatusCommand(OPERATOR_ID, UserStatus.DISABLED));

        // 409 段位而不是 40000：入参合法，被拒绝的是它造成的状态
        assertThat(failure.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(failure.getMessage()).isEqualTo("不能停用自己");
        verify(userQuery, never()).isOnlyAvailableSuperAdmin(anyLong());
        verifyNoInteractions(userAccountUpdater, sessionInvalidator, auditRecorder);
    }

    @Test
    @DisplayName("停用最后一个可用超管：409 拒绝，不写入也不失效会话")
    void shouldRejectDisablingLastAvailableSuperAdmin() {
        givenUser(OTHER_USER_ID, UserStatus.ACTIVE);
        given(userQuery.isOnlyAvailableSuperAdmin(OTHER_USER_ID)).willReturn(true);

        BizException failure = failureOf(new ChangeUserStatusCommand(OTHER_USER_ID, UserStatus.DISABLED));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(failure.getMessage()).isEqualTo("不能停用系统中最后一个可用的超级管理员");
        verifyNoInteractions(userAccountUpdater, sessionInvalidator, auditRecorder);
    }

    @Test
    @DisplayName("用户不存在：404 拒绝，不查超管也不写入")
    void shouldRejectMissingUser() {
        given(userQuery.findById(OTHER_USER_ID)).willReturn(Optional.empty());

        BizException failure = failureOf(new ChangeUserStatusCommand(OTHER_USER_ID, UserStatus.DISABLED));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(failure.getMessage()).isEqualTo("用户不存在");
        verify(userQuery, never()).isOnlyAvailableSuperAdmin(anyLong());
        verifyNoInteractions(userAccountUpdater, sessionInvalidator, auditRecorder);
    }

    @Test
    @DisplayName("当前会话无效：40100，不查库也不写入")
    void shouldRejectMissingSession() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.empty());

        BizException failure = failureOf(new ChangeUserStatusCommand(OTHER_USER_ID, UserStatus.DISABLED));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(userQuery, never()).findById(anyLong());
        verifyNoInteractions(userAccountUpdater, sessionInvalidator, auditRecorder);
    }

    @Test
    @DisplayName("留痕：停用的摘要写明方向，前后快照只差状态")
    void shouldRecordDisableAuditWithDirection() {
        givenUser(OTHER_USER_ID, UserStatus.ACTIVE);
        given(userQuery.isOnlyAvailableSuperAdmin(OTHER_USER_ID)).willReturn(false);

        service.change(new ChangeUserStatusCommand(OTHER_USER_ID, UserStatus.DISABLED));

        AuditEntry entry = captureSingleAudit();
        assertThat(entry.bizType()).isEqualTo(AuditBizType.USER);
        assertThat(entry.action()).isEqualTo(AuditAction.UPDATE);
        assertThat(entry.bizId()).isEqualTo(LOGIN_NAME);
        // 「修改状态」看不出是收紧还是放宽，看日志的人需要知道方向
        assertThat(entry.summary()).isEqualTo("停用用户 " + LOGIN_NAME);
        assertThat(entry.beforeValue()).containsOnlyKeys(AuditSnapshotKeys.USER);
        assertThat(entry.afterValue()).containsOnlyKeys(AuditSnapshotKeys.USER);
        assertThat(entry.beforeValue()).containsEntry("status", "ACTIVE");
        assertThat(entry.afterValue()).containsEntry("status", "DISABLED");
    }

    @Test
    @DisplayName("留痕：启用同样写明方向")
    void shouldRecordEnableAuditWithDirection() {
        givenUser(OTHER_USER_ID, UserStatus.DISABLED);

        service.change(new ChangeUserStatusCommand(OTHER_USER_ID, UserStatus.ACTIVE));

        assertThat(captureSingleAudit().summary()).isEqualTo("启用用户 " + LOGIN_NAME);
    }

    /**
     * 造一份账号详情：登录名固定，状态按用例需要变化。
     *
     * @param userId 账号标识
     * @param status 库里的状态
     */
    private void givenUser(long userId, UserStatus status) {
        UserSummary summary = new UserSummary(userId, LOGIN_NAME, "张三", SubjectType.STAFF, null, null,
                status, List.of(), null);
        given(userQuery.findById(userId)).willReturn(Optional.of(new UserProfile(
                summary, "z@example.com", null, false, null, List.of(),
                Instant.parse("2026-01-01T00:00:00Z"), List.of())));
    }

    /**
     * 取回本次唯一一条审计内容。
     *
     * @return 提交给审计端口的日志内容
     */
    private AuditEntry captureSingleAudit() {
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRecorder).record(captor.capture());
        return captor.getValue();
    }

    private BizException failureOf(ChangeUserStatusCommand command) {
        try {
            service.change(command);
        } catch (BizException e) {
            return e;
        }
        throw new AssertionError("期望请求被拒绝，但通过了");
    }
}

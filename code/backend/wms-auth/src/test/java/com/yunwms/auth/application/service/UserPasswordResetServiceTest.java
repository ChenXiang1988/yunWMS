package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.ResetUserPasswordCommand;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.PasswordHasher;
import com.yunwms.auth.domain.port.SessionInvalidator;
import com.yunwms.auth.domain.port.UserAccountUpdater;
import com.yunwms.auth.domain.port.UserQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 管理员重置密码用例的单测。
 *
 * <p>端口全部替换为 mock。这里钉住三件事：自己不能重置自己（否则管理员重置接口就成了
 * 一条不验旧密码改自己密码的通道）、密码策略不通过时一次带回全部违规项且不落库、
 * 重置成功一定伴随「失效该账号全部会话」。
 */
@DisplayName("重置密码")
class UserPasswordResetServiceTest {

    private static final long OPERATOR_ID = 1L;
    private static final long OTHER_USER_ID = 9L;
    private static final String NEW_PASSWORD = "Init@1234";
    private static final String PASSWORD_HASH = "$2a$10$hash";

    private UserQuery userQuery;
    private UserAccountUpdater userAccountUpdater;
    private PasswordHasher passwordHasher;
    private SessionInvalidator sessionInvalidator;
    private CurrentSession currentSession;
    private UserPasswordResetService service;

    @BeforeEach
    void setUp() {
        userQuery = mock(UserQuery.class);
        userAccountUpdater = mock(UserAccountUpdater.class);
        passwordHasher = mock(PasswordHasher.class);
        sessionInvalidator = mock(SessionInvalidator.class);
        currentSession = mock(CurrentSession.class);
        service = new UserPasswordResetService(
                userQuery, userAccountUpdater, passwordHasher, sessionInvalidator, currentSession);

        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
    }

    @Test
    @DisplayName("重置成功：只把哈希交给写入端口，并失效该账号的全部会话")
    void shouldResetPasswordAndInvalidateSessions() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(true);
        given(passwordHasher.hash(NEW_PASSWORD)).willReturn(PASSWORD_HASH);

        service.reset(new ResetUserPasswordCommand(OTHER_USER_ID, NEW_PASSWORD));

        verify(userAccountUpdater).resetPassword(
                eq(OTHER_USER_ID), eq(PASSWORD_HASH), any(Instant.class), eq(OPERATOR_ID));
        // 凭据换了，别处手里的旧令牌必须立刻作废：改密只挡住下一次登录，挡不住已经在用的终端
        verify(sessionInvalidator).invalidateAllOf(OTHER_USER_ID);
    }

    @Test
    @DisplayName("重置自己：409 拒绝，不查库、不哈希、不写库")
    void shouldRejectResettingSelf() {
        BizException failure = failureOf(new ResetUserPasswordCommand(OPERATOR_ID, NEW_PASSWORD));

        // 409 段位而不是 40000：入参合法，被拒绝的是它造成的状态
        assertThat(failure.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(failure.getMessage()).isEqualTo("不能重置自己的密码，请通过「修改密码」并提供当前密码");
        // 本接口不验旧密码，若能作用于自己，本人改密那道校验就等于不存在
        verify(userQuery, never()).existsById(anyLong());
        verifyNoInteractions(passwordHasher, userAccountUpdater, sessionInvalidator);
    }

    @Test
    @DisplayName("密码不合规：一次返回全部违规项，不哈希、不写库")
    void shouldReturnAllPolicyViolations() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(true);

        BizException failure = failureOf(new ResetUserPasswordCommand(OTHER_USER_ID, "abc"));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage())
                .as("改一条错一条的提示等于让管理员反复试")
                .contains("密码长度需为", "密码必须包含大写字母", "密码必须包含数字");
        verify(passwordHasher, never()).hash(anyString());
        verifyNoInteractions(userAccountUpdater, sessionInvalidator);
    }

    @Test
    @DisplayName("用户不存在：404 拒绝，不查策略也不写入")
    void shouldRejectMissingUser() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(false);

        BizException failure = failureOf(new ResetUserPasswordCommand(OTHER_USER_ID, NEW_PASSWORD));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(failure.getMessage()).isEqualTo("用户不存在");
        verifyNoInteractions(passwordHasher, userAccountUpdater, sessionInvalidator);
    }

    @Test
    @DisplayName("当前会话无效：40100，不查库也不写入")
    void shouldRejectMissingSession() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.empty());

        BizException failure = failureOf(new ResetUserPasswordCommand(OTHER_USER_ID, NEW_PASSWORD));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(userQuery, never()).existsById(anyLong());
        verifyNoInteractions(passwordHasher, userAccountUpdater, sessionInvalidator);
    }

    private BizException failureOf(ResetUserPasswordCommand command) {
        try {
            service.reset(command);
        } catch (BizException e) {
            return e;
        }
        throw new AssertionError("期望请求被拒绝，但通过了");
    }
}

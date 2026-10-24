package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.ChangePasswordCommand;
import com.yunwms.auth.domain.model.PasswordRuleViolation;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserAccount;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.PasswordHasher;
import com.yunwms.auth.domain.port.UserAccountRepository;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 本人改密用例的单测。
 *
 * <p>三个端口全部替换为 mock，不起 Spring 上下文、不连数据库。这里能钉住的是「判定顺序」与
 * 「哪一步不该发生」：旧密码错时一次写都不能发生，新密码不合规时连哈希都不该生成——
 * 这两类错误一旦漏到库里，用户就会带着一个自己没打算用的密码继续用系统。
 */
@DisplayName("本人改密")
class PasswordChangeServiceTest {

    private static final long USER_ID = 1001L;
    private static final String OLD_PASSWORD = "Init@1234";
    private static final String NEW_PASSWORD = "Changed@5678";
    private static final String OLD_HASH = "$2a$10$0123456789012345678901234567890123456789012345678901";
    private static final String NEW_HASH = "$2a$10$9876543210987654321098765432109876543210987654321098";

    private UserAccountRepository userAccountRepository;
    private PasswordHasher passwordHasher;
    private CurrentSession currentSession;
    private PasswordChangeService passwordChangeService;

    @BeforeEach
    void setUp() {
        userAccountRepository = mock(UserAccountRepository.class);
        passwordHasher = mock(PasswordHasher.class);
        currentSession = mock(CurrentSession.class);
        passwordChangeService = new PasswordChangeService(userAccountRepository, passwordHasher, currentSession);
        given(currentSession.currentUserId()).willReturn(OptionalLong.of(USER_ID));
    }

    @Test
    @DisplayName("改密成功：写入新密码哈希，并在落库之后清除改密标记")
    void shouldUpdateHashThenClearMark() {
        given(userAccountRepository.findById(USER_ID)).willReturn(Optional.of(pendingAccount()));
        given(passwordHasher.matches(OLD_PASSWORD, OLD_HASH)).willReturn(true);
        given(passwordHasher.hash(NEW_PASSWORD)).willReturn(NEW_HASH);

        passwordChangeService.change(new ChangePasswordCommand(OLD_PASSWORD, NEW_PASSWORD));

        verify(userAccountRepository).updatePassword(eq(USER_ID), eq(NEW_HASH), any(Instant.class));
        // 先落库再清标记：顺序反过来会留下「会话已放行、库里仍是初始密码」的状态
        InOrder inOrder = inOrder(userAccountRepository, currentSession);
        inOrder.verify(userAccountRepository).updatePassword(eq(USER_ID), eq(NEW_HASH), any(Instant.class));
        inOrder.verify(currentSession).clearMustChangePassword();
    }

    @Test
    @DisplayName("旧密码错：拒绝改密，且一次写都不发生")
    void shouldRejectWhenOldPasswordIsWrong() {
        given(userAccountRepository.findById(USER_ID)).willReturn(Optional.of(pendingAccount()));
        given(passwordHasher.matches(anyString(), anyString())).willReturn(false);

        BizException failure = failureOf(new ChangePasswordCommand("Wrong@1234", NEW_PASSWORD));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
        assertThat(failure.getMessage()).isEqualTo("当前密码不正确");
        verify(userAccountRepository, never()).updatePassword(anyLong(), anyString(), any(Instant.class));
        verify(currentSession, never()).clearMustChangePassword();
        // 连哈希都不生成：旧密码没验过，新密码就不该被加工，更不该有机会落库
        verify(passwordHasher, never()).hash(anyString());
    }

    @Test
    @DisplayName("新密码不合规：返回全部违规项，且不改哈希")
    void shouldReturnAllViolationsAndKeepHash() {
        given(userAccountRepository.findById(USER_ID)).willReturn(Optional.of(pendingAccount()));
        given(passwordHasher.matches(OLD_PASSWORD, OLD_HASH)).willReturn(true);

        BizException failure = failureOf(new ChangePasswordCommand(OLD_PASSWORD, "abc"));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        // 一次说清全部违规项，而不是改一条错一条
        assertThat(failure.getMessage()).contains(
                PasswordRuleViolation.LENGTH.message(),
                PasswordRuleViolation.UPPERCASE.message(),
                PasswordRuleViolation.DIGIT.message());
        verify(passwordHasher, never()).hash(anyString());
        verify(userAccountRepository, never()).updatePassword(anyLong(), anyString(), any(Instant.class));
        verify(currentSession, never()).clearMustChangePassword();
    }

    @Test
    @DisplayName("新旧密码相同：拒绝，且不重复写同一个密码")
    void shouldRejectWhenNewPasswordEqualsOld() {
        given(userAccountRepository.findById(USER_ID)).willReturn(Optional.of(pendingAccount()));
        given(passwordHasher.matches(NEW_PASSWORD, OLD_HASH)).willReturn(true);

        BizException failure = failureOf(new ChangePasswordCommand(NEW_PASSWORD, NEW_PASSWORD));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("新密码不能与当前密码相同");
        // 两次校验都拿明文比旧哈希（先验旧密码、再判新旧是否相同）：BCrypt 加盐，哈希串永不相等，
        // 若改成比哈希串，「新旧相同」这条永远判不出来
        verify(passwordHasher, times(2)).matches(NEW_PASSWORD, OLD_HASH);
        verify(userAccountRepository, never()).updatePassword(anyLong(), anyString(), any(Instant.class));
        verify(currentSession, never()).clearMustChangePassword();
    }

    @Test
    @DisplayName("会话已失效：按未登录拒绝，不读账号")
    void shouldRejectWhenSessionHasNoUser() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.empty());

        BizException failure = failureOf(new ChangePasswordCommand(OLD_PASSWORD, NEW_PASSWORD));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(userAccountRepository);
    }

    @Test
    @DisplayName("账号已被删除：按未登录拒绝，不写密码")
    void shouldRejectWhenAccountDisappeared() {
        given(userAccountRepository.findById(USER_ID)).willReturn(Optional.empty());

        BizException failure = failureOf(new ChangePasswordCommand(OLD_PASSWORD, NEW_PASSWORD));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(userAccountRepository, never()).updatePassword(anyLong(), anyString(), any(Instant.class));
        verifyNoInteractions(passwordHasher);
    }

    /** 初始密码登录后的账号：库内标记为需先改密。 */
    private UserAccount pendingAccount() {
        return new UserAccount(USER_ID, "admin", "张三", SubjectType.STAFF, null,
                OLD_HASH, true, UserStatus.ACTIVE, null);
    }

    /** 断言改密被拒绝并取回异常；意外成功时直接让测试失败，避免它被当成「没抛异常」的空白通过。 */
    private BizException failureOf(ChangePasswordCommand command) {
        try {
            passwordChangeService.change(command);
        } catch (BizException e) {
            return e;
        }
        throw new AssertionError("期望改密被拒绝，但改密成功了");
    }
}

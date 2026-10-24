package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.PasswordLoginCommand;
import com.yunwms.auth.application.dto.AuthenticatedSubject;
import com.yunwms.auth.domain.model.FailedAttemptState;
import com.yunwms.auth.domain.model.SessionOrigin;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserAccount;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.domain.port.PasswordHasher;
import com.yunwms.auth.domain.port.SessionTokenIssuer;
import com.yunwms.auth.domain.port.UserAccountRepository;
import com.yunwms.auth.domain.port.UserRoleQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
 * 登录用例的单测。
 *
 * <p>四个端口全部替换为 mock，不起 Spring 上下文、不连数据库：登录规则的错误应该由这里发现，
 * 而不是等部署到有库的环境才暴露。
 *
 * <p>失败计数与锁定时间的落库由 {@code UserAccountRepository} 的实现负责（XML 里的
 * {@code UPDATE ... RETURNING}），这里断言的是「什么时候调、传了什么阈值与截止时间」；
 * 语句本身的正确性由对着真实数据库的手工验证覆盖。
 */
@DisplayName("登录名加密码登录")
class PasswordLoginServiceTest {

    private static final long USER_ID = 1001L;
    private static final String LOGIN_NAME = "admin";
    private static final String RAW_PASSWORD = "Admin@1234";
    private static final String WRONG_PASSWORD = "Wrong@1234";
    private static final String PASSWORD_HASH = "$2a$10$0123456789012345678901234567890123456789012345678901";
    private static final String TOKEN = "b0e1c2d3-token";

    /** 规格「连续 5 次密码错误后锁定」。 */
    private static final int LOCK_THRESHOLD = 5;

    private UserAccountRepository userAccountRepository;
    private UserRoleQuery userRoleQuery;
    private PasswordHasher passwordHasher;
    private SessionTokenIssuer sessionTokenIssuer;
    private PasswordLoginService passwordLoginService;

    @BeforeEach
    void setUp() {
        userAccountRepository = mock(UserAccountRepository.class);
        userRoleQuery = mock(UserRoleQuery.class);
        passwordHasher = mock(PasswordHasher.class);
        sessionTokenIssuer = mock(SessionTokenIssuer.class);
        passwordLoginService = new PasswordLoginService(
                userAccountRepository, userRoleQuery, passwordHasher, sessionTokenIssuer);
    }

    @Test
    @DisplayName("登录成功：返回令牌与主体信息，记录登录时间并结束本轮失败计数")
    void shouldIssueTokenWhenCredentialsAreValid() {
        UserAccount account = account(UserStatus.ACTIVE, null);
        given(userAccountRepository.findByLoginName(LOGIN_NAME)).willReturn(Optional.of(account));
        given(passwordHasher.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);
        given(userRoleQuery.findActiveRoleCodesByUserId(USER_ID)).willReturn(List.of("CS_STAFF"));
        given(sessionTokenIssuer.issue(any(UserAccount.class), any(SessionOrigin.class))).willReturn(TOKEN);

        AuthenticatedSubject subject = passwordLoginService.login(command(LOGIN_NAME, RAW_PASSWORD));

        assertThat(subject.token()).isEqualTo(TOKEN);
        assertThat(subject.subjectType()).isEqualTo(SubjectType.STAFF);
        assertThat(subject.ownerId()).isNull();
        assertThat(subject.displayName()).isEqualTo("张三");
        assertThat(subject.roles()).containsExactly("CS_STAFF");
        // 成功登录同时也是「连续失败」这一轮的终点，计数在这一步归零
        verify(userAccountRepository).recordSuccessfulLogin(eq(USER_ID), any(Instant.class));
        verify(sessionTokenIssuer).issue(eq(account), any(SessionOrigin.class));
    }

    @Test
    @DisplayName("登录名大小写与两侧空格不影响匹配")
    void shouldNormalizeLoginNameBeforeLookup() {
        given(userAccountRepository.findByLoginName(LOGIN_NAME)).willReturn(Optional.of(account(UserStatus.ACTIVE, null)));
        given(passwordHasher.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);
        given(userRoleQuery.findActiveRoleCodesByUserId(USER_ID)).willReturn(List.of());
        given(sessionTokenIssuer.issue(any(UserAccount.class), any(SessionOrigin.class))).willReturn(TOKEN);

        AuthenticatedSubject subject = passwordLoginService.login(command("  " + LOGIN_NAME + "  ", RAW_PASSWORD));

        assertThat(subject.roles()).isEmpty();
        verify(userAccountRepository).findByLoginName(LOGIN_NAME);
    }

    @Test
    @DisplayName("密码错误：拒绝登录，计入一次失败，且提示不暴露账号是否存在")
    void shouldRejectWrongPasswordWithoutRevealingAccountExistence() {
        given(userAccountRepository.findByLoginName(LOGIN_NAME)).willReturn(Optional.of(account(UserStatus.ACTIVE, null)));
        given(passwordHasher.matches(anyString(), anyString())).willReturn(false);
        given(userAccountRepository.recordFailedAttempt(eq(USER_ID), anyInt(), any(Instant.class)))
                .willReturn(new FailedAttemptState(1, null));

        BizException failure = failureOf(command(LOGIN_NAME, WRONG_PASSWORD));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
        assertThat(failure.getMessage())
                .isEqualTo(ErrorCode.LOGIN_FAILED.defaultMessage())
                .doesNotContain("不存在", "未注册", "用户名", "手机号");
        verify(userAccountRepository).recordFailedAttempt(eq(USER_ID), eq(LOCK_THRESHOLD), any(Instant.class));
        verifyNoInteractions(sessionTokenIssuer);
        verify(userAccountRepository, never()).recordSuccessfulLogin(anyLong(), any(Instant.class));
    }

    @Test
    @DisplayName("登录名不存在：与密码错误返回完全相同的错误码与文案，且不产生失败计数")
    void shouldFailIdenticallyWhenLoginNameDoesNotExist() {
        given(userAccountRepository.findByLoginName("someone-else")).willReturn(Optional.empty());

        BizException unknownLoginName = failureOf(command("someone-else", RAW_PASSWORD));

        assertThat(unknownLoginName.errorCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
        assertThat(unknownLoginName.getMessage()).isEqualTo(ErrorCode.LOGIN_FAILED.defaultMessage());
        verify(userAccountRepository, never()).recordFailedAttempt(anyLong(), anyInt(), any(Instant.class));
        verifyNoInteractions(sessionTokenIssuer);
    }

    @Test
    @DisplayName("主体停用：拒绝登录并提示账号已停用")
    void shouldRejectDisabledAccount() {
        given(userAccountRepository.findByLoginName(LOGIN_NAME)).willReturn(Optional.of(account(UserStatus.DISABLED, null)));
        given(passwordHasher.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);

        BizException failure = failureOf(command(LOGIN_NAME, RAW_PASSWORD));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.LOGIN_ACCOUNT_DISABLED);
        assertThat(failure.getMessage()).isEqualTo("账号已停用");
        verifyNoInteractions(sessionTokenIssuer);
        verify(userAccountRepository, never()).recordSuccessfulLogin(anyLong(), any(Instant.class));
    }

    @Test
    @DisplayName("锁定期内即使密码正确也拒绝登录，且不改动锁定状态")
    void shouldRejectLockedAccountEvenWithCorrectPassword() {
        given(userAccountRepository.findByLoginName(LOGIN_NAME))
                .willReturn(Optional.of(account(UserStatus.ACTIVE, Instant.now().plus(10, ChronoUnit.MINUTES))));
        given(passwordHasher.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);

        BizException failure = failureOf(command(LOGIN_NAME, RAW_PASSWORD));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.LOGIN_ACCOUNT_LOCKED);
        verifyNoInteractions(sessionTokenIssuer);
        // 锁定仍在有效期内：既不能清零，也不能重新计时
        verify(userAccountRepository, never()).clearExpiredLock(anyLong(), any(Instant.class));
        verify(userAccountRepository, never()).recordFailedAttempt(anyLong(), anyInt(), any(Instant.class));
    }

    @Test
    @DisplayName("锁定已过期：先归零再校验密码，正确密码可以登录")
    void shouldClearExpiredLockBeforeVerifyingPassword() {
        given(userAccountRepository.findByLoginName(LOGIN_NAME))
                .willReturn(Optional.of(account(UserStatus.ACTIVE, Instant.now().minus(1, ChronoUnit.MINUTES))));
        given(passwordHasher.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);
        given(userRoleQuery.findActiveRoleCodesByUserId(USER_ID)).willReturn(List.of());
        given(sessionTokenIssuer.issue(any(UserAccount.class), any(SessionOrigin.class))).willReturn(TOKEN);

        AuthenticatedSubject subject = passwordLoginService.login(command(LOGIN_NAME, RAW_PASSWORD));

        assertThat(subject.token()).isEqualTo(TOKEN);
        InOrder inOrder = inOrder(userAccountRepository, passwordHasher);
        inOrder.verify(userAccountRepository).clearExpiredLock(eq(USER_ID), any(Instant.class));
        inOrder.verify(passwordHasher).matches(RAW_PASSWORD, PASSWORD_HASH);
        verify(userAccountRepository).recordSuccessfulLogin(eq(USER_ID), any(Instant.class));
    }

    @Test
    @DisplayName("连续 5 次密码错误：第 5 次写入锁定截止时间，之后即使密码正确也拒绝登录")
    void shouldLockAccountAfterFiveConsecutiveWrongPasswords() {
        Instant lockedUntil = Instant.now().plus(30, ChronoUnit.MINUTES);
        UserAccount unlocked = account(UserStatus.ACTIVE, null);
        UserAccount locked = account(UserStatus.ACTIVE, lockedUntil);
        given(userAccountRepository.findByLoginName(LOGIN_NAME))
                .willReturn(Optional.of(unlocked), Optional.of(unlocked), Optional.of(unlocked),
                        Optional.of(unlocked), Optional.of(unlocked), Optional.of(locked));
        given(passwordHasher.matches(WRONG_PASSWORD, PASSWORD_HASH)).willReturn(false);
        given(passwordHasher.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);
        given(userAccountRepository.recordFailedAttempt(eq(USER_ID), eq(LOCK_THRESHOLD), any(Instant.class)))
                .willReturn(new FailedAttemptState(1, null),
                        new FailedAttemptState(2, null),
                        new FailedAttemptState(3, null),
                        new FailedAttemptState(4, null),
                        new FailedAttemptState(5, lockedUntil));

        for (int attempt = 1; attempt <= LOCK_THRESHOLD; attempt++) {
            BizException failure = failureOf(command(LOGIN_NAME, WRONG_PASSWORD));
            assertThat(failure.errorCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
        }

        // 第 6 次：密码正确，但账号已在锁定期内
        BizException rejected = failureOf(command(LOGIN_NAME, RAW_PASSWORD));
        assertThat(rejected.errorCode()).isEqualTo(ErrorCode.LOGIN_ACCOUNT_LOCKED);

        ArgumentCaptor<Instant> lockUntilCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(userAccountRepository, times(LOCK_THRESHOLD))
                .recordFailedAttempt(eq(USER_ID), eq(LOCK_THRESHOLD), lockUntilCaptor.capture());
        // 第 5 次写入的截止时间是当前时间 +30 分钟，且五次都按同一个阈值累加
        assertThat(lockUntilCaptor.getValue()).isBetween(
                Instant.now().plus(29, ChronoUnit.MINUTES),
                Instant.now().plus(31, ChronoUnit.MINUTES));
        verify(userAccountRepository, never()).recordSuccessfulLogin(anyLong(), any(Instant.class));
        verifyNoInteractions(sessionTokenIssuer);
    }

    @Test
    @DisplayName("锁定期内提交错误密码：只返回凭据错误，不累加次数，不延长锁定")
    void shouldNotExtendLockWhenPasswordIsWrongDuringLock() {
        given(userAccountRepository.findByLoginName(LOGIN_NAME))
                .willReturn(Optional.of(account(UserStatus.ACTIVE, Instant.now().plus(20, ChronoUnit.MINUTES))));
        given(passwordHasher.matches(anyString(), anyString())).willReturn(false);

        BizException failure = failureOf(command(LOGIN_NAME, WRONG_PASSWORD));

        // 锁定同样是账号状态信息，只能给到拿到正确密码的人
        assertThat(failure.errorCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
        verify(userAccountRepository, never()).recordFailedAttempt(anyLong(), anyInt(), any(Instant.class));
        verify(userAccountRepository, never()).clearExpiredLock(anyLong(), any(Instant.class));
        verifyNoInteractions(sessionTokenIssuer);
    }

    @Test
    @DisplayName("密码错 4 次后成功登录：计数归零，本轮不再触发锁定")
    void shouldResetFailedAttemptsAfterSuccessfulLogin() {
        given(userAccountRepository.findByLoginName(LOGIN_NAME)).willReturn(Optional.of(account(UserStatus.ACTIVE, null)));
        given(passwordHasher.matches(WRONG_PASSWORD, PASSWORD_HASH)).willReturn(false);
        given(passwordHasher.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);
        given(userAccountRepository.recordFailedAttempt(eq(USER_ID), eq(LOCK_THRESHOLD), any(Instant.class)))
                .willReturn(new FailedAttemptState(1, null),
                        new FailedAttemptState(2, null),
                        new FailedAttemptState(3, null),
                        new FailedAttemptState(4, null));
        given(userRoleQuery.findActiveRoleCodesByUserId(USER_ID)).willReturn(List.of());
        given(sessionTokenIssuer.issue(any(UserAccount.class), any(SessionOrigin.class))).willReturn(TOKEN);

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThat(failureOf(command(LOGIN_NAME, WRONG_PASSWORD)).errorCode())
                    .isEqualTo(ErrorCode.LOGIN_FAILED);
        }

        AuthenticatedSubject subject = passwordLoginService.login(command(LOGIN_NAME, RAW_PASSWORD));

        assertThat(subject.token()).isEqualTo(TOKEN);
        verify(userAccountRepository, times(4)).recordFailedAttempt(eq(USER_ID), eq(LOCK_THRESHOLD), any(Instant.class));
        // 归零由这一条语句完成：连续失败随本次成功登录结束
        verify(userAccountRepository).recordSuccessfulLogin(eq(USER_ID), any(Instant.class));
    }

    private UserAccount account(UserStatus status, Instant lockedUntil) {
        return new UserAccount(USER_ID, LOGIN_NAME, "张三", SubjectType.STAFF, null,
                PASSWORD_HASH, status, lockedUntil);
    }

    private PasswordLoginCommand command(String loginName, String password) {
        return new PasswordLoginCommand(loginName, password, "10.0.0.8", "JUnit");
    }

    /** 断言登录被拒绝并取回异常；登录意外成功时直接让测试失败，避免把它当成「没抛异常」的空白通过。 */
    private BizException failureOf(PasswordLoginCommand command) {
        try {
            passwordLoginService.login(command);
        } catch (BizException e) {
            return e;
        }
        throw new AssertionError("期望登录被拒绝，但登录成功了");
    }
}

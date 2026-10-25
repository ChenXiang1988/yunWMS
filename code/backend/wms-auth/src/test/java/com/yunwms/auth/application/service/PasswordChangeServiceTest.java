package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.ChangePasswordCommand;
import com.yunwms.auth.domain.model.FailedAttemptState;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.OptionalLong;

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
 * 本人改密用例的单测。
 *
 * <p>端口替换为 mock（失败计数记录器用真实实现，它背后的仓储是 mock），不起 Spring 上下文、
 * 不连数据库。这里能钉住的是「判定顺序」与「哪一步不该发生」：旧密码错时哈希与密码都不该动，
 * 新密码不合规时连哈希都不该生成，账号处于锁定期时连密码都不该验——
 * 这几类错误一旦漏过去，用户就会带着一个自己没打算用的密码继续用系统，或者绕过锁定继续试密码。
 */
@DisplayName("本人改密")
class PasswordChangeServiceTest {

    private static final long USER_ID = 1001L;
    private static final String OLD_PASSWORD = "Init@1234";
    private static final String NEW_PASSWORD = "Changed@5678";
    private static final String WRONG_PASSWORD = "Wrong@1234";
    private static final String OLD_HASH = "$2a$10$0123456789012345678901234567890123456789012345678901";
    private static final String NEW_HASH = "$2a$10$9876543210987654321098765432109876543210987654321098";

    /** 规格「连续 5 次密码错误后锁定」；这里写死而不引用常量，才能在提取后仍然拦住「顺手改数」。 */
    private static final int LOCK_THRESHOLD = 5;

    private UserAccountRepository userAccountRepository;
    private PasswordHasher passwordHasher;
    private CurrentSession currentSession;
    private PasswordChangeService passwordChangeService;

    @BeforeEach
    void setUp() {
        userAccountRepository = mock(UserAccountRepository.class);
        passwordHasher = mock(PasswordHasher.class);
        currentSession = mock(CurrentSession.class);
        // 失败计数用真实实现包住同一个仓储 mock：只有这样才能断言真正传给库的阈值与窗口，
        // 把记录器换成 mock 就只能断言「调了它」，而「参数与登录一致」正是本任务要钉住的东西
        passwordChangeService = new PasswordChangeService(userAccountRepository, passwordHasher, currentSession,
                new FailedAttemptRecorder(userAccountRepository));
        given(currentSession.currentUserId()).willReturn(OptionalLong.of(USER_ID));
    }

    @Test
    @DisplayName("改密成功：写入新密码哈希，落库之后清改密标记与失败状态")
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
        inOrder.verify(userAccountRepository).clearFailureState(USER_ID);
        // 改密不是登录：清失败状态不能顺手写登录时间，否则审计里的「上次登录」会变成「上次改密」
        verify(userAccountRepository, never()).recordSuccessfulLogin(anyLong(), any(Instant.class));
    }

    @Test
    @DisplayName("旧密码错：拒绝改密，计入一次失败，且不改哈希、不落库")
    void shouldRejectWhenOldPasswordIsWrong() {
        given(userAccountRepository.findById(USER_ID)).willReturn(Optional.of(pendingAccount()));
        given(passwordHasher.matches(anyString(), anyString())).willReturn(false);
        given(userAccountRepository.recordFailedAttempt(eq(USER_ID), anyInt(), any(Instant.class), any(Instant.class), any(Instant.class)))
                .willReturn(new FailedAttemptState(1, null));

        BizException failure = failureOf(new ChangePasswordCommand(WRONG_PASSWORD, NEW_PASSWORD));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
        assertThat(failure.getMessage()).isEqualTo("当前密码不正确");
        // 旧密码错就是一次密码失败，与登录走同一套计数：不计数的话这个接口就是穷举密码的入口
        verify(userAccountRepository).recordFailedAttempt(eq(USER_ID), eq(LOCK_THRESHOLD), any(Instant.class), any(Instant.class), any(Instant.class));
        verify(userAccountRepository, never()).updatePassword(anyLong(), anyString(), any(Instant.class));
        verify(currentSession, never()).clearMustChangePassword();
        // 失败不是这一轮的终点，计数不能被清掉
        verify(userAccountRepository, never()).clearFailureState(anyLong());
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

    @Test
    @DisplayName("锁定期内调改密：直接拒绝，不验密码、不累加次数")
    void shouldRejectWhenAccountIsLocked() {
        given(userAccountRepository.findById(USER_ID)).willReturn(Optional.of(lockedAccount()));

        BizException failure = failureOf(new ChangePasswordCommand(OLD_PASSWORD, NEW_PASSWORD));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.LOGIN_ACCOUNT_LOCKED);
        // 锁定期内连「密码对不对」都不该被探出来：不验密码，这条路就彻底堵死
        verifyNoInteractions(passwordHasher);
        // 这一轮已经在计数，再累加只会把锁定无限延长
        verify(userAccountRepository, never())
                .recordFailedAttempt(anyLong(), anyInt(), any(Instant.class), any(Instant.class), any(Instant.class));
        verify(userAccountRepository, never()).updatePassword(anyLong(), anyString(), any(Instant.class));
        verify(currentSession, never()).clearMustChangePassword();
    }

    @Test
    @DisplayName("旧密码连错 5 次：每次都计入失败，之后的正确旧密码也被锁定挡下")
    void shouldLockAccountAfterFiveWrongOldPasswords() {
        Instant lockedUntil = Instant.now().plus(30, ChronoUnit.MINUTES);
        UserAccount unlocked = pendingAccount();
        UserAccount locked = new UserAccount(USER_ID, "admin", "张三", SubjectType.STAFF, null,
                OLD_HASH, true, UserStatus.ACTIVE, lockedUntil);
        given(userAccountRepository.findById(USER_ID))
                .willReturn(Optional.of(unlocked), Optional.of(unlocked), Optional.of(unlocked),
                        Optional.of(unlocked), Optional.of(unlocked), Optional.of(locked));
        given(passwordHasher.matches(anyString(), eq(OLD_HASH))).willReturn(false);
        given(passwordHasher.matches(OLD_PASSWORD, OLD_HASH)).willReturn(true);
        given(userAccountRepository.recordFailedAttempt(eq(USER_ID), eq(LOCK_THRESHOLD), any(Instant.class), any(Instant.class), any(Instant.class)))
                .willReturn(new FailedAttemptState(1, null),
                        new FailedAttemptState(2, null),
                        new FailedAttemptState(3, null),
                        new FailedAttemptState(4, null),
                        new FailedAttemptState(5, lockedUntil));

        for (int attempt = 1; attempt <= LOCK_THRESHOLD; attempt++) {
            assertThat(failureOf(new ChangePasswordCommand(WRONG_PASSWORD, NEW_PASSWORD)).errorCode())
                    .isEqualTo(ErrorCode.LOGIN_FAILED);
        }

        // 第 6 次：旧密码正确，但账号已在锁定期内
        assertThat(failureOf(new ChangePasswordCommand(OLD_PASSWORD, NEW_PASSWORD)).errorCode())
                .isEqualTo(ErrorCode.LOGIN_ACCOUNT_LOCKED);

        ArgumentCaptor<Instant> windowStartCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> failedAtCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> lockUntilCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(userAccountRepository, times(LOCK_THRESHOLD))
                .recordFailedAttempt(eq(USER_ID), eq(LOCK_THRESHOLD),
                        windowStartCaptor.capture(), failedAtCaptor.capture(), lockUntilCaptor.capture());
        // 与登录同一套参数：窗口起点在失败时刻之前 30 分钟，锁定截止时间在失败时刻之后 30 分钟
        assertThat(Duration.between(windowStartCaptor.getValue(), failedAtCaptor.getValue()))
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(Duration.between(failedAtCaptor.getValue(), lockUntilCaptor.getValue()))
                .isEqualTo(Duration.ofMinutes(30));
        // 第 6 次没有走到密码校验：锁定期内再多的尝试都不该被受理
        verify(passwordHasher, times(LOCK_THRESHOLD)).matches(anyString(), anyString());
        verify(userAccountRepository, never()).updatePassword(anyLong(), anyString(), any(Instant.class));
        verify(userAccountRepository, never()).clearFailureState(anyLong());
    }

    /** 初始密码登录后的账号：库内标记为需先改密。 */
    private UserAccount pendingAccount() {
        return new UserAccount(USER_ID, "admin", "张三", SubjectType.STAFF, null,
                OLD_HASH, true, UserStatus.ACTIVE, null);
    }

    /** 已触发连续失败锁定的账号：登录与改密都必须拒绝。 */
    private UserAccount lockedAccount() {
        return new UserAccount(USER_ID, "admin", "张三", SubjectType.STAFF, null,
                OLD_HASH, true, UserStatus.ACTIVE, Instant.now().plus(20, ChronoUnit.MINUTES));
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

package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.CreateUserCommand;
import com.yunwms.auth.application.dto.CreatedUser;
import com.yunwms.auth.domain.model.NewUserAccount;
import com.yunwms.auth.domain.model.PasswordRuleViolation;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.PasswordHasher;
import com.yunwms.auth.domain.port.UserAccountRegistrar;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.List;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 建号用例的单测。
 *
 * <p>端口替换为 mock，不起 Spring、不连库。这里能钉住的是「判定顺序」与「哪一步不该发生」：
 * 主体与货主不匹配时不该生成哈希，密码不合规时不该哈希、更不该落库，
 * 唯一冲突必须转成前端能在字段上展示的错误码，而不是 50000。
 *
 * <p>唯一性不在本用例的判定范围内（服务层「先查再插」在并发下无效），因此这里验证的是
 * 「数据库抛出冲突之后如何翻译」，而不是「建号前是否查过重名」。
 */
@DisplayName("管理员建号")
class UserCreationServiceTest {

    private static final long OPERATOR_ID = 9L;
    private static final long NEW_USER_ID = 12L;
    private static final String LOGIN_NAME = "zhangsan";
    private static final String INITIAL_PASSWORD = "Init@1234";
    private static final String PASSWORD_HASH = "$2a$10$0123456789012345678901234567890123456789012345678901";

    private UserAccountRegistrar userAccountRegistrar;
    private PasswordHasher passwordHasher;
    private CurrentSession currentSession;
    private UserCreationService userCreationService;

    @BeforeEach
    void setUp() {
        userAccountRegistrar = mock(UserAccountRegistrar.class);
        passwordHasher = mock(PasswordHasher.class);
        currentSession = mock(CurrentSession.class);
        userCreationService = new UserCreationService(userAccountRegistrar, passwordHasher, currentSession);
        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
    }

    @Test
    @DisplayName("建员工成功：落库的是哈希、角色与操作人，响应回标识与登录名")
    void shouldRegisterStaffWithHashAndOperator() {
        given(passwordHasher.hash(INITIAL_PASSWORD)).willReturn(PASSWORD_HASH);
        given(userAccountRegistrar.register(any(NewUserAccount.class), anyList(), anyLong()))
                .willReturn(NEW_USER_ID);

        CreatedUser created = userCreationService.create(staffCommand("z@example.com"));

        assertThat(created.id()).isEqualTo(NEW_USER_ID);
        assertThat(created.loginName()).isEqualTo(LOGIN_NAME);

        ArgumentCaptor<NewUserAccount> accountCaptor = ArgumentCaptor.forClass(NewUserAccount.class);
        verify(userAccountRegistrar).register(accountCaptor.capture(), eq(List.of()), eq(OPERATOR_ID));
        NewUserAccount account = accountCaptor.getValue();
        // 跨层传递的必须是哈希：明文一旦进入落库对象，日志与异常里随时可能出现它
        assertThat(account.passwordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(account.passwordHash()).isNotEqualTo(INITIAL_PASSWORD);
        assertThat(account.subjectType()).isEqualTo(SubjectType.STAFF);
        assertThat(account.ownerId()).isNull();
        assertThat(account.email()).isEqualTo("z@example.com");
    }

    @Test
    @DisplayName("建员工成功：登录名两侧空白与角色标识都按落库口径处理")
    void shouldTrimLoginNameAndPassRoleIds() {
        CreateUserCommand command = new CreateUserCommand("  zhangsan  ", " 张三 ", SubjectType.STAFF, null,
                "  z@example.com  ", "  ", INITIAL_PASSWORD, List.of(3L, 3L));
        given(passwordHasher.hash(INITIAL_PASSWORD)).willReturn(PASSWORD_HASH);
        given(userAccountRegistrar.register(any(NewUserAccount.class), anyList(), anyLong()))
                .willReturn(NEW_USER_ID);

        CreatedUser created = userCreationService.create(command);

        assertThat(created.loginName()).isEqualTo(LOGIN_NAME);
        ArgumentCaptor<NewUserAccount> accountCaptor = ArgumentCaptor.forClass(NewUserAccount.class);
        verify(userAccountRegistrar).register(accountCaptor.capture(), eq(List.of(3L, 3L)), eq(OPERATOR_ID));
        // 不裁空白会建出 " zhangsan" 这种「看起来一样」的第二个账号；空串必须落成 NULL，
        // 否则 email = '' 会绕过 email IS NOT NULL
        assertThat(accountCaptor.getValue().email()).isEqualTo("z@example.com");
        assertThat(accountCaptor.getValue().wecomUserId()).isNull();
    }

    @Test
    @DisplayName("员工绑定了货主：拒绝，不哈希、不落库")
    void shouldRejectStaffWithOwnerId() {
        CreateUserCommand command = new CreateUserCommand(LOGIN_NAME, "张三", SubjectType.STAFF, 7L,
                "z@example.com", null, INITIAL_PASSWORD, List.of());

        BizException failure = failureOf(command);

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("内部员工不得绑定所属货主");
        verify(passwordHasher, never()).hash(anyString());
        verifyNoInteractions(userAccountRegistrar);
    }

    @Test
    @DisplayName("货主用户缺 ownerId：拒绝，不哈希、不落库")
    void shouldRejectOwnerWithoutOwnerId() {
        CreateUserCommand command = new CreateUserCommand(LOGIN_NAME, "张三", SubjectType.OWNER, null,
                null, null, INITIAL_PASSWORD, List.of());

        BizException failure = failureOf(command);

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("货主用户必须指定所属货主");
        verify(passwordHasher, never()).hash(anyString());
        verifyNoInteractions(userAccountRegistrar);
    }

    @Test
    @DisplayName("员工缺邮箱：拒绝，即使邮箱是空串也按未填处理")
    void shouldRejectStaffWithoutEmail() {
        BizException missing = failureOf(staffCommand(null));
        assertThat(missing.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(missing.getMessage()).isEqualTo("内部员工必须填写邮箱");

        BizException blank = failureOf(staffCommand("   "));
        assertThat(blank.getMessage()).isEqualTo("内部员工必须填写邮箱");

        verify(passwordHasher, never()).hash(anyString());
        verifyNoInteractions(userAccountRegistrar);
    }

    @Test
    @DisplayName("货主用户可不填邮箱：邮箱只在员工主体上必填")
    void shouldAllowOwnerWithoutEmail() {
        CreateUserCommand command = new CreateUserCommand("owner01", "货主账号", SubjectType.OWNER, 7L,
                null, null, INITIAL_PASSWORD, null);
        given(passwordHasher.hash(INITIAL_PASSWORD)).willReturn(PASSWORD_HASH);
        given(userAccountRegistrar.register(any(NewUserAccount.class), anyList(), anyLong()))
                .willReturn(NEW_USER_ID);

        CreatedUser created = userCreationService.create(command);

        assertThat(created.id()).isEqualTo(NEW_USER_ID);
        // roleIds 为 null 表示不分配角色，不能变成一次「分配了 null 角色」的写入
        verify(userAccountRegistrar).register(any(NewUserAccount.class), eq(List.of()), eq(OPERATOR_ID));
    }

    @Test
    @DisplayName("初始密码不合规：一次返回全部违规项，不哈希、不落库")
    void shouldRejectWeakInitialPassword() {
        BizException failure = failureOf(staffCommandWithPassword("abc"));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).contains(
                PasswordRuleViolation.LENGTH.message(),
                PasswordRuleViolation.UPPERCASE.message(),
                PasswordRuleViolation.DIGIT.message());
        // 策略不通过就不该把密码交给哈希，更不该有机会落库
        verify(passwordHasher, never()).hash(anyString());
        verifyNoInteractions(userAccountRegistrar);
    }

    @Test
    @DisplayName("会话无效：按未登录拒绝，连校验都不做")
    void shouldRejectWhenSessionHasNoUser() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.empty());

        BizException failure = failureOf(staffCommand("z@example.com"));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(passwordHasher, userAccountRegistrar);
    }

    @Test
    @DisplayName("登录名唯一冲突：转成 40901，供前端在登录名字段就地提示")
    void shouldTranslateDuplicateLoginName() {
        given(passwordHasher.hash(INITIAL_PASSWORD)).willReturn(PASSWORD_HASH);
        given(userAccountRegistrar.register(any(NewUserAccount.class), anyList(), anyLong()))
                .willThrow(constraintViolation("ux_sys_user_login_name"));

        BizException failure = failureOf(staffCommand("z@example.com"));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.LOGIN_NAME_TAKEN);
        assertThat(failure.getMessage()).isEqualTo(ErrorCode.LOGIN_NAME_TAKEN.defaultMessage());
    }

    @Test
    @DisplayName("邮箱唯一冲突：转成 40000 并说明是邮箱冲突")
    void shouldTranslateDuplicateEmail() {
        given(passwordHasher.hash(INITIAL_PASSWORD)).willReturn(PASSWORD_HASH);
        given(userAccountRegistrar.register(any(NewUserAccount.class), anyList(), anyLong()))
                .willThrow(constraintViolation("ux_sys_user_email"));

        BizException failure = failureOf(staffCommand("z@example.com"));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("邮箱已被其他账号使用");
    }

    @Test
    @DisplayName("货主外键冲突：转成 40000 并说明货主不存在")
    void shouldTranslateMissingOwner() {
        given(passwordHasher.hash(INITIAL_PASSWORD)).willReturn(PASSWORD_HASH);
        given(userAccountRegistrar.register(any(NewUserAccount.class), anyList(), anyLong()))
                .willThrow(constraintViolation("fk_sys_user_owner"));

        BizException failure = failureOf(staffCommand("z@example.com"));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("指定的货主不存在");
    }

    @Test
    @DisplayName("角色外键冲突：转成 40000 并说明角色不存在")
    void shouldTranslateMissingRole() {
        given(passwordHasher.hash(INITIAL_PASSWORD)).willReturn(PASSWORD_HASH);
        given(userAccountRegistrar.register(any(NewUserAccount.class), anyList(), anyLong()))
                .willThrow(constraintViolation("sys_user_role_role_id_fkey"));

        BizException failure = failureOf(staffCommand("z@example.com"));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("指定的角色不存在");
    }

    @Test
    @DisplayName("不认识的约束：原样抛出，不猜错误码")
    void shouldNotGuessUnknownConstraint() {
        given(passwordHasher.hash(INITIAL_PASSWORD)).willReturn(PASSWORD_HASH);
        DataIntegrityViolationException unexpected = constraintViolation("ck_user_subject");
        given(userAccountRegistrar.register(any(NewUserAccount.class), anyList(), anyLong()))
                .willThrow(unexpected);

        // 猜一个 40000 返回给用户，等于把「我们不知道自己违反了哪条规则」伪装成一次正常拒绝；
        // 这类情况必须留下来变成 50000 并带堆栈，才能暴露本用例的假设已经失效
        assertThatThrownBy(() -> userCreationService.create(staffCommand("z@example.com")))
                .isSameAs(unexpected);
    }

    /** 一个内部员工的建号入参，只有邮箱按用例需要变化。 */
    private static CreateUserCommand staffCommand(String email) {
        return staffCommandWithPassword(INITIAL_PASSWORD, email);
    }

    private static CreateUserCommand staffCommandWithPassword(String initialPassword) {
        return staffCommandWithPassword(initialPassword, "z@example.com");
    }

    private static CreateUserCommand staffCommandWithPassword(String initialPassword, String email) {
        return new CreateUserCommand(LOGIN_NAME, "张三", SubjectType.STAFF, null,
                email, null, initialPassword, List.of());
    }

    /**
     * 构造一个带约束名的完整性冲突，形如 PostgreSQL 抛出的
     * {@code ERROR: duplicate key value violates unique constraint "ux_sys_user_login_name"}。
     *
     * @param constraintName 约束名
     * @return Spring 翻译后的异常，其根因消息里带约束名
     */
    private static DataIntegrityViolationException constraintViolation(String constraintName) {
        return new DataIntegrityViolationException("建号写入违反约束",
                new SQLException("ERROR: 违反约束 \"" + constraintName + "\""));
    }

    /** 断言建号被拒绝并取回异常；意外成功时直接让测试失败，避免它被当成「没抛异常」的空白通过。 */
    private BizException failureOf(CreateUserCommand command) {
        try {
            userCreationService.create(command);
        } catch (BizException e) {
            return e;
        }
        throw new AssertionError("期望建号被拒绝，但建号成功了");
    }
}

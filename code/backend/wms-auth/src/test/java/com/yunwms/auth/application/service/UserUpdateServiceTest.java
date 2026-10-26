package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.UpdateUserCommand;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.UserAccountUpdater;
import com.yunwms.auth.domain.port.UserQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 编辑用户用例的单测。
 *
 * <p>端口全部替换为 mock，不起 Spring、不连库。这里钉住三件事：三个字段的清洗口径
 * （空串一律落成 {@code null}，否则 {@code email = ''} 能绕过 {@code email IS NOT NULL}）、
 * 「邮箱必填与唯一交给数据库」之后两条约束各自翻译成哪一个码、以及校验不通过时一条写入都不发。
 * UPDATE 语句本身的效果属于仓储与集成测试。
 */
@DisplayName("编辑用户")
class UserUpdateServiceTest {

    private static final long OPERATOR_ID = 1L;
    private static final long OTHER_USER_ID = 9L;
    private static final String DISPLAY_NAME = "张三";
    private static final String EMAIL = "z@example.com";
    private static final String WECOM_USER_ID = "wx_zhangsan";

    private UserQuery userQuery;
    private UserAccountUpdater userAccountUpdater;
    private CurrentSession currentSession;
    private UserUpdateService service;

    @BeforeEach
    void setUp() {
        userQuery = mock(UserQuery.class);
        userAccountUpdater = mock(UserAccountUpdater.class);
        currentSession = mock(CurrentSession.class);
        service = new UserUpdateService(userQuery, userAccountUpdater, currentSession);

        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
    }

    @Test
    @DisplayName("编辑成功：三个字段与本页操作人一起交给写入端口")
    void shouldUpdateProfile() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(true);

        service.update(new UpdateUserCommand(OTHER_USER_ID, DISPLAY_NAME, EMAIL, WECOM_USER_ID));

        verify(userAccountUpdater).updateProfile(
                eq(OTHER_USER_ID), eq(DISPLAY_NAME), eq(EMAIL), eq(WECOM_USER_ID),
                eq(OPERATOR_ID), any(Instant.class));
    }

    @Test
    @DisplayName("两侧空白被裁掉：登录名与邮箱的唯一索引都在 lower() 上，带空格的两个值在库里是两个人")
    void shouldStripSurroundingWhitespace() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(true);

        service.update(new UpdateUserCommand(OTHER_USER_ID, " 张三 ", " z@example.com ", " wx_zhangsan "));

        verify(userAccountUpdater).updateProfile(
                eq(OTHER_USER_ID), eq(DISPLAY_NAME), eq(EMAIL), eq(WECOM_USER_ID),
                eq(OPERATOR_ID), any(Instant.class));
    }

    @Test
    @DisplayName("空串按未填处理：货主账号清空邮箱会落成 NULL，而不是一个能绕过非空检查的空串")
    void shouldTreatBlankAsNull() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(true);

        service.update(new UpdateUserCommand(OTHER_USER_ID, DISPLAY_NAME, "   ", ""));

        verify(userAccountUpdater).updateProfile(
                eq(OTHER_USER_ID), eq(DISPLAY_NAME), isNull(), isNull(),
                eq(OPERATOR_ID), any(Instant.class));
    }

    @Test
    @DisplayName("展示名为空白：按 40000 拒绝，一条写入都不发")
    void shouldRejectBlankDisplayName() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(true);

        BizException failure = failureOf(new UpdateUserCommand(OTHER_USER_ID, "   ", EMAIL, null));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("展示名不能为空");
        verifyNoInteractions(userAccountUpdater);
    }

    @Test
    @DisplayName("用户不存在：按 404 拒绝，不写入")
    void shouldRejectMissingUser() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(false);

        BizException failure = failureOf(new UpdateUserCommand(OTHER_USER_ID, DISPLAY_NAME, EMAIL, null));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(failure.getMessage()).isEqualTo("用户不存在");
        verifyNoInteractions(userAccountUpdater);
    }

    @Test
    @DisplayName("邮箱唯一冲突：转成 40903，供前端在邮箱字段就地提示")
    void shouldTranslateDuplicateEmail() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(true);
        willThrow(constraintViolation("ux_sys_user_email"))
                .given(userAccountUpdater).updateProfile(anyLong(), anyString(), any(), any(), anyLong(), any());

        BizException failure = failureOf(new UpdateUserCommand(OTHER_USER_ID, DISPLAY_NAME, EMAIL, null));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.EMAIL_TAKEN);
        assertThat(failure.getMessage()).isEqualTo("邮箱已被其他账号使用");
    }

    @Test
    @DisplayName("内部员工清空邮箱：由 ck_user_email_required 拦下并转成中文提示")
    void shouldTranslateMissingEmail() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(true);
        willThrow(constraintViolation("ck_user_email_required"))
                .given(userAccountUpdater).updateProfile(anyLong(), anyString(), any(), any(), anyLong(), any());

        BizException failure = failureOf(new UpdateUserCommand(OTHER_USER_ID, DISPLAY_NAME, null, null));

        // 与建号侧同一文案：同一条规则在两个入口不该有两种说法
        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("内部员工必须填写邮箱");
    }

    @Test
    @DisplayName("不认识的约束原样抛出：不能猜一个错误码把「不知道自己违反了哪条规则」伪装成正常拒绝")
    void shouldRethrowUnknownConstraint() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(true);
        willThrow(constraintViolation("ck_something_new"))
                .given(userAccountUpdater).updateProfile(anyLong(), anyString(), any(), any(), anyLong(), any());

        assertThatThrownBy(() -> service.update(
                new UpdateUserCommand(OTHER_USER_ID, DISPLAY_NAME, EMAIL, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("当前会话无效：40100，不查库也不写入")
    void shouldRejectMissingSession() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.empty());

        BizException failure = failureOf(new UpdateUserCommand(OTHER_USER_ID, DISPLAY_NAME, EMAIL, null));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(userQuery, never()).existsById(anyLong());
        verifyNoInteractions(userAccountUpdater);
    }

    /** 造一条带约束名的数据库异常：翻译逻辑按约束名匹配，异常文本必须与真实驱动一致。 */
    static DataIntegrityViolationException constraintViolation(String constraintName) {
        return new DataIntegrityViolationException(
                "could not execute statement; SQL [n/a]; constraint [" + constraintName + "]");
    }

    private BizException failureOf(UpdateUserCommand command) {
        try {
            service.update(command);
        } catch (BizException e) {
            return e;
        }
        throw new AssertionError("期望请求被拒绝，但通过了");
    }
}

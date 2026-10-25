package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.AssignRolesCommand;
import com.yunwms.auth.domain.model.RoleIdentity;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.RoleQuery;
import com.yunwms.auth.domain.port.UserQuery;
import com.yunwms.auth.domain.port.UserRoleRegistrar;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 分配角色用例的单测。
 *
 * <p>端口全部替换为 mock，不起 Spring、不连库。这里钉住用例自己的判定顺序与边界：
 * 替换语义下空集合合法、重复标识去重、角色不存在整单拒绝、不能把自己最后的内置角色换掉。
 * 「删掉旧关联、插入新关联」这两句 SQL 的效果属于仓储与集成测试。
 */
@DisplayName("分配角色")
class UserRoleAssignmentServiceTest {

    private static final long OPERATOR_ID = 1L;
    private static final long OTHER_USER_ID = 9L;
    private static final long BUILTIN_ROLE_ID = 100L;
    private static final long NORMAL_ROLE_ID = 200L;

    private UserQuery userQuery;
    private RoleQuery roleQuery;
    private UserRoleRegistrar userRoleRegistrar;
    private CurrentSession currentSession;
    private UserRoleAssignmentService service;

    @BeforeEach
    void setUp() {
        userQuery = mock(UserQuery.class);
        roleQuery = mock(RoleQuery.class);
        userRoleRegistrar = mock(UserRoleRegistrar.class);
        currentSession = mock(CurrentSession.class);
        service = new UserRoleAssignmentService(userQuery, roleQuery, userRoleRegistrar, currentSession);

        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
    }

    @Test
    @DisplayName("替换成功：把去重后的角色集合与本页操作人交给写入端口")
    void shouldReplaceRoles() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(true);
        given(roleQuery.findIdentities(any())).willReturn(List.of(
                new RoleIdentity(NORMAL_ROLE_ID, false)));

        service.assign(new AssignRolesCommand(OTHER_USER_ID, List.of(NORMAL_ROLE_ID)));

        verify(userRoleRegistrar).replaceRoles(OTHER_USER_ID, Set.of(NORMAL_ROLE_ID), OPERATOR_ID);
    }

    @Test
    @DisplayName("重复的角色标识只保留一个：集合语义，不该变成主键冲突")
    void shouldDeduplicateRoleIds() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(true);
        given(roleQuery.findIdentities(any())).willReturn(List.of(
                new RoleIdentity(NORMAL_ROLE_ID, false)));

        service.assign(new AssignRolesCommand(OTHER_USER_ID, List.of(NORMAL_ROLE_ID, NORMAL_ROLE_ID)));

        // 去重发生在查询之前：不去重时 findIdentities 会收到两个相同的 id，
        // 而 SQL 只会返回一行，于是一次合法的提交被判成「有角色不存在」
        verify(roleQuery).findIdentities(Set.of(NORMAL_ROLE_ID));
        verify(userRoleRegistrar).replaceRoles(OTHER_USER_ID, Set.of(NORMAL_ROLE_ID), OPERATOR_ID);
    }

    @Test
    @DisplayName("空数组移除全部角色：合法，且不去查角色表")
    void shouldAllowRemovingAllRoles() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(true);

        service.assign(new AssignRolesCommand(OTHER_USER_ID, List.of()));

        verifyNoInteractions(roleQuery);
        verify(userRoleRegistrar).replaceRoles(OTHER_USER_ID, Set.of(), OPERATOR_ID);
    }

    @Test
    @DisplayName("角色不存在整单拒绝：一条写入都不发")
    void shouldRejectUnknownRole() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(true);
        // 请求两个角色，库里只回一个：说明另一个不存在（或已被逻辑删除）
        given(roleQuery.findIdentities(any())).willReturn(List.of(
                new RoleIdentity(NORMAL_ROLE_ID, false)));

        BizException failure = failureOf(new AssignRolesCommand(
                OTHER_USER_ID, List.of(NORMAL_ROLE_ID, BUILTIN_ROLE_ID)));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("指定的角色不存在");
        verifyNoInteractions(userRoleRegistrar);
    }

    @Test
    @DisplayName("用户不存在：按 404 拒绝，不查角色也不写入")
    void shouldRejectMissingUser() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(false);

        BizException failure = failureOf(new AssignRolesCommand(OTHER_USER_ID, List.of(NORMAL_ROLE_ID)));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(failure.getMessage()).isEqualTo("用户不存在");
        verifyNoInteractions(roleQuery, userRoleRegistrar);
    }

    @Test
    @DisplayName("改自己的角色但替换后不含内置角色：拒绝并提示，不写入")
    void shouldRejectRemovingOwnBuiltinRole() {
        given(userQuery.existsById(OPERATOR_ID)).willReturn(true);
        given(roleQuery.findIdentities(any())).willReturn(List.of(
                new RoleIdentity(NORMAL_ROLE_ID, false)));

        BizException failure = failureOf(new AssignRolesCommand(OPERATOR_ID, List.of(NORMAL_ROLE_ID)));

        // 409 段位而不是 40000：入参合法，被拒绝的是它造成的状态
        assertThat(failure.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(failure.getMessage()).isEqualTo("不能移除自己最后的内置角色");
        verifyNoInteractions(userRoleRegistrar);
    }

    @Test
    @DisplayName("把自己全部角色清空同样被拒绝：空集合里不可能有内置角色")
    void shouldRejectClearingOwnRoles() {
        given(userQuery.existsById(OPERATOR_ID)).willReturn(true);

        BizException failure = failureOf(new AssignRolesCommand(OPERATOR_ID, List.of()));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(failure.getMessage()).isEqualTo("不能移除自己最后的内置角色");
        verifyNoInteractions(userRoleRegistrar);
    }

    @Test
    @DisplayName("改自己的角色且替换后仍含内置角色：允许，且内置角色的判定来自库里的 builtin 标记")
    void shouldAllowKeepingOwnBuiltinRole() {
        given(userQuery.existsById(OPERATOR_ID)).willReturn(true);
        given(roleQuery.findIdentities(any())).willReturn(List.of(
                new RoleIdentity(BUILTIN_ROLE_ID, true),
                new RoleIdentity(NORMAL_ROLE_ID, false)));

        service.assign(new AssignRolesCommand(OPERATOR_ID, List.of(BUILTIN_ROLE_ID, NORMAL_ROLE_ID)));

        verify(userRoleRegistrar).replaceRoles(
                OPERATOR_ID, Set.of(BUILTIN_ROLE_ID, NORMAL_ROLE_ID), OPERATOR_ID);
    }

    @Test
    @DisplayName("改别人的角色使其失去内置角色：允许，守护只针对操作人自己")
    void shouldAllowChangingOthersWithoutBuiltinRole() {
        given(userQuery.existsById(OTHER_USER_ID)).willReturn(true);
        given(roleQuery.findIdentities(any())).willReturn(List.of(
                new RoleIdentity(NORMAL_ROLE_ID, false)));

        service.assign(new AssignRolesCommand(OTHER_USER_ID, List.of(NORMAL_ROLE_ID)));

        verify(userRoleRegistrar).replaceRoles(OTHER_USER_ID, Set.of(NORMAL_ROLE_ID), OPERATOR_ID);
    }

    @Test
    @DisplayName("当前会话无效：40100，不查库也不写入")
    void shouldRejectMissingSession() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.empty());

        BizException failure = failureOf(new AssignRolesCommand(OTHER_USER_ID, List.of(NORMAL_ROLE_ID)));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(userQuery, never()).existsById(anyLong());
        verifyNoInteractions(roleQuery, userRoleRegistrar);
    }

    private BizException failureOf(AssignRolesCommand command) {
        try {
            service.assign(command);
        } catch (BizException e) {
            return e;
        }
        throw new AssertionError("期望请求被拒绝，但通过了");
    }
}

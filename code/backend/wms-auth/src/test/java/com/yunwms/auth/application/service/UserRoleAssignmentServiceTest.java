package com.yunwms.auth.application.service;

import com.yunwms.auth.application.audit.AuditSnapshotKeys;
import com.yunwms.auth.application.command.AssignRolesCommand;
import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditEntry;
import com.yunwms.auth.domain.model.RoleIdentity;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.domain.model.UserSummary;
import com.yunwms.auth.domain.port.AuditRecorder;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.RoleQuery;
import com.yunwms.auth.domain.port.UserQuery;
import com.yunwms.auth.domain.port.UserRoleRegistrar;
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
 *
 * <p>留痕的动作必须是 {@code PERMISSION_CHANGE}：与「改个展示名」共用 {@code UPDATE}
 * 会让事后按动作筛选权限变更时两者分不开。
 */
@DisplayName("分配角色")
class UserRoleAssignmentServiceTest {

    private static final long OPERATOR_ID = 1L;
    private static final long OTHER_USER_ID = 9L;
    private static final long BUILTIN_ROLE_ID = 100L;
    private static final long NORMAL_ROLE_ID = 200L;
    private static final long OTHER_ROLE_ID = 300L;
    private static final String LOGIN_NAME = "zhangsan";

    private UserQuery userQuery;
    private RoleQuery roleQuery;
    private UserRoleRegistrar userRoleRegistrar;
    private CurrentSession currentSession;
    private AuditRecorder auditRecorder;
    private UserRoleAssignmentService service;

    @BeforeEach
    void setUp() {
        userQuery = mock(UserQuery.class);
        roleQuery = mock(RoleQuery.class);
        userRoleRegistrar = mock(UserRoleRegistrar.class);
        currentSession = mock(CurrentSession.class);
        auditRecorder = mock(AuditRecorder.class);
        service = new UserRoleAssignmentService(userQuery, roleQuery, userRoleRegistrar, currentSession,
                auditRecorder);

        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
    }

    @Test
    @DisplayName("替换成功：把去重后的角色集合与本页操作人交给写入端口")
    void shouldReplaceRoles() {
        givenUser(OTHER_USER_ID);
        given(roleQuery.findIdentities(any())).willReturn(List.of(
                new RoleIdentity(NORMAL_ROLE_ID, "AUDITOR", false)));

        service.assign(new AssignRolesCommand(OTHER_USER_ID, List.of(NORMAL_ROLE_ID)));

        verify(userRoleRegistrar).replaceRoles(OTHER_USER_ID, Set.of(NORMAL_ROLE_ID), OPERATOR_ID);
    }

    @Test
    @DisplayName("重复的角色标识只保留一个：集合语义，不该变成主键冲突")
    void shouldDeduplicateRoleIds() {
        givenUser(OTHER_USER_ID);
        given(roleQuery.findIdentities(any())).willReturn(List.of(
                new RoleIdentity(NORMAL_ROLE_ID, "AUDITOR", false)));

        service.assign(new AssignRolesCommand(OTHER_USER_ID, List.of(NORMAL_ROLE_ID, NORMAL_ROLE_ID)));

        // 去重发生在查询之前：不去重时 findIdentities 会收到两个相同的 id，
        // 而 SQL 只会返回一行，于是一次合法的提交被判成「有角色不存在」
        verify(roleQuery).findIdentities(Set.of(NORMAL_ROLE_ID));
        verify(userRoleRegistrar).replaceRoles(OTHER_USER_ID, Set.of(NORMAL_ROLE_ID), OPERATOR_ID);
    }

    @Test
    @DisplayName("空数组移除全部角色：合法，且不去查角色表")
    void shouldAllowRemovingAllRoles() {
        givenUser(OTHER_USER_ID);

        service.assign(new AssignRolesCommand(OTHER_USER_ID, List.of()));

        verifyNoInteractions(roleQuery);
        verify(userRoleRegistrar).replaceRoles(OTHER_USER_ID, Set.of(), OPERATOR_ID);
    }

    @Test
    @DisplayName("角色不存在整单拒绝：一条写入都不发")
    void shouldRejectUnknownRole() {
        givenUser(OTHER_USER_ID);
        // 请求两个角色，库里只回一个：说明另一个不存在（或已被逻辑删除）
        given(roleQuery.findIdentities(any())).willReturn(List.of(
                new RoleIdentity(NORMAL_ROLE_ID, "AUDITOR", false)));

        BizException failure = failureOf(new AssignRolesCommand(
                OTHER_USER_ID, List.of(NORMAL_ROLE_ID, BUILTIN_ROLE_ID)));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("指定的角色不存在");
        verifyNoInteractions(userRoleRegistrar, auditRecorder);
    }

    @Test
    @DisplayName("用户不存在：按 404 拒绝，不查角色也不写入")
    void shouldRejectMissingUser() {
        given(userQuery.findById(OTHER_USER_ID)).willReturn(Optional.empty());

        BizException failure = failureOf(new AssignRolesCommand(OTHER_USER_ID, List.of(NORMAL_ROLE_ID)));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(failure.getMessage()).isEqualTo("用户不存在");
        verifyNoInteractions(roleQuery, userRoleRegistrar, auditRecorder);
    }

    @Test
    @DisplayName("改自己的角色但替换后不含内置角色：拒绝并提示，不写入")
    void shouldRejectRemovingOwnBuiltinRole() {
        givenUser(OPERATOR_ID);
        given(roleQuery.findIdentities(any())).willReturn(List.of(
                new RoleIdentity(NORMAL_ROLE_ID, "AUDITOR", false)));

        BizException failure = failureOf(new AssignRolesCommand(OPERATOR_ID, List.of(NORMAL_ROLE_ID)));

        // 409 段位而不是 40000：入参合法，被拒绝的是它造成的状态
        assertThat(failure.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(failure.getMessage()).isEqualTo("不能移除自己最后的内置角色");
        verifyNoInteractions(userRoleRegistrar, auditRecorder);
    }

    @Test
    @DisplayName("把自己全部角色清空同样被拒绝：空集合里不可能有内置角色")
    void shouldRejectClearingOwnRoles() {
        givenUser(OPERATOR_ID);

        BizException failure = failureOf(new AssignRolesCommand(OPERATOR_ID, List.of()));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(failure.getMessage()).isEqualTo("不能移除自己最后的内置角色");
        verifyNoInteractions(userRoleRegistrar, auditRecorder);
    }

    @Test
    @DisplayName("改自己的角色且替换后仍含内置角色：允许，且内置角色的判定来自库里的 builtin 标记")
    void shouldAllowKeepingOwnBuiltinRole() {
        givenUser(OPERATOR_ID);
        given(roleQuery.findIdentities(any())).willReturn(List.of(
                new RoleIdentity(BUILTIN_ROLE_ID, "SUPER_ADMIN", true),
                new RoleIdentity(NORMAL_ROLE_ID, "AUDITOR", false)));

        service.assign(new AssignRolesCommand(OPERATOR_ID, List.of(BUILTIN_ROLE_ID, NORMAL_ROLE_ID)));

        verify(userRoleRegistrar).replaceRoles(
                OPERATOR_ID, Set.of(BUILTIN_ROLE_ID, NORMAL_ROLE_ID), OPERATOR_ID);
    }

    @Test
    @DisplayName("改别人的角色使其失去内置角色：允许，守护只针对操作人自己")
    void shouldAllowChangingOthersWithoutBuiltinRole() {
        givenUser(OTHER_USER_ID);
        given(roleQuery.findIdentities(any())).willReturn(List.of(
                new RoleIdentity(NORMAL_ROLE_ID, "AUDITOR", false)));

        service.assign(new AssignRolesCommand(OTHER_USER_ID, List.of(NORMAL_ROLE_ID)));

        verify(userRoleRegistrar).replaceRoles(OTHER_USER_ID, Set.of(NORMAL_ROLE_ID), OPERATOR_ID);
    }

    @Test
    @DisplayName("当前会话无效：40100，不查库也不写入")
    void shouldRejectMissingSession() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.empty());

        BizException failure = failureOf(new AssignRolesCommand(OTHER_USER_ID, List.of(NORMAL_ROLE_ID)));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(userQuery, never()).findById(anyLong());
        verifyNoInteractions(roleQuery, userRoleRegistrar, auditRecorder);
    }

    @Test
    @DisplayName("留痕：动作是 PERMISSION_CHANGE，对象标识与摘要都用登录名")
    void shouldRecordPermissionChangeAudit() {
        givenUser(OTHER_USER_ID);
        given(roleQuery.findIdentities(any())).willReturn(List.of(
                new RoleIdentity(NORMAL_ROLE_ID, "AUDITOR", false)));

        service.assign(new AssignRolesCommand(OTHER_USER_ID, List.of(NORMAL_ROLE_ID)));

        AuditEntry entry = captureSingleAudit();
        assertThat(entry.bizType()).isEqualTo(AuditBizType.USER);
        assertThat(entry.action()).isEqualTo(AuditAction.PERMISSION_CHANGE);
        assertThat(entry.bizId()).isEqualTo(LOGIN_NAME);
        assertThat(entry.summary()).isEqualTo("分配角色 " + LOGIN_NAME);
    }

    @Test
    @DisplayName("留痕：改前与改后的角色编码各记一份，日志答得出「改成了哪些角色」")
    void shouldRecordRoleCodesBeforeAndAfterAssignment() {
        // 改前挂着 CS_STAFF，本次提交把它换成 AUDITOR
        givenUserWithRoles(OTHER_USER_ID, "CS_STAFF");
        given(roleQuery.findIdentities(any())).willReturn(List.of(
                new RoleIdentity(NORMAL_ROLE_ID, "AUDITOR", false)));

        service.assign(new AssignRolesCommand(OTHER_USER_ID, List.of(NORMAL_ROLE_ID)));

        AuditEntry entry = captureSingleAudit();
        // 键集是分配角色专用的那份：账号快照里没有角色集合，套用它会让两份快照逐字相同
        assertThat(entry.beforeValue()).containsOnlyKeys(AuditSnapshotKeys.USER_ROLE_ASSIGNMENT);
        assertThat(entry.afterValue()).containsOnlyKeys(AuditSnapshotKeys.USER_ROLE_ASSIGNMENT);
        assertThat(entry.beforeValue()).containsEntry("roleCodes", List.of("CS_STAFF"));
        assertThat(entry.afterValue()).containsEntry("roleCodes", List.of("AUDITOR"));
        assertThat(entry.beforeValue()).isNotEqualTo(entry.afterValue());
    }

    @Test
    @DisplayName("留痕：编码排序后写入，角色身份的返回顺序不影响快照")
    void shouldRecordRoleCodesInSortedOrder() {
        givenUserWithRoles(OTHER_USER_ID, "PICKER", "AUDITOR");
        // 角色的返回顺序不保证：这里故意给出与编码字典序不同的顺序
        given(roleQuery.findIdentities(any())).willReturn(List.of(
                new RoleIdentity(OTHER_ROLE_ID, "AUDITOR", false),
                new RoleIdentity(NORMAL_ROLE_ID, "CS_STAFF", false)));

        service.assign(new AssignRolesCommand(OTHER_USER_ID, List.of(NORMAL_ROLE_ID, OTHER_ROLE_ID)));

        AuditEntry entry = captureSingleAudit();
        assertThat(entry.beforeValue()).containsEntry("roleCodes", List.of("AUDITOR", "PICKER"));
        assertThat(entry.afterValue()).containsEntry("roleCodes", List.of("AUDITOR", "CS_STAFF"));
    }

    @Test
    @DisplayName("留痕：改前改后角色相同，两份快照相等但仍写一条（分配本身是一次权限确认）")
    void shouldStillRecordWhenRoleSetUnchanged() {
        givenUserWithRoles(OTHER_USER_ID, "AUDITOR");
        given(roleQuery.findIdentities(any())).willReturn(List.of(
                new RoleIdentity(NORMAL_ROLE_ID, "AUDITOR", false)));

        service.assign(new AssignRolesCommand(OTHER_USER_ID, List.of(NORMAL_ROLE_ID)));

        AuditEntry entry = captureSingleAudit();
        assertThat(entry.action()).isEqualTo(AuditAction.PERMISSION_CHANGE);
        assertThat(entry.summary()).isEqualTo("分配角色 " + LOGIN_NAME);
        // 相等说明这次确认没有改变结果；但它不构成跳过留痕的理由——确认过哪些角色同样要能追溯
        assertThat(entry.beforeValue()).isEqualTo(entry.afterValue());
    }

    /**
     * 造一份没有角色的账号详情。
     *
     * @param userId 账号标识
     */
    private void givenUser(long userId) {
        givenUserWithRoles(userId);
    }

    /**
     * 造一份账号详情，带上改前挂着的角色编码。
     *
     * <p>{@code roleIds} 留空：这条链路只读摘要里的 {@code roleCodes}（分配按标识整批替换，
     * 不比对原有标识），造一份用不上的数据只会让后来的人以为它被测到了。
     *
     * @param userId    账号标识
     * @param roleCodes 改前挂着的角色编码
     */
    private void givenUserWithRoles(long userId, String... roleCodes) {
        UserSummary summary = new UserSummary(userId, LOGIN_NAME, "张三", SubjectType.STAFF, null, null,
                UserStatus.ACTIVE, List.of(roleCodes), null);
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

    private BizException failureOf(AssignRolesCommand command) {
        try {
            service.assign(command);
        } catch (BizException e) {
            return e;
        }
        throw new AssertionError("期望请求被拒绝，但通过了");
    }
}

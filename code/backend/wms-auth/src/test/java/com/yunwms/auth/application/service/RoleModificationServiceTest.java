package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.ChangeRoleStatusCommand;
import com.yunwms.auth.application.command.UpdateRoleCommand;
import com.yunwms.auth.domain.model.RoleStatus;
import com.yunwms.auth.domain.model.RoleSummary;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.RoleQuery;
import com.yunwms.auth.domain.port.RoleUpdater;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 角色维护用例的单测：编辑、启停、删除。
 *
 * <p>端口替换为 mock，不起 Spring、不连库。这里钉住的是用例自己的职责：内置角色三个动作全拒、
 * 占用中的角色不许删且提示人数、同状态重复提交不写库、名称与备注的归一化、以及库约束冲突
 * 必须翻译成前端能展示的错误码而不是 50000。软删除与替换授权的 SQL 语义属于集成测试。
 */
@DisplayName("角色维护")
class RoleModificationServiceTest {

    private static final long ROLE_ID = 7L;
    private static final long OPERATOR_ID = 9L;
    private static final String CODE = "CS_STAFF";
    private static final String NAME = "客服";

    private RoleQuery roleQuery;
    private RoleUpdater roleUpdater;
    private CurrentSession currentSession;
    private RoleModificationService roleModificationService;

    @BeforeEach
    void setUp() {
        roleQuery = mock(RoleQuery.class);
        roleUpdater = mock(RoleUpdater.class);
        currentSession = mock(CurrentSession.class);
        roleModificationService = new RoleModificationService(roleQuery, roleUpdater, currentSession);
        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
    }

    @Test
    @DisplayName("编辑：名称与备注裁剪后落库，menuIds 去重后整批替换")
    void shouldUpdateWithTrimmedFieldsAndDeduplicatedMenus() {
        givenRole(false, RoleStatus.ACTIVE, 0L);

        roleModificationService.update(new UpdateRoleCommand(
                ROLE_ID, "  客服二线 ", "  对接货主  ", Arrays.asList(3L, 3L, 4L)));

        verify(roleUpdater).updateWithMenus(
                eq(ROLE_ID), eq("客服二线"), eq("对接货主"), eq(Set.of(3L, 4L)), eq(OPERATOR_ID), any());
    }

    @Test
    @DisplayName("编辑：空 menuIds 与 null remark 表示清空授权与备注")
    void shouldClearMenusAndRemarkWithEmptyValues() {
        givenRole(false, RoleStatus.ACTIVE, 0L);

        roleModificationService.update(new UpdateRoleCommand(ROLE_ID, NAME, "  ", List.of()));

        // 空集合不是「不改」：替换语义下它就是「这个角色之后一个权限点都没有」
        verify(roleUpdater).updateWithMenus(
                eq(ROLE_ID), eq(NAME), isNull(), eq(Set.of()), eq(OPERATOR_ID), any());
    }

    @Test
    @DisplayName("编辑：名称为空被拒绝，不写库")
    void shouldRejectBlankName() {
        givenRole(false, RoleStatus.ACTIVE, 0L);

        BizException failure = failureOf(() ->
                roleModificationService.update(new UpdateRoleCommand(ROLE_ID, "   ", null, List.of())));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("角色名称不能为空");
        verifyNoInteractions(roleUpdater);
    }

    @Test
    @DisplayName("编辑：角色不存在按 404 处理，不写库")
    void shouldRejectMissingRole() {
        given(roleQuery.findById(ROLE_ID)).willReturn(Optional.empty());

        BizException failure = failureOf(() ->
                roleModificationService.update(new UpdateRoleCommand(ROLE_ID, NAME, null, List.of())));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(failure.getMessage()).isEqualTo("角色不存在");
        verifyNoInteractions(roleUpdater);
    }

    @Test
    @DisplayName("编辑：内置角色被拒绝，不写库")
    void shouldRejectEditingBuiltinRole() {
        givenRole(true, RoleStatus.ACTIVE, 1L);

        BizException failure = failureOf(() ->
                roleModificationService.update(new UpdateRoleCommand(ROLE_ID, NAME, null, List.of())));

        // 409 段位而不是 40000：入参合法，被拒绝的是「这个对象不允许被改」
        assertThat(failure.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(failure.getMessage()).isEqualTo("内置角色不可编辑");
        verifyNoInteractions(roleUpdater);
    }

    @Test
    @DisplayName("编辑：菜单不存在由外键拦下，翻成参数错误而不是 409")
    void shouldTranslateMissingMenu() {
        givenRole(false, RoleStatus.ACTIVE, 0L);
        willThrow(constraintViolation("sys_role_menu_menu_id_fkey"))
                .given(roleUpdater).updateWithMenus(eq(ROLE_ID), eq(NAME), isNull(), eq(Set.of(999L)),
                        eq(OPERATOR_ID), any());

        BizException failure = failureOf(() ->
                roleModificationService.update(new UpdateRoleCommand(ROLE_ID, NAME, null, List.of(999L))));

        // 40000 而不是 409：入参引用了一个不存在的 id，改一个 id 就能过
        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("指定的菜单或权限点不存在");
    }

    @Test
    @DisplayName("启停：目标状态与当前状态相同按成功处理，不写库")
    void shouldDoNothingWhenStatusUnchanged() {
        givenRole(false, RoleStatus.DISABLED, 0L);

        roleModificationService.changeStatus(new ChangeRoleStatusCommand(ROLE_ID, RoleStatus.DISABLED));

        // 幂等不是靠「写一次同样的值」实现的：重复提交不该刷新 updated_at，
        // 也不该产生一条「谁在什么时候停用了它」的误导性审计记录
        verifyNoInteractions(roleUpdater);
    }

    @Test
    @DisplayName("启停：状态变化时按目标状态落库")
    void shouldUpdateStatus() {
        givenRole(false, RoleStatus.ACTIVE, 0L);

        roleModificationService.changeStatus(new ChangeRoleStatusCommand(ROLE_ID, RoleStatus.DISABLED));

        verify(roleUpdater).updateStatus(eq(ROLE_ID), eq(RoleStatus.DISABLED), eq(OPERATOR_ID), any());
    }

    @Test
    @DisplayName("启停：内置角色被拒绝，不写库")
    void shouldRejectDisablingBuiltinRole() {
        givenRole(true, RoleStatus.ACTIVE, 1L);

        BizException failure = failureOf(() ->
                roleModificationService.changeStatus(new ChangeRoleStatusCommand(ROLE_ID, RoleStatus.DISABLED)));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(failure.getMessage()).isEqualTo("内置角色不可启停");
        verifyNoInteractions(roleUpdater);
    }

    @Test
    @DisplayName("删除：仍被用户占用时拒绝，提示里带占用人数，不写库")
    void shouldRejectDeletingOccupiedRole() {
        givenRole(false, RoleStatus.ACTIVE, 3L);

        BizException failure = failureOf(() -> roleModificationService.delete(ROLE_ID));

        // 规格原文要求「拒绝删除并提示占用人数」：只说「被占用」时管理员还得自己去找是谁在用
        assertThat(failure.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(failure.getMessage()).isEqualTo("该角色仍被 3 个用户使用，请先解除分配");
        verifyNoInteractions(roleUpdater);
    }

    @Test
    @DisplayName("删除：内置角色即使没人占用也被拒绝")
    void shouldRejectDeletingBuiltinRole() {
        givenRole(true, RoleStatus.ACTIVE, 0L);

        BizException failure = failureOf(() -> roleModificationService.delete(ROLE_ID));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(failure.getMessage()).isEqualTo("内置角色不可删除");
        verifyNoInteractions(roleUpdater);
    }

    @Test
    @DisplayName("删除：没人占用时软删除")
    void shouldSoftDeleteUnoccupiedRole() {
        givenRole(false, RoleStatus.DISABLED, 0L);

        roleModificationService.delete(ROLE_ID);

        verify(roleUpdater).softDelete(eq(ROLE_ID), eq(OPERATOR_ID), any());
    }

    @Test
    @DisplayName("会话无效：按未登录拒绝，连角色都不查")
    void shouldRejectWhenSessionHasNoUser() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.empty());

        BizException failure = failureOf(() -> roleModificationService.delete(ROLE_ID));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(roleQuery, roleUpdater);
    }

    private void givenRole(boolean builtin, RoleStatus status, long userCount) {
        given(roleQuery.findById(ROLE_ID)).willReturn(Optional.of(
                new RoleSummary(ROLE_ID, CODE, NAME, builtin, status, userCount, 2L, null)));
    }

    /**
     * 构造一个带约束名的完整性冲突，形如 PostgreSQL 抛出的
     * {@code ERROR: insert or update on table "sys_role_menu" violates foreign key constraint ...}。
     *
     * @param constraintName 约束名
     * @return Spring 翻译后的异常，其根因消息里带约束名
     */
    private static DataIntegrityViolationException constraintViolation(String constraintName) {
        return new DataIntegrityViolationException("角色写入违反约束",
                new SQLException("ERROR: 违反约束 \"" + constraintName + "\""));
    }

    /** 断言操作被拒绝并取回异常；意外成功时直接让测试失败，避免它被当成「没抛异常」的空白通过。 */
    private static BizException failureOf(Runnable action) {
        try {
            action.run();
        } catch (BizException e) {
            return e;
        }
        throw new AssertionError("期望操作被拒绝，但它成功了");
    }
}

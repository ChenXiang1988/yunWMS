package com.yunwms.auth.application.service;

import com.yunwms.auth.domain.model.DataScope;
import com.yunwms.auth.domain.model.DataScopeSetting;
import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.model.RoleDataScope;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.DataScopeQuery;
import com.yunwms.auth.domain.port.OwnerAssigneeQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
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
 * 生效范围解析的单测：规格「数据权限范围」一节的规则逐条落在 {@link DataScopeResolver} 上。
 *
 * <p>端口替换为 mock，不起 Spring、不连库。这里钉住的是优先级与合并顺序：货主账号强制隔离、
 * 用户覆盖优先且不回退角色、启用角色并集、{@code ALL} 吞并、{@code ASSIGNED} 实时推导、
 * 空明细不贡献、空并集无权。真实的「停用角色不计入」由查询层过滤，在集成测试里验证。
 */
@DisplayName("数据范围解析")
class DataScopeResolverTest {

    private static final long USER_ID = 11L;
    private static final long OWNER_ID = 21L;

    private CurrentSession currentSession;
    private DataScopeQuery dataScopeQuery;
    private OwnerAssigneeQuery ownerAssigneeQuery;
    private DataScopeResolver resolver;

    @BeforeEach
    void setUp() {
        currentSession = mock(CurrentSession.class);
        dataScopeQuery = mock(DataScopeQuery.class);
        ownerAssigneeQuery = mock(OwnerAssigneeQuery.class);
        resolver = new DataScopeResolver(currentSession, dataScopeQuery, ownerAssigneeQuery);

        given(currentSession.currentUserId()).willReturn(OptionalLong.of(USER_ID));
        given(currentSession.currentSubjectType()).willReturn(Optional.of(SubjectType.STAFF));
    }

    @Test
    @DisplayName("未登录：结果为空集，不查任何配置")
    void shouldBeNoneWhenNotLoggedIn() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.empty());

        assertThat(resolver.resolve()).isEqualTo(DataScope.none());
        verifyNoInteractions(dataScopeQuery, ownerAssigneeQuery);
    }

    @Test
    @DisplayName("会话缺少主体类型：按无权处理，不猜主体")
    void shouldBeNoneWhenSubjectTypeMissing() {
        given(currentSession.currentSubjectType()).willReturn(Optional.empty());

        assertThat(resolver.resolve()).isEqualTo(DataScope.none());
        verifyNoInteractions(dataScopeQuery, ownerAssigneeQuery);
    }

    @Test
    @DisplayName("货主账号：强制按所属货主过滤，忽略角色与用户配置")
    void shouldForceOwnerScopeForOwnerAccount() {
        given(currentSession.currentSubjectType()).willReturn(Optional.of(SubjectType.OWNER));
        given(currentSession.currentOwnerId()).willReturn(OptionalLong.of(OWNER_ID));

        assertThat(resolver.resolve()).isEqualTo(DataScope.restricted(Set.of(OWNER_ID)));
        // 连配置都不该读：读了就有「配置比强制隔离优先」的解释空间
        verifyNoInteractions(dataScopeQuery, ownerAssigneeQuery);
    }

    @Test
    @DisplayName("货主账号的会话缺少所属货主：按无权处理，不滑到角色解析")
    void shouldBeNoneWhenOwnerAccountHasNoOwnerId() {
        given(currentSession.currentSubjectType()).willReturn(Optional.of(SubjectType.OWNER));
        given(currentSession.currentOwnerId()).willReturn(OptionalLong.empty());
        // 角色侧故意给一份「全部货主」：若实现滑到角色解析，这条用例会失败
        given(dataScopeQuery.findActiveRoleScopes(USER_ID))
                .willReturn(List.of(new RoleDataScope(1L, DataScopeType.ALL)));

        assertThat(resolver.resolve()).isEqualTo(DataScope.none());
    }

    @Test
    @DisplayName("用户覆盖优先：有覆盖就不再看角色，且不回退")
    void shouldUseUserOverrideOnly() {
        given(dataScopeQuery.findUserSetting(USER_ID))
                .willReturn(Optional.of(new DataScopeSetting(DataScopeType.CUSTOM, Set.of(3L))));

        assertThat(resolver.resolve()).isEqualTo(DataScope.restricted(Set.of(3L)));
        verify(dataScopeQuery, never()).findActiveRoleScopes(anyLong());
    }

    @Test
    @DisplayName("用户覆盖为 CUSTOM 空明细：结果无权，不回退角色")
    void shouldBeNoneWhenUserOverrideIsEmpty() {
        given(dataScopeQuery.findUserSetting(USER_ID))
                .willReturn(Optional.of(new DataScopeSetting(DataScopeType.CUSTOM, Set.of())));
        // 角色侧有货主 3：若实现「空了就回落」，这条用例会失败
        given(dataScopeQuery.findActiveRoleScopes(USER_ID))
                .willReturn(List.of(new RoleDataScope(2L, DataScopeType.CUSTOM)));

        assertThat(resolver.resolve()).isEqualTo(DataScope.none());
        verify(dataScopeQuery, never()).findActiveRoleScopes(anyLong());
    }

    @Test
    @DisplayName("用户覆盖为 ALL：全部货主")
    void shouldHonourUserOverrideAll() {
        given(dataScopeQuery.findUserSetting(USER_ID))
                .willReturn(Optional.of(new DataScopeSetting(DataScopeType.ALL, Set.of())));

        assertThat(resolver.resolve()).isEqualTo(DataScope.all());
    }

    @Test
    @DisplayName("无覆盖且无启用角色：无权")
    void shouldBeNoneWhenNoActiveRoles() {
        given(dataScopeQuery.findUserSetting(USER_ID)).willReturn(Optional.empty());
        given(dataScopeQuery.findActiveRoleScopes(USER_ID)).willReturn(List.of());

        assertThat(resolver.resolve()).isEqualTo(DataScope.none());
    }

    @Test
    @DisplayName("角色并集：多个 CUSTOM 求和")
    void shouldUnionCustomRoles() {
        given(dataScopeQuery.findUserSetting(USER_ID)).willReturn(Optional.empty());
        given(dataScopeQuery.findActiveRoleScopes(USER_ID)).willReturn(List.of(
                new RoleDataScope(1L, DataScopeType.CUSTOM),
                new RoleDataScope(2L, DataScopeType.CUSTOM)));
        given(dataScopeQuery.findCustomOwnerIds(List.of(1L, 2L)))
                .willReturn(Map.of(1L, List.of(1L, 2L), 2L, List.of(3L)));

        assertThat(resolver.resolve()).isEqualTo(DataScope.restricted(Set.of(1L, 2L, 3L)));
        // 一次批量查询取回两个角色的明细，不在循环里逐角色查
        verify(dataScopeQuery).findCustomOwnerIds(List.of(1L, 2L));
    }

    @Test
    @DisplayName("ALL 吞并其他角色范围，且不再查明细与负责人")
    void shouldLetAllSwallowRoleScopes() {
        given(dataScopeQuery.findUserSetting(USER_ID)).willReturn(Optional.empty());
        given(dataScopeQuery.findActiveRoleScopes(USER_ID)).willReturn(List.of(
                new RoleDataScope(1L, DataScopeType.CUSTOM),
                new RoleDataScope(2L, DataScopeType.ALL)));

        assertThat(resolver.resolve()).isEqualTo(DataScope.all());
        verifyNoInteractions(ownerAssigneeQuery);
        verify(dataScopeQuery, never()).findCustomOwnerIds(any());
    }

    @Test
    @DisplayName("ASSIGNED 实时推导：只查一次负责人，多个 ASSIGNED 角色复用同一结果")
    void shouldResolveAssignedOnce() {
        given(dataScopeQuery.findUserSetting(USER_ID)).willReturn(Optional.empty());
        given(dataScopeQuery.findActiveRoleScopes(USER_ID)).willReturn(List.of(
                new RoleDataScope(1L, DataScopeType.ASSIGNED),
                new RoleDataScope(2L, DataScopeType.ASSIGNED)));
        given(ownerAssigneeQuery.findAssignedOwnerIds(USER_ID)).willReturn(Set.of(4L, 5L));

        assertThat(resolver.resolve()).isEqualTo(DataScope.restricted(Set.of(4L, 5L)));
        verify(ownerAssigneeQuery).findAssignedOwnerIds(USER_ID);
    }

    @Test
    @DisplayName("CUSTOM 与 ASSIGNED 混合：并集")
    void shouldUnionCustomAndAssigned() {
        given(dataScopeQuery.findUserSetting(USER_ID)).willReturn(Optional.empty());
        given(dataScopeQuery.findActiveRoleScopes(USER_ID)).willReturn(List.of(
                new RoleDataScope(1L, DataScopeType.CUSTOM),
                new RoleDataScope(2L, DataScopeType.ASSIGNED)));
        given(dataScopeQuery.findCustomOwnerIds(List.of(1L))).willReturn(Map.of(1L, List.of(1L)));
        given(ownerAssigneeQuery.findAssignedOwnerIds(USER_ID)).willReturn(Set.of(2L, 3L));

        assertThat(resolver.resolve()).isEqualTo(DataScope.restricted(Set.of(1L, 2L, 3L)));
    }

    @Test
    @DisplayName("空明细的 CUSTOM 角色不吞并其他角色")
    void shouldIgnoreEmptyCustomRole() {
        given(dataScopeQuery.findUserSetting(USER_ID)).willReturn(Optional.empty());
        given(dataScopeQuery.findActiveRoleScopes(USER_ID)).willReturn(List.of(
                new RoleDataScope(1L, DataScopeType.CUSTOM),
                new RoleDataScope(2L, DataScopeType.CUSTOM)));
        // 角色 1 没有明细，因此不出现在映射里
        given(dataScopeQuery.findCustomOwnerIds(List.of(1L, 2L))).willReturn(Map.of(2L, List.of(3L)));

        assertThat(resolver.resolve()).isEqualTo(DataScope.restricted(Set.of(3L)));
    }

    @Test
    @DisplayName("全部角色停用：查询层返回空列表，结果无权")
    void shouldBeNoneWhenAllRolesDisabled() {
        given(dataScopeQuery.findUserSetting(USER_ID)).willReturn(Optional.empty());
        // 停用角色由 DataScopeQuery 过滤掉，解析侧看到的只有空列表
        given(dataScopeQuery.findActiveRoleScopes(USER_ID)).willReturn(List.of());

        assertThat(resolver.resolve()).isEqualTo(DataScope.none());
        verifyNoInteractions(ownerAssigneeQuery);
    }
}

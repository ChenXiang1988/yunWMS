package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.ConfigureUserDataScopeCommand;
import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.domain.model.UserSummary;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.DataScopeOwnerRegistrar;
import com.yunwms.auth.domain.port.UserAccountUpdater;
import com.yunwms.auth.domain.port.UserQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 用户级数据范围配置用例的单测：货主账号拒绝、清除覆盖与空明细的区别、两处写入的分工。
 *
 * <p>端口替换为 mock，不起 Spring、不连库；两处写入是否真的同事务提交由集成测试验证。
 */
@DisplayName("用户数据范围配置")
class UserDataScopeConfigurationServiceTest {

    private static final long USER_ID = 31L;
    private static final long OPERATOR_ID = 9L;

    private UserQuery userQuery;
    private UserAccountUpdater userAccountUpdater;
    private DataScopeOwnerRegistrar dataScopeOwnerRegistrar;
    private DataScopeOwnerValidator dataScopeOwnerValidator;
    private CurrentSession currentSession;
    private UserDataScopeConfigurationService service;

    @BeforeEach
    void setUp() {
        userQuery = mock(UserQuery.class);
        userAccountUpdater = mock(UserAccountUpdater.class);
        dataScopeOwnerRegistrar = mock(DataScopeOwnerRegistrar.class);
        dataScopeOwnerValidator = mock(DataScopeOwnerValidator.class);
        currentSession = mock(CurrentSession.class);
        service = new UserDataScopeConfigurationService(userQuery, userAccountUpdater,
                dataScopeOwnerRegistrar, dataScopeOwnerValidator, currentSession);

        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
        given(userQuery.findById(USER_ID)).willReturn(Optional.of(profile(SubjectType.STAFF)));
    }

    @Test
    @DisplayName("货主账号：拒绝配置覆盖，类型与明细都不写")
    void shouldRejectOwnerAccount() {
        given(userQuery.findById(USER_ID)).willReturn(Optional.of(profile(SubjectType.OWNER)));

        BizException failure = failureOf(new ConfigureUserDataScopeCommand(
                USER_ID, DataScopeType.ALL, List.of()));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("货主账号的数据范围由所属货主固定，不能配置用户级覆盖");
        verifyNoInteractions(userAccountUpdater, dataScopeOwnerRegistrar);
    }

    @Test
    @DisplayName("账号不存在：404，不写库")
    void shouldRejectMissingUser() {
        given(userQuery.findById(USER_ID)).willReturn(Optional.empty());

        BizException failure = failureOf(new ConfigureUserDataScopeCommand(
                USER_ID, DataScopeType.ALL, List.of()));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(failure.getMessage()).isEqualTo("用户不存在");
        verifyNoInteractions(userAccountUpdater, dataScopeOwnerRegistrar);
    }

    @Test
    @DisplayName("清除覆盖：类型写 NULL，明细清空")
    void shouldClearOverride() {
        service.configure(new ConfigureUserDataScopeCommand(USER_ID, null, List.of()));

        verify(userAccountUpdater).updateDataScopeType(eq(USER_ID), isNull(), eq(OPERATOR_ID), any());
        verify(dataScopeOwnerRegistrar).replaceUserOwners(USER_ID, Set.of());
        // 清除不是「配置成某个类型」，因此不该走类型与明细的搭配校验
        verifyNoInteractions(dataScopeOwnerValidator);
    }

    @Test
    @DisplayName("清除覆盖却提交了明细：拒绝，因为两种意图矛盾")
    void shouldRejectClearingWithOwnerIds() {
        BizException failure = failureOf(new ConfigureUserDataScopeCommand(USER_ID, null, List.of(3L)));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("清除数据范围覆盖时不能提交货主明细");
        verifyNoInteractions(userAccountUpdater, dataScopeOwnerRegistrar);
    }

    @Test
    @DisplayName("CUSTOM 覆盖：类型与明细都写，明细交给仓储整批替换")
    void shouldConfigureCustomOverride() {
        service.configure(new ConfigureUserDataScopeCommand(USER_ID, DataScopeType.CUSTOM, List.of(3L, 4L)));

        verify(dataScopeOwnerValidator).requireConfigurable(DataScopeType.CUSTOM, Set.of(3L, 4L));
        verify(userAccountUpdater).updateDataScopeType(eq(USER_ID), eq(DataScopeType.CUSTOM), eq(OPERATOR_ID), any());
        verify(dataScopeOwnerRegistrar).replaceUserOwners(USER_ID, Set.of(3L, 4L));
    }

    @Test
    @DisplayName("CUSTOM 空明细：覆盖存在但结果无权，明细写空")
    void shouldAcceptEmptyCustomOverride() {
        service.configure(new ConfigureUserDataScopeCommand(USER_ID, DataScopeType.CUSTOM, List.of()));

        verify(userAccountUpdater).updateDataScopeType(eq(USER_ID), eq(DataScopeType.CUSTOM), eq(OPERATOR_ID), any());
        verify(dataScopeOwnerRegistrar).replaceUserOwners(USER_ID, Set.of());
    }

    @Test
    @DisplayName("ALL 覆盖：清空明细，避免留一份永不生效的名单")
    void shouldClearDetailsForAllOverride() {
        service.configure(new ConfigureUserDataScopeCommand(USER_ID, DataScopeType.ALL, List.of()));

        verify(userAccountUpdater).updateDataScopeType(eq(USER_ID), eq(DataScopeType.ALL), eq(OPERATOR_ID), any());
        verify(dataScopeOwnerRegistrar).replaceUserOwners(USER_ID, Set.of());
    }

    @Test
    @DisplayName("会话无效：按未登录拒绝，连账号都不查")
    void shouldRejectWhenSessionHasNoUser() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.empty());

        BizException failure = failureOf(new ConfigureUserDataScopeCommand(
                USER_ID, DataScopeType.ALL, List.of()));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(userQuery, never()).findById(USER_ID);
        verifyNoInteractions(userAccountUpdater, dataScopeOwnerRegistrar);
    }

    private static UserProfile profile(SubjectType subjectType) {
        Long ownerId = subjectType == SubjectType.OWNER ? 77L : null;
        return new UserProfile(
                new UserSummary(USER_ID, "zhangsan", "张三", subjectType, ownerId, null,
                        UserStatus.ACTIVE, List.of(), null),
                "zhangsan@example.com", null, null, List.of(),
                Instant.parse("2026-01-04T08:30:00Z"), List.of());
    }

    private BizException failureOf(ConfigureUserDataScopeCommand command) {
        try {
            service.configure(command);
        } catch (BizException e) {
            return e;
        }
        throw new AssertionError("期望配置被拒绝，但它成功了");
    }
}

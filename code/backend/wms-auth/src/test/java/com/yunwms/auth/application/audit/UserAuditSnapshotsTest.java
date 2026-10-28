package com.yunwms.auth.application.audit;

import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.model.NewUserAccount;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.domain.model.UserSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户审计快照的单测。
 *
 * <p>钉住四件事：键集与白名单全等（多一个键立即失败，这是 {@code passwordHash} 之类被顺手塞进来的
 * 唯一防线）、密码相关的一切都不出现、各入口的「变更后」值确实取的是本次落库的值，
 * 以及「分配角色」那份专用快照的集合语义（编码排序后写入、空集合写空数组）。
 */
@DisplayName("用户审计快照")
class UserAuditSnapshotsTest {

    private static final long USER_ID = 12L;
    private static final String LOGIN_NAME = "zhangsan";
    private static final String DISPLAY_NAME = "张三";
    private static final String EMAIL = "zhangsan@example.com";
    private static final String WECOM_USER_ID = "wx_zhangsan";

    /** 看起来像真实 BCrypt 哈希的串：断言它一个字符都不该出现在快照里。 */
    private static final String PASSWORD_HASH = "$2a$10$0123456789012345678901234567890123456789012345678901";

    @Test
    @DisplayName("建档：键集与白名单全等，状态与待改密取建档固定值，范围为空")
    void shouldBuildCreationSnapshot() {
        NewUserAccount account = new NewUserAccount(LOGIN_NAME, DISPLAY_NAME, SubjectType.STAFF, null,
                EMAIL, WECOM_USER_ID, PASSWORD_HASH);

        Map<String, Object> snapshot = UserAuditSnapshots.ofCreated(account);

        assertThat(snapshot).containsOnlyKeys(AuditSnapshotKeys.USER);
        assertThat(snapshot)
                .containsEntry("loginName", LOGIN_NAME)
                .containsEntry("displayName", DISPLAY_NAME)
                .containsEntry("subjectType", "STAFF")
                .containsEntry("ownerId", null)
                .containsEntry("email", EMAIL)
                .containsEntry("wecomUserId", WECOM_USER_ID)
                .containsEntry("status", "ACTIVE")
                .containsEntry("dataScopeType", null)
                .containsEntry("mustChangePassword", true);
    }

    @Test
    @DisplayName("建档：货主账号的 ownerId 按字符串写，与接口层标识口径一致")
    void shouldWriteOwnerIdAsText() {
        NewUserAccount account = new NewUserAccount("owner01", "货主账号", SubjectType.OWNER, 77L,
                null, null, PASSWORD_HASH);

        assertThat(UserAuditSnapshots.ofCreated(account))
                .containsEntry("ownerId", "77")
                .containsEntry("email", null)
                .containsEntry("wecomUserId", null);
    }

    @Test
    @DisplayName("建档：密码哈希不出现在任何值里")
    void shouldNeverCarryPasswordHashOnCreation() {
        NewUserAccount account = new NewUserAccount(LOGIN_NAME, DISPLAY_NAME, SubjectType.STAFF, null,
                EMAIL, null, PASSWORD_HASH);

        Map<String, Object> snapshot = UserAuditSnapshots.ofCreated(account);

        // 按值判定而不是按字段名：换个键名塞进去，这条断言仍然红
        assertThat(snapshot.values()).doesNotContain(PASSWORD_HASH);
        assertThat(snapshot.keySet()).noneMatch(key -> key.toLowerCase().contains("password")
                && !key.equals("mustChangePassword"));
    }

    @Test
    @DisplayName("当前值：取自账号详情，含需改密标记与用户级范围覆盖")
    void shouldProjectProfile() {
        Map<String, Object> snapshot = UserAuditSnapshots.of(profile("CUSTOM", true));

        assertThat(snapshot).containsOnlyKeys(AuditSnapshotKeys.USER);
        assertThat(snapshot)
                .containsEntry("loginName", LOGIN_NAME)
                .containsEntry("displayName", DISPLAY_NAME)
                .containsEntry("subjectType", "STAFF")
                .containsEntry("ownerId", null)
                .containsEntry("status", "ACTIVE")
                .containsEntry("dataScopeType", "CUSTOM")
                .containsEntry("mustChangePassword", true);
    }

    @Test
    @DisplayName("编辑后：只换展示名、邮箱与企业微信 userid，其余取变更前的既成事实")
    void shouldRefreshOnlyEditedFields() {
        UserProfile before = profile("ALL", false);

        Map<String, Object> after = UserAuditSnapshots.ofUpdated(before, "张三（已改名）", null, null);

        assertThat(after).containsOnlyKeys(AuditSnapshotKeys.USER);
        assertThat(after)
                .containsEntry("displayName", "张三（已改名）")
                .containsEntry("email", null)
                .containsEntry("wecomUserId", null)
                // 编辑改不到这些：值必须与变更前一致，否则日志会声称一次它没做的事
                .containsEntry("status", "ACTIVE")
                .containsEntry("dataScopeType", "ALL")
                .containsEntry("mustChangePassword", false);
    }

    @Test
    @DisplayName("状态流转后：只换状态")
    void shouldChangeOnlyStatus() {
        UserProfile before = profile(null, false);

        Map<String, Object> after = UserAuditSnapshots.ofStatusChanged(before, UserStatus.DISABLED);

        assertThat(after).containsOnlyKeys(AuditSnapshotKeys.USER);
        assertThat(after)
                .containsEntry("status", "DISABLED")
                .containsEntry("displayName", DISPLAY_NAME)
                .containsEntry("dataScopeType", null);
    }

    @Test
    @DisplayName("范围配置后：换覆盖类型，清除覆盖时为 null 而不是某个默认值")
    void shouldChangeOnlyDataScopeType() {
        UserProfile before = profile("CUSTOM", false);

        assertThat(UserAuditSnapshots.ofDataScopeChanged(before, DataScopeType.ASSIGNED))
                .containsOnlyKeys(AuditSnapshotKeys.USER)
                .containsEntry("dataScopeType", "ASSIGNED");
        assertThat(UserAuditSnapshots.ofDataScopeChanged(before, null))
                .containsEntry("dataScopeType", null)
                .containsEntry("status", "ACTIVE");
    }

    @Test
    @DisplayName("分配角色：键集只有角色编码，编码排序后写入")
    void shouldSortRoleCodesForAssignment() {
        Map<String, Object> snapshot = UserAuditSnapshots.ofRoleAssignment(
                List.of("CS_STAFF", "AUDITOR", "PICKER"));

        assertThat(snapshot).containsOnlyKeys(AuditSnapshotKeys.USER_ROLE_ASSIGNMENT);
        // 字典序：A < C < P；排序写死后，事后比对两份快照才能直接判等
        assertThat(snapshot).containsEntry("roleCodes", List.of("AUDITOR", "CS_STAFF", "PICKER"));
    }

    @Test
    @DisplayName("分配角色：同一批角色以不同顺序提交，两份快照逐字相等")
    void shouldProduceSameSnapshotRegardlessOfRoleOrder() {
        Map<String, Object> one = UserAuditSnapshots.ofRoleAssignment(List.of("CS_STAFF", "AUDITOR"));
        Map<String, Object> other = UserAuditSnapshots.ofRoleAssignment(List.of("AUDITOR", "CS_STAFF"));

        // 集合语义：提交顺序不构成变更，否则「改前改后其实一样」会被读成一次权限变更
        assertThat(one).isEqualTo(other);
    }

    @Test
    @DisplayName("分配角色：没有任何角色时写空数组，不是 null")
    void shouldWriteEmptyArrayWhenNoRoles() {
        Map<String, Object> snapshot = UserAuditSnapshots.ofRoleAssignment(List.of());

        // 空数组是「这个人没有任何角色」这个明确状态；null 会让读取方分不清它与「快照里没这个键」
        assertThat(snapshot).containsEntry("roleCodes", List.of());
        assertThat(snapshot.get("roleCodes")).isNotNull().isInstanceOf(List.class);
    }

    @Test
    @DisplayName("分配角色：角色编码不出现在通用账号快照里")
    void shouldKeepRoleCodesOutOfAccountSnapshot() {
        // 通用快照被建档、编辑、停用、范围配置共用：加一个键就是给那四处各加一次角色查询
        assertThat(UserAuditSnapshots.of(profile(null, false))).doesNotContainKey("roleCodes");
    }

    /**
     * 造一份账号详情。
     *
     * @param dataScopeType     用户级范围覆盖编码，{@code null} 表示继承角色默认
     * @param mustChangePassword 是否仍需先改密
     * @return 账号详情
     */
    private static UserProfile profile(String dataScopeType, boolean mustChangePassword) {
        UserSummary summary = new UserSummary(USER_ID, LOGIN_NAME, DISPLAY_NAME, SubjectType.STAFF, null,
                null, UserStatus.ACTIVE, List.of(), null);
        return new UserProfile(summary, EMAIL, WECOM_USER_ID, mustChangePassword, dataScopeType,
                List.of(), Instant.parse("2026-01-01T00:00:00Z"), List.of());
    }
}

package com.yunwms.auth.application.audit;

import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.model.NewRole;
import com.yunwms.auth.domain.model.RoleStatus;
import com.yunwms.auth.domain.model.RoleSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 角色审计快照的单测。
 *
 * <p>除键集全等之外，这里专门钉住 {@code menuIds} 的两件事：按数值排序后写入，
 * 以及同一批授权以不同顺序提交时快照必须相等——否则「编辑没改任何东西」的判定会被顺序差异
 * 打成「有变更」，日志里就会多出一堆什么都没发生的记录。
 */
@DisplayName("角色审计快照")
class RoleAuditSnapshotsTest {

    private static final long ROLE_ID = 7L;
    private static final String CODE = "CS_STAFF";
    private static final String NAME = "客服";

    @Test
    @DisplayName("建档：键集与白名单全等，builtin 与状态取建档固定值")
    void shouldBuildCreationSnapshot() {
        NewRole role = new NewRole(CODE, NAME, "对接货主", Set.of(4L, 3L), DataScopeType.CUSTOM, Set.of(9L));

        Map<String, Object> snapshot = RoleAuditSnapshots.ofCreated(role);

        assertThat(snapshot).containsOnlyKeys(AuditSnapshotKeys.ROLE);
        assertThat(snapshot)
                .containsEntry("code", CODE)
                .containsEntry("name", NAME)
                .containsEntry("builtin", false)
                .containsEntry("status", "ACTIVE")
                .containsEntry("dataScopeType", "CUSTOM")
                .containsEntry("remark", "对接货主");
        // 日志里没有要「谁的授权」之外的东西：明细（指定货主）不是角色快照的白名单字段
        assertThat(snapshot).doesNotContainKey("ownerIds");
    }

    @Test
    @DisplayName("menuIds 按数值升序写入：字符串排序会把 10 排在 9 前面")
    void shouldSortMenuIdsNumerically() {
        NewRole role = new NewRole(CODE, NAME, null, Set.of(10L, 9L, 2L), DataScopeType.ALL, Set.of());

        assertThat(RoleAuditSnapshots.ofCreated(role)).containsEntry("menuIds", List.of("2", "9", "10"));
    }

    @Test
    @DisplayName("同一批授权以不同顺序提交：两份快照必须相等，重复元素不影响")
    void shouldNotDependOnSubmissionOrder() {
        Map<String, Object> first = RoleAuditSnapshots.of(summary(RoleStatus.ACTIVE, 2L), DataScopeType.CUSTOM,
                Arrays.asList(5L, 3L, 4L));
        Map<String, Object> second = RoleAuditSnapshots.of(summary(RoleStatus.ACTIVE, 2L), DataScopeType.CUSTOM,
                Arrays.asList(3L, 4L, 5L, 5L));

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("当前值：取自角色摘要与本次读到的授权、范围")
    void shouldProjectSummary() {
        Map<String, Object> snapshot = RoleAuditSnapshots.of(summary(RoleStatus.DISABLED, 1L),
                DataScopeType.ASSIGNED, List.of(8L));

        assertThat(snapshot).containsOnlyKeys(AuditSnapshotKeys.ROLE);
        assertThat(snapshot)
                .containsEntry("code", CODE)
                .containsEntry("name", NAME)
                .containsEntry("builtin", false)
                .containsEntry("status", "DISABLED")
                .containsEntry("dataScopeType", "ASSIGNED")
                .containsEntry("remark", "备注")
                .containsEntry("menuIds", List.of("8"));
    }

    @Test
    @DisplayName("编辑后：只换名称、备注与授权，状态与范围取变更前的值")
    void shouldRefreshOnlyEditedFields() {
        Map<String, Object> after = RoleAuditSnapshots.ofUpdated(summary(RoleStatus.DISABLED, 2L),
                DataScopeType.ALL, "客服二线", null, List.of());

        assertThat(after).containsOnlyKeys(AuditSnapshotKeys.ROLE);
        assertThat(after)
                .containsEntry("name", "客服二线")
                .containsEntry("remark", null)
                .containsEntry("menuIds", List.of())
                .containsEntry("status", "DISABLED")
                .containsEntry("dataScopeType", "ALL");
    }

    @Test
    @DisplayName("启停后：只换状态")
    void shouldChangeOnlyStatus() {
        Map<String, Object> after = RoleAuditSnapshots.ofStatusChanged(summary(RoleStatus.ACTIVE, 2L),
                DataScopeType.CUSTOM, RoleStatus.DISABLED, List.of(4L));

        assertThat(after)
                .containsEntry("status", "DISABLED")
                .containsEntry("name", NAME)
                .containsEntry("menuIds", List.of("4"));
    }

    /**
     * 造一份角色摘要。
     *
     * @param status          角色状态
     * @param permissionCount 已勾选的数量，快照不读它（只有日志读键集）
     * @return 角色摘要
     */
    private static RoleSummary summary(RoleStatus status, long permissionCount) {
        return new RoleSummary(ROLE_ID, CODE, NAME, false, status, 0L, permissionCount, "备注");
    }
}

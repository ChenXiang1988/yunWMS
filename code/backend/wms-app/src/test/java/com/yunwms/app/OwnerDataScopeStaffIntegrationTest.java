package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 员工账号的货主数据范围过滤：范围真的落到 SQL 上、真的随配置与负责人实时变化、真的挡住他方数据。
 *
 * <p><b>为什么必须是真实 PostgreSQL + MockMvc：</b>要证的是「拦截器把条件追加进了最终执行的 SQL」，
 * 而这件事只有真库能证。单测能证明片段长什么样（{@code DataScopePermissionHandlerTest}），
 * 但证明不了 JSqlParser 能不能解析这两条 XML 语句、条件是否与 {@code deleted_at} 和关键字筛选
 * 正确共存、{@code count} 与分页是否落在同一范围。H2 也不行：本仓的 SQL 用了 {@code ILIKE}
 * 与 {@code ||} 拼接，换库测通只能说明「语法大致像」。
 *
 * <p><b>覆盖的规则（与规格「数据权限范围」逐条对应）：</b>
 * <ul>
 *   <li>角色 {@code ALL}：可见全部货主；超管账号同理；</li>
 *   <li>角色 {@code CUSTOM}：只可见明细里的货主，他方详情与终止影响清单都是 404——两个接口都必须
 *       挡住，否则可以借计数探测「这个标识对应一条真实货主」；</li>
 *   <li>角色 {@code ASSIGNED}：随 {@code owner_assignee} 实时变化（增删立即生效，不做静态名单）；</li>
 *   <li>用户覆盖优先于角色，清除后回落到角色默认；</li>
 *   <li>多角色并集，{@code ALL} 吞并其他取值，角色停用立即收缩；</li>
 *   <li>空范围：列表为空、详情 404，不回退到「全部」；</li>
 *   <li>分页 {@code total} 与 {@code records} 同范围（只登记其中一条语句时这里会红）。</li>
 * </ul>
 *
 * <p><b>造数方式：</b>直接用 SQL 造账号、角色与货主，不走接口。角色只授予
 * {@code system:owner:list} 这一个权限点（本类验证的是数据范围，不是功能权限），因此
 * 非超管账号也能调这些接口——否则测到的会是 403 而不是范围。
 *
 * <p>用例之间不隔离数据（整个测试 JVM 共用一个库），因此每个用例用自己的货主编码、SKU 前缀、
 * 登录名与关键字标记；断言只针对本用例造的那几条。
 */
@DisplayName("员工货主数据范围过滤")
class OwnerDataScopeStaffIntegrationTest extends AbstractOwnerDataScopeIntegrationTest {

    @Test
    @DisplayName("角色 ALL：列表可见全部货主，详情可读")
    void shouldListAllOwnersForAllScopeRole() throws Exception {
        String marker = "FLT-ALL";
        long ownerA = seedOwner("OWNER-FLT-ALL-A", "范围全量甲" + marker, "FLA2", "ACTIVE");
        long ownerB = seedOwner("OWNER-FLT-ALL-B", "范围全量乙" + marker, "FLB3", "ACTIVE");
        long staffId = seedStaff("flt-all-user");
        link(staffId, seedScopedRole("FLT_ROLE_ALL", "ALL"));

        String token = loginToken("flt-all-user");
        JsonNode page = list(token, marker);

        assertThat(codes(page)).containsExactlyInAnyOrder("OWNER-FLT-ALL-A", "OWNER-FLT-ALL-B");
        assertThat(total(page)).as("ALL 不是名单：两条都要在 total 里").isEqualTo(2);
        assertThat(ownerDetailCode(token, ownerA)).isZero();
        assertThat(ownerDetailCode(token, ownerB)).isZero();
    }

    @Test
    @DisplayName("角色 CUSTOM：只可见明细货主，他方详情与终止影响清单都是 404")
    void shouldSeeOnlyCustomOwners() throws Exception {
        String marker = "FLT-CUSTOM";
        long ownerA = seedOwner("OWNER-FLT-CUSTOM-A", "范围指定甲" + marker, "FCA2", "ACTIVE");
        long ownerB = seedOwner("OWNER-FLT-CUSTOM-B", "范围指定乙" + marker, "FCB3", "ACTIVE");
        long staffId = seedStaff("flt-custom-user");
        link(staffId, seedScopedRole("FLT_ROLE_CUSTOM_A", "CUSTOM", ownerA));

        String token = loginToken("flt-custom-user");
        JsonNode page = list(token, marker);

        assertThat(codes(page)).containsExactly("OWNER-FLT-CUSTOM-A");
        // total 与 records 同范围：只登记分页不登记计数时，这里会看到 total=2、records=1
        assertThat(total(page)).isEqualTo(1);
        assertThat(ownerDetailCode(token, ownerA)).isZero();
        assertThat(ownerDetailCode(token, ownerB)).isEqualTo(NOT_FOUND_CODE);
        // 影响清单只有两个数字，但数字本身能证明「这个标识是一条真实货主」，必须一起挡住
        assertThat(terminationImpactCode(token, ownerB)).isEqualTo(NOT_FOUND_CODE);
        assertThat(terminationImpactCode(token, ownerA)).isZero();
    }

    @Test
    @DisplayName("角色 ASSIGNED：随 owner_assignee 实时变化，新增与移除立即生效")
    void shouldFollowAssigneeChangesInRealtime() throws Exception {
        String marker = "FLT-ASSIGNED";
        long ownerA = seedOwner("OWNER-FLT-ASG-A", "负责甲" + marker, "FAA2", "ACTIVE");
        long ownerB = seedOwner("OWNER-FLT-ASG-B", "负责乙" + marker, "FAB3", "ACTIVE");
        long staffId = seedStaff("flt-assigned-user");
        link(staffId, seedScopedRole("FLT_ROLE_ASSIGNED", "ASSIGNED"));
        String token = loginToken("flt-assigned-user");

        assign(ownerA, staffId);
        assertThat(codes(list(token, marker))).containsExactly("OWNER-FLT-ASG-A");

        // 不做静态名单：负责人一加，下一个请求就看得见
        assign(ownerB, staffId);
        assertThat(codes(list(token, marker)))
                .containsExactlyInAnyOrder("OWNER-FLT-ASG-A", "OWNER-FLT-ASG-B");

        // 移除关系同样立即收缩，而不是等到重新登录或重启
        unassign(ownerA, staffId);
        assertThat(codes(list(token, marker))).containsExactly("OWNER-FLT-ASG-B");
    }

    @Test
    @DisplayName("用户覆盖优先于角色，清除覆盖后回落到角色默认")
    void shouldPreferUserOverrideThenFallBackToRoles() throws Exception {
        String marker = "FLT-OVERRIDE";
        long ownerA = seedOwner("OWNER-FLT-OVR-A", "覆盖甲" + marker, "FQA2", "ACTIVE");
        long ownerB = seedOwner("OWNER-FLT-OVR-B", "覆盖乙" + marker, "FQB3", "ACTIVE");
        long staffId = seedStaff("flt-override-user");
        link(staffId, seedScopedRole("FLT_ROLE_OVERRIDE", "CUSTOM", ownerA));
        String token = loginToken("flt-override-user");

        assertThat(codes(list(token, marker))).containsExactly("OWNER-FLT-OVR-A");

        // 覆盖不回退角色：设置了覆盖就只按覆盖算
        setUserOverride(staffId, "CUSTOM", ownerB);
        assertThat(codes(list(token, marker))).containsExactly("OWNER-FLT-OVR-B");

        // 清除覆盖（类型写 NULL）后才回到角色的默认值
        clearUserOverride(staffId);
        assertThat(codes(list(token, marker))).containsExactly("OWNER-FLT-OVR-A");
    }

    @Test
    @DisplayName("多角色并集：两个 CUSTOM 角色的明细取并集")
    void shouldUnionMultipleRoleScopes() throws Exception {
        String marker = "FLT-UNION";
        long ownerA = seedOwner("OWNER-FLT-UNION-A", "并集甲" + marker, "FUA2", "ACTIVE");
        long ownerB = seedOwner("OWNER-FLT-UNION-B", "并集乙" + marker, "FUB3", "ACTIVE");
        long staffId = seedStaff("flt-union-user");
        link(staffId, seedScopedRole("FLT_ROLE_UNION_A", "CUSTOM", ownerA));
        link(staffId, seedScopedRole("FLT_ROLE_UNION_B", "CUSTOM", ownerB));

        String token = loginToken("flt-union-user");

        assertThat(codes(list(token, marker)))
                .containsExactlyInAnyOrder("OWNER-FLT-UNION-A", "OWNER-FLT-UNION-B");
    }

    @Test
    @DisplayName("ALL 吞并其他取值：再叠一个 CUSTOM 角色不会把范围缩小")
    void shouldLetAllScopeSwallowCustomScopes() throws Exception {
        String marker = "FLT-SWALLOW";
        seedOwner("OWNER-FLT-SWL-A", "吞并甲" + marker, "FSA2", "ACTIVE");
        long ownerB = seedOwner("OWNER-FLT-SWL-B", "吞并乙" + marker, "FSB3", "ACTIVE");
        long staffId = seedStaff("flt-swallow-user");
        // CUSTOM 只给乙：若 ALL 没有吞并它，甲会消失——那正是「并集算成交集」的表现
        link(staffId, seedScopedRole("FLT_ROLE_SWALLOW_CUSTOM", "CUSTOM", ownerB));
        link(staffId, seedScopedRole("FLT_ROLE_SWALLOW_ALL", "ALL"));

        String token = loginToken("flt-swallow-user");

        assertThat(codes(list(token, marker)))
                .containsExactlyInAnyOrder("OWNER-FLT-SWL-A", "OWNER-FLT-SWL-B");
    }

    @Test
    @DisplayName("角色停用：立即从并集里消失，不需要重新登录")
    void shouldShrinkImmediatelyWhenRoleDisabled() throws Exception {
        String marker = "FLT-DISABLE";
        long ownerA = seedOwner("OWNER-FLT-DIS-A", "停用甲" + marker, "FDA2", "ACTIVE");
        long ownerB = seedOwner("OWNER-FLT-DIS-B", "停用乙" + marker, "FDB3", "ACTIVE");
        long staffId = seedStaff("flt-disable-user");
        long roleA = seedScopedRole("FLT_ROLE_DISABLE_A", "CUSTOM", ownerA);
        long roleB = seedScopedRole("FLT_ROLE_DISABLE_B", "CUSTOM", ownerB);
        link(staffId, roleA);
        link(staffId, roleB);
        String token = loginToken("flt-disable-user");

        // 先确认两个角色都在并集里，否则「停用后变小」可能只是本来就没生效
        assertThat(codes(list(token, marker))).hasSize(2);

        setRoleStatus(roleB, "DISABLED");
        assertThat(codes(list(token, marker))).containsExactly("OWNER-FLT-DIS-A");
    }

    @Test
    @DisplayName("空范围：列表为空、详情 404，不回退到全部")
    void shouldReturnEmptyPageForEmptyScope() throws Exception {
        String marker = "FLT-EMPTY";
        long ownerA = seedOwner("OWNER-FLT-EMPTY-A", "空范围甲" + marker, "FEA2", "ACTIVE");
        long staffId = seedStaff("flt-empty-user");
        link(staffId, seedScopedRole("FLT_ROLE_EMPTY", "CUSTOM"));

        String token = loginToken("flt-empty-user");
        JsonNode page = list(token, marker);

        assertThat(total(page)).isZero();
        assertThat(codes(page)).isEmpty();
        // 空集合与「无权」是同一件事：详情不能因为「范围里没有它所以不加条件」而放行
        assertThat(ownerDetailCode(token, ownerA)).isEqualTo(NOT_FOUND_CODE);
    }

    @Test
    @DisplayName("超管：角色 ALL，可见全部货主")
    void shouldGiveSuperAdminAllOwners() throws Exception {
        String marker = "FLT-SUPER";
        seedOwner("OWNER-FLT-SUPER-A", "超管甲" + marker, "FUA3", "ACTIVE");
        seedOwner("OWNER-FLT-SUPER-B", "超管乙" + marker, "FUB4", "ACTIVE");
        long staffId = seedStaff("flt-super-user");
        link(staffId, superAdminRole());

        String token = loginToken("flt-super-user");
        JsonNode page = list(token, marker);

        assertThat(codes(page)).containsExactlyInAnyOrder("OWNER-FLT-SUPER-A", "OWNER-FLT-SUPER-B");
        assertThat(total(page)).isEqualTo(2);
    }

    @Test
    @DisplayName("分页：total 是范围与筛选之后的总数，翻页时 records 与它同范围")
    void shouldKeepTotalConsistentWithRecords() throws Exception {
        String marker = "FLT-PAGE";
        long ownerA = seedOwner("OWNER-FLT-PAGE-A", "分页甲" + marker, "FPA2", "ACTIVE");
        long ownerB = seedOwner("OWNER-FLT-PAGE-B", "分页乙" + marker, "FPB3", "ACTIVE");
        long staffId = seedStaff("flt-page-user");
        link(staffId, seedScopedRole("FLT_ROLE_PAGE", "CUSTOM", ownerA, ownerB));
        String token = loginToken("flt-page-user");

        JsonNode firstPage = list(token, marker, 1, 1);
        assertThat(total(firstPage)).as("total 是满足条件的两条，不是本页条数").isEqualTo(2);
        assertThat(codes(firstPage)).hasSize(1);

        JsonNode secondPage = list(token, marker, 2, 1);
        assertThat(total(secondPage)).isEqualTo(2);
        assertThat(codes(secondPage)).hasSize(1);
        assertThat(codes(secondPage)).doesNotContainAnyElementsOf(codes(firstPage));

        // 关键字把两条都筛掉时，total 与 records 必须同为 0（计数语句漏了范围会留下 2）
        assertThat(total(list(token, "Nomatch" + marker))).isZero();
        assertThat(codes(list(token, "Nomatch" + marker))).isEmpty();
    }
}

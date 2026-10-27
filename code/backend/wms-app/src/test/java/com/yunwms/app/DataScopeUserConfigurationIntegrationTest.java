package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.yunwms.auth.domain.model.DataScopeSetting;
import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.model.RoleDataScope;
import com.yunwms.auth.domain.port.DataScopeQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据范围「用户覆盖」与读取口径的端到端验证。
 *
 * <p>要证的是单测证不了的四件事：
 * <ol>
 *   <li>{@code sys_user.data_scope_type} 是「有覆盖」的唯一判据：清除必须写 {@code NULL}，
 *       而不是写成某个具体类型；明细同步清空；</li>
 *   <li>货主账号的范围由所属货主固定，任何用户级覆盖都必须被拒；</li>
 *   <li>缺权限点时请求停在鉴权，配置一行都不落库；</li>
 *   <li>{@code DataScopeQuery} 的真实 SQL 口径：停用角色不计入并集、非 {@code CUSTOM} 不查明细。</li>
 * </ol>
 *
 * <p>操作人与无权限账号都用 SQL 造：种子账号 {@code superadmin} 带「需先改密」标记，
 * 替它改密会污染 {@link BootstrapIntegrationTest} 的断言。超管权限在鉴权时动态展开，
 * 因此挂着内置超管角色的账号天然拥有 {@code system:user:update}。
 *
 * <p>用例之间不隔离数据（整个测试 JVM 共用一个库），因此每个用例用自己的登录名。
 */
@DisplayName("数据范围用户配置")
class DataScopeUserConfigurationIntegrationTest extends AbstractDataScopeIntegrationTest {

    private static final String OPERATOR_LOGIN_NAME = "data-scope-operator";
    private static final String NO_PERMISSION_LOGIN_NAME = "data-scope-nobody";

    /** 启用中的货主：CUSTOM 明细的合法目标。 */
    private static final String ACTIVE_OWNER_ONE_CODE = "OWNER-DS-1";
    private static final String ACTIVE_OWNER_TWO_CODE = "OWNER-DS-2";

    /** 无权限点角色编码，用例自造；迁移里除超管外不预置角色。 */
    private static final String NO_PERMISSION_ROLE = "TEST_DATA_SCOPE_NO_PERMISSION";

    /** 直接验证真实 SQL 口径，比通过接口回显更能钉住「停用角色不计入并集」这类行为。 */
    @Autowired
    private DataScopeQuery dataScopeQuery;

    private long activeOwnerOneId;
    private long activeOwnerTwoId;

    @BeforeEach
    void prepareAccountsAndOwners() {
        Long operatorId = seedStaff(OPERATOR_LOGIN_NAME, "数据范围操作员");
        Long nobodyId = seedStaff(NO_PERMISSION_LOGIN_NAME, "数据范围无权限");
        // 超管权限由鉴权时动态展开，这里不写 sys_role_menu：那正是规格禁止的做法
        link(operatorId, seedRole("SUPER_ADMIN", "超级管理员"));
        link(nobodyId, seedRole(NO_PERMISSION_ROLE + "_ROLE", "数据范围用无权限角色"));

        activeOwnerOneId = seedOwner(ACTIVE_OWNER_ONE_CODE, "范围货主一", "DSXA", "ACTIVE");
        activeOwnerTwoId = seedOwner(ACTIVE_OWNER_TWO_CODE, "范围货主二", "DSXB", "ACTIVE");
    }

    @Test
    @DisplayName("配置用户覆盖：CUSTOM 落库并在详情回显 ownerIds")
    void shouldPersistUserOverride() throws Exception {
        String token = consoleToken();
        String userId = Long.toString(seedStaff("data-scope-target", "被配置的员工"));

        JsonNode response = configureUserScope(token, userId, "CUSTOM", List.of(String.valueOf(activeOwnerOneId)));
        assertThat(response.path("code").asInt()).as("配置失败：%s", response).isZero();

        assertThat(dataScopeTypeOfUser(Long.parseLong(userId))).isEqualTo("CUSTOM");
        assertThat(ownerIdsOfUser(Long.parseLong(userId))).containsExactly(activeOwnerOneId);

        JsonNode detail = getJson(token, "/api/console/users/" + userId).path("data");
        assertThat(detail.path("dataScopeType").asText()).isEqualTo("CUSTOM");
        assertThat(textsOf(detail.path("ownerIds"))).containsExactly(String.valueOf(activeOwnerOneId));
    }

    @Test
    @DisplayName("清除用户覆盖：类型写 NULL、明细清空，详情回到继承角色默认")
    void shouldClearUserOverride() throws Exception {
        String token = consoleToken();
        long targetId = seedStaff("data-scope-clear", "被清除的员工");
        String userId = Long.toString(targetId);
        assertThat(configureUserScope(token, userId, "CUSTOM", List.of(String.valueOf(activeOwnerOneId)))
                .path("code").asInt()).isZero();

        JsonNode cleared = configureUserScope(token, userId, null, List.of());
        assertThat(cleared.path("code").asInt()).as("清除失败：%s", cleared).isZero();

        // data_scope_type 为 NULL 才是「没有覆盖」：写任何一个具体类型都表示「有覆盖」
        assertThat(dataScopeTypeOfUser(targetId)).isNull();
        assertThat(ownerIdsOfUser(targetId)).isEmpty();

        JsonNode detail = getJson(token, "/api/console/users/" + userId).path("data");
        assertThat(detail.path("dataScopeType").isNull()).isTrue();
        assertThat(textsOf(detail.path("ownerIds"))).isEmpty();
    }

    @Test
    @DisplayName("清除用户覆盖时提交明细：拒绝，两种意图不能混在一份请求里")
    void shouldRejectClearingWithOwnerIds() throws Exception {
        String token = consoleToken();
        String userId = Long.toString(seedStaff("data-scope-clear-bad", "清除带明细"));

        JsonNode response = configureUserScope(token, userId, null, List.of(String.valueOf(activeOwnerOneId)));

        assertThat(response.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(response.path("message").asText()).isEqualTo("清除数据范围覆盖时不能提交货主明细");
    }

    @Test
    @DisplayName("货主账号：拒绝用户级覆盖，库里不留任何配置")
    void shouldRejectOwnerAccountOverride() throws Exception {
        String token = consoleToken();
        long ownerUserId = seedOwnerAccount("data-scope-owner-account", "货主账号", activeOwnerOneId);

        JsonNode response = configureUserScope(token, Long.toString(ownerUserId), "ALL", List.of());

        assertThat(response.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(response.path("message").asText()).isEqualTo("货主账号的数据范围由所属货主固定，不能配置用户级覆盖");
        assertThat(dataScopeTypeOfUser(ownerUserId)).isNull();
        assertThat(ownerIdsOfUser(ownerUserId)).isEmpty();
    }

    @Test
    @DisplayName("配置用户覆盖：账号不存在返回 404")
    void shouldRejectMissingUser() throws Exception {
        JsonNode response = configureUserScope(consoleToken(), "999999", "ALL", List.of());

        assertThat(response.path("code").asInt()).isEqualTo(NOT_FOUND_CODE);
        assertThat(response.path("message").asText()).isEqualTo("用户不存在");
    }

    @Test
    @DisplayName("无 system:user:update 权限的账号：HTTP 403 且配置未落库")
    void shouldRejectConfigurationWithoutPermission() throws Exception {
        String token = login(NO_PERMISSION_LOGIN_NAME).path("data").path("token").asText();
        long targetId = seedStaff("data-scope-denied-target", "被拒绝的目标");

        mockMvc.perform(put("/api/console/users/" + targetId + "/data-scope")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DataScopePayload("ALL", List.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        assertThat(dataScopeTypeOfUser(targetId)).isNull();
    }

    @Test
    @DisplayName("生效范围读取：停用角色不计入并集，禁用后立即从结果里消失")
    void shouldExcludeDisabledRolesFromActiveScopes() {
        long staffId = seedStaff("data-scope-resolver", "范围解析员工");
        long enabledRoleId = seedRole("DS_ENABLED_ROLE", "启用角色");
        long disabledRoleId = seedRole("DS_DISABLED_ROLE", "停用角色");
        setRoleStatus(disabledRoleId, "DISABLED");
        link(staffId, enabledRoleId);
        link(staffId, disabledRoleId);

        List<RoleDataScope> scopes = dataScopeQuery.findActiveRoleScopes(staffId);

        assertThat(scopes).extracting(RoleDataScope::roleId).containsExactly(enabledRoleId);
    }

    @Test
    @DisplayName("配置读取：CUSTOM 才读明细，非 CUSTOM 不返回明细")
    void shouldReadSettingsThroughQueryPort() throws Exception {
        long roleId = Long.parseLong(createdRoleId(createRole(consoleToken(), "ROLE_SCOPE_QUERY",
                "读取口径", List.of())));
        // 直接写库而不是走接口：这里验证的是读侧口径，与写侧校验无关
        jdbcTemplate.update("UPDATE sys_role SET data_scope_type = 'CUSTOM' WHERE id = ?", roleId);
        jdbcTemplate.update("""
                INSERT INTO sys_data_scope_owner (role_id, owner_id) VALUES (?, ?)
                """, roleId, activeOwnerTwoId);

        DataScopeSetting custom = dataScopeQuery.findRoleSetting(roleId).orElseThrow();
        assertThat(custom.type()).isEqualTo(DataScopeType.CUSTOM);
        assertThat(custom.ownerIds()).containsExactly(activeOwnerTwoId);

        jdbcTemplate.update("UPDATE sys_role SET data_scope_type = 'ALL' WHERE id = ?", roleId);

        DataScopeSetting all = dataScopeQuery.findRoleSetting(roleId).orElseThrow();
        assertThat(all.type()).isEqualTo(DataScopeType.ALL);
        // 非 CUSTOM 一律返回空明细：查一次只能是空结果，不该为此付一次往返
        assertThat(all.ownerIds()).isEmpty();
    }

    /** 用操作人账号登录取令牌；处于待改密状态时属于测试自身的前提失败。 */
    private String consoleToken() throws Exception {
        return loginToken(OPERATOR_LOGIN_NAME);
    }

    /** 配置用户数据范围覆盖；{@code dataScopeType} 为 {@code null} 表示清除覆盖。 */
    private JsonNode configureUserScope(String token, String userId, String dataScopeType, List<String> ownerIds)
            throws Exception {
        return putJson(token, "/api/console/users/" + userId + "/data-scope",
                objectMapper.writeValueAsString(new DataScopePayload(dataScopeType, ownerIds)));
    }

    /** 直接查库取用户的数据范围覆盖类型；没有覆盖时为 {@code null}。 */
    private String dataScopeTypeOfUser(long userId) {
        return jdbcTemplate.queryForObject("SELECT data_scope_type FROM sys_user WHERE id = ?", String.class, userId);
    }

    /** 直接查库取用户的货主明细，按标识升序。 */
    private List<Long> ownerIdsOfUser(long userId) {
        return jdbcTemplate.queryForList(
                "SELECT owner_id FROM sys_data_scope_owner WHERE user_id = ? ORDER BY owner_id", Long.class, userId);
    }
}

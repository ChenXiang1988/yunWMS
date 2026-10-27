package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据范围「角色」配置入口的端到端验证：类型与明细真的落库、详情真的回显、非法配置真的被拒。
 *
 * <p>要证的是单测证不了的三件事：
 * <ol>
 *   <li>{@code sys_role.data_scope_type} 与 {@code sys_data_scope_owner} 真的被写进库，
 *       而且替换语义是「整批替换」而不是追加；</li>
 *   <li>详情回显读的是库里的事实，不是请求参数的缓存；</li>
 *   <li>「货主必须存在且启用」这条判定真的穿透到 {@code wms-master}（{@code wms-app} 的适配器
 *       接得对），而不是在 auth 里猜的。</li>
 * </ol>
 *
 * <p>操作人不复用种子账号 {@code superadmin}（它带着「需先改密」标记，替它改密会污染
 * {@link BootstrapIntegrationTest} 的断言），改为用 SQL 造一个挂着内置超管角色的已改密账号：
 * 超管权限在鉴权时动态展开，因此它天然拥有 {@code system:role:update}。
 *
 * <p>用例之间不隔离数据（整个测试 JVM 共用一个库），因此每个用例用自己的编码。
 */
@DisplayName("数据范围角色配置")
class DataScopeRoleConfigurationIntegrationTest extends AbstractDataScopeIntegrationTest {

    private static final String OPERATOR_LOGIN_NAME = "data-scope-operator";

    /** 两个启用中的货主：CUSTOM 明细的合法目标。 */
    private static final String ACTIVE_OWNER_ONE_CODE = "OWNER-DS-1";
    private static final String ACTIVE_OWNER_TWO_CODE = "OWNER-DS-2";
    /** 草稿货主：未启用，不能写进范围配置。 */
    private static final String DRAFT_OWNER_CODE = "OWNER-DS-DRAFT";
    /** 已软删除的货主：同样不能写进范围配置。 */
    private static final String DELETED_OWNER_CODE = "OWNER-DS-DEL";

    private long activeOwnerOneId;
    private long activeOwnerTwoId;
    private long draftOwnerId;
    private long deletedOwnerId;

    @BeforeEach
    void prepareOperatorAndOwners() {
        Long operatorId = seedStaff(OPERATOR_LOGIN_NAME, "数据范围操作员");
        // 超管权限由鉴权时动态展开，这里不写 sys_role_menu：那正是规格禁止的做法
        link(operatorId, seedRole("SUPER_ADMIN", "超级管理员"));

        activeOwnerOneId = seedOwner(ACTIVE_OWNER_ONE_CODE, "范围货主一", "DSXA", "ACTIVE");
        activeOwnerTwoId = seedOwner(ACTIVE_OWNER_TWO_CODE, "范围货主二", "DSXB", "ACTIVE");
        draftOwnerId = seedOwner(DRAFT_OWNER_CODE, "范围草稿货主", "DSXC", "DRAFT");
        deletedOwnerId = seedDeletedOwner(DELETED_OWNER_CODE, "范围已删货主", "DSXD");
    }

    @Test
    @DisplayName("建角色时带 CUSTOM 与货主：类型与明细落库，详情原样回显")
    void shouldPersistRoleDataScopeOnCreation() throws Exception {
        String code = "ROLE_SCOPE_CREATE";

        JsonNode created = createRole(consoleToken(), code, "带范围的客服", List.of(),
                "CUSTOM", List.of(String.valueOf(activeOwnerOneId), String.valueOf(activeOwnerTwoId)));
        assertThat(created.path("code").asInt()).as("建角色失败：%s", created).isZero();
        String roleId = created.path("data").path("id").asText();

        assertThat(dataScopeTypeOfRole(Long.parseLong(roleId))).isEqualTo("CUSTOM");
        assertThat(ownerIdsOfRole(Long.parseLong(roleId)))
                .containsExactlyInAnyOrder(activeOwnerOneId, activeOwnerTwoId);

        JsonNode detail = getJson(consoleToken(), "/api/console/roles/" + roleId).path("data");
        assertThat(detail.path("dataScopeType").asText()).isEqualTo("CUSTOM");
        assertThat(textsOf(detail.path("ownerIds")))
                .containsExactlyElementsOf(sortedTexts(List.of(activeOwnerOneId, activeOwnerTwoId)));
    }

    @Test
    @DisplayName("建角色时省略数据范围：默认 CUSTOM 且明细为空，详情回显空数组")
    void shouldDefaultToEmptyCustomOnCreation() throws Exception {
        String code = "ROLE_SCOPE_DEFAULT";

        String roleId = createdRoleId(createRole(consoleToken(), code, "未配范围的客服", List.of()));

        assertThat(dataScopeTypeOfRole(Long.parseLong(roleId))).isEqualTo("CUSTOM");
        assertThat(ownerIdsOfRole(Long.parseLong(roleId))).isEmpty();

        JsonNode detail = getJson(consoleToken(), "/api/console/roles/" + roleId).path("data");
        // 未配置与「配置成空明细」在库里是同一种状态（都是无权），回显必须是空数组而不是 null
        assertThat(detail.path("ownerIds").isArray()).isTrue();
        assertThat(textsOf(detail.path("ownerIds"))).isEmpty();
    }

    @Test
    @DisplayName("配置角色范围：CUSTOM 整批替换明细，改成 ALL 后明细被清空")
    void shouldReplaceRoleDataScope() throws Exception {
        String token = consoleToken();
        String roleId = createdRoleId(createRole(token, "ROLE_SCOPE_REPLACE", "待改范围的客服", List.of()));

        assertThat(configureRoleScope(token, roleId, "CUSTOM", List.of(String.valueOf(activeOwnerOneId)))
                .path("code").asInt()).isZero();
        assertThat(ownerIdsOfRole(Long.parseLong(roleId))).containsExactly(activeOwnerOneId);

        // 替换而不是追加：只剩本次提交的那一个
        assertThat(configureRoleScope(token, roleId, "CUSTOM",
                List.of(String.valueOf(activeOwnerTwoId))).path("code").asInt()).isZero();
        assertThat(ownerIdsOfRole(Long.parseLong(roleId))).containsExactly(activeOwnerTwoId);

        assertThat(configureRoleScope(token, roleId, "ALL", List.of()).path("code").asInt()).isZero();
        assertThat(dataScopeTypeOfRole(Long.parseLong(roleId))).isEqualTo("ALL");
        // ALL 是规则而不是名单：留着旧明细会让「这条明细算不算数」变成要读解析逻辑才能回答的问题
        assertThat(ownerIdsOfRole(Long.parseLong(roleId))).isEmpty();

        JsonNode detail = getJson(token, "/api/console/roles/" + roleId).path("data");
        assertThat(detail.path("dataScopeType").asText()).isEqualTo("ALL");
        assertThat(textsOf(detail.path("ownerIds"))).isEmpty();
    }

    @Test
    @DisplayName("配置角色范围：ALL 带明细被拒绝，库里的类型与明细都不变")
    void shouldRejectOwnerIdsOnAllScope() throws Exception {
        String token = consoleToken();
        String roleId = createdRoleId(createRole(token, "ROLE_SCOPE_ALL_BAD", "规则范围带明细", List.of()));

        JsonNode response = configureRoleScope(token, roleId, "ALL", List.of(String.valueOf(activeOwnerOneId)));

        assertThat(response.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(response.path("message").asText()).contains("ALL");
        assertThat(dataScopeTypeOfRole(Long.parseLong(roleId))).isEqualTo("CUSTOM");
        assertThat(ownerIdsOfRole(Long.parseLong(roleId))).isEmpty();
    }

    @Test
    @DisplayName("配置角色范围：指向不存在或未启用的货主被拒绝")
    void shouldRejectUnavailableOwners() throws Exception {
        String token = consoleToken();
        String roleId = createdRoleId(createRole(token, "ROLE_SCOPE_BAD_OWNER", "非法货主", List.of()));

        JsonNode missing = configureRoleScope(token, roleId, "CUSTOM", List.of("999999"));
        assertThat(missing.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(missing.path("message").asText()).contains("999999");

        // 草稿货主存在但未启用：它不该被写进配置，否则配置指向一个业务上用不了的对象
        JsonNode draft = configureRoleScope(token, roleId, "CUSTOM", List.of(String.valueOf(draftOwnerId)));
        assertThat(draft.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);

        // 已软删除的货主同样不可用：这条穿透到 wms-master 的口径，不能在 auth 里靠猜
        JsonNode deleted = configureRoleScope(token, roleId, "CUSTOM", List.of(String.valueOf(deletedOwnerId)));
        assertThat(deleted.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);

        assertThat(ownerIdsOfRole(Long.parseLong(roleId))).isEmpty();
    }

    @Test
    @DisplayName("配置角色范围：空 CUSTOM 合法，落库为空明细")
    void shouldAcceptEmptyCustomScope() throws Exception {
        String token = consoleToken();
        String roleId = createdRoleId(createRole(token, "ROLE_SCOPE_EMPTY", "空范围角色", List.of()));

        assertThat(configureRoleScope(token, roleId, "CUSTOM", List.of()).path("code").asInt()).isZero();

        assertThat(dataScopeTypeOfRole(Long.parseLong(roleId))).isEqualTo("CUSTOM");
        assertThat(ownerIdsOfRole(Long.parseLong(roleId))).isEmpty();
        JsonNode detail = getJson(token, "/api/console/roles/" + roleId).path("data");
        assertThat(detail.path("dataScopeType").asText()).isEqualTo("CUSTOM");
        assertThat(textsOf(detail.path("ownerIds"))).isEmpty();
    }

    @Test
    @DisplayName("配置角色范围：内置角色被拒绝")
    void shouldRejectBuiltinRoleScope() throws Exception {
        String token = consoleToken();
        String superAdminId = Long.toString(superAdminRole());

        JsonNode response = configureRoleScope(token, superAdminId, "ALL", List.of());

        assertThat(response.path("code").asInt()).isEqualTo(CONFLICT_CODE);
        assertThat(response.path("message").asText()).isEqualTo("内置角色不可配置数据范围");
        // 超管的 ALL 是初始化数据，不能被端点改成别的取值，也不能被重复写
        assertThat(dataScopeTypeOfRole(Long.parseLong(superAdminId))).isEqualTo("ALL");
    }

    @Test
    @DisplayName("配置角色范围：角色不存在返回 404")
    void shouldRejectMissingRole() throws Exception {
        JsonNode response = configureRoleScope(consoleToken(), "999999", "ALL", List.of());

        assertThat(response.path("code").asInt()).isEqualTo(NOT_FOUND_CODE);
        assertThat(response.path("message").asText()).isEqualTo("角色不存在");
    }

    /** 用操作人账号登录取令牌；处于待改密状态时属于测试自身的前提失败。 */
    private String consoleToken() throws Exception {
        return loginToken(OPERATOR_LOGIN_NAME);
    }

    /** 配置角色数据范围。 */
    private JsonNode configureRoleScope(String token, String roleId, String dataScopeType, List<String> ownerIds)
            throws Exception {
        return putJson(token, "/api/console/roles/" + roleId + "/data-scope",
                objectMapper.writeValueAsString(new DataScopePayload(dataScopeType, ownerIds)));
    }

    /** 直接查库取角色的数据范围类型；未配置时为 {@code null}。 */
    private String dataScopeTypeOfRole(long roleId) {
        return jdbcTemplate.queryForObject("SELECT data_scope_type FROM sys_role WHERE id = ?", String.class, roleId);
    }

    /** 直接查库取角色的货主明细，按标识升序。 */
    private List<Long> ownerIdsOfRole(long roleId) {
        return jdbcTemplate.queryForList(
                "SELECT owner_id FROM sys_data_scope_owner WHERE role_id = ? ORDER BY owner_id", Long.class, roleId);
    }

    /** 详情按标识升序返回，因此期望值也排一次序。 */
    private static List<String> sortedTexts(List<Long> values) {
        return values.stream().sorted().map(String::valueOf).toList();
    }

    /** 造一个已软删除的货主：它存在，但不该被任何范围配置引用。 */
    private long seedDeletedOwner(String code, String name, String skuPrefix) {
        long ownerId = seedOwner(code, name, skuPrefix, "ACTIVE");
        jdbcTemplate.update("UPDATE owner SET deleted_at = now() WHERE id = ? AND deleted_at IS NULL", ownerId);
        return ownerId;
    }
}

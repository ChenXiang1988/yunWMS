package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.auth.domain.port.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 货主状态流转、终止影响清单与删除草稿的端到端验证。
 *
 * <p>要证的是单测证不了的几件事：
 * <ul>
 *   <li>五态流转链路在真实库上跑得通，且终止后真的回不去（不是「单测里回不去」）；</li>
 *   <li>影响清单的两个计数来自两张真实的表——{@code sku} 由本模块查、{@code sys_user} 经
 *       {@code wms-app} 的适配器查，且都排除已逻辑删除的行、账号数包含已停用的账号；</li>
 *   <li>软删除之后列表查不到、详情 404，而 {@code code} 与 {@code sku_prefix} 仍被占用
 *       （用一条直接 INSERT 撞唯一索引来证，而不是只看接口表现）；</li>
 *   <li>{@code V036} 的 {@code system:owner:delete} 真的被接口消费：只有其他货主权限点的账号调用删除得 403。</li>
 * </ul>
 *
 * <p>操作人不用种子账号 {@code superadmin}（它带着「需先改密」标记，改它又会污染
 * {@code BootstrapIntegrationTest} 依赖的初始状态），而是用 SQL 造一个挂着内置超管角色的已改密账号：
 * 超管权限在鉴权时动态展开，因此它天然拥有 {@code V033} 与 {@code V036} 的权限点。
 *
 * <p>用例之间不隔离数据（同一个测试 JVM 共用一个库），因此每个用例用自己的 SKU 前缀与业务键，
 * 断言只针对自己造的那几条。
 */
@DisplayName("货主生命周期")
class OwnerLifecycleIntegrationTest extends AbstractIntegrationTest {

    /** 操作人账号：已改密、已启用，挂内置超管角色。 */
    private static final String OPERATOR_LOGIN_NAME = "owner-lifecycle-operator";
    /** 持有货主列表 / 建档 / 编辑权限、但没有删除权限的账号，用于验证删除需要单独权限点。 */
    private static final String NO_DELETE_LOGIN_NAME = "owner-lifecycle-no-delete";
    private static final String PASSWORD = "Admin@1234";
    /** 无删除权限的角色编码，用例自造；迁移里除超管外不预置角色。 */
    private static final String NO_DELETE_ROLE = "TEST_OWNER_NO_DELETE";

    /** 错误码来自 {@code ErrorCode}：40000 参数问题，40300 无权访问，40400 资源不存在，40900 状态冲突，40905 前缀被占用。 */
    private static final int BAD_REQUEST_CODE = 40000;
    private static final int FORBIDDEN_CODE = 40300;
    private static final int NOT_FOUND_CODE = 40400;
    private static final int CONFLICT_CODE = 40900;
    private static final int SKU_PREFIX_TAKEN_CODE = 40905;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @BeforeEach
    void prepareAccounts() {
        Long operatorId = seedAccount(OPERATOR_LOGIN_NAME, "生命周期操作员", "owner-lifecycle@example.com");
        Long noDeleteId = seedAccount(NO_DELETE_LOGIN_NAME, "无删除权限", "owner-lifecycle-nodelete@example.com");
        link(operatorId, seedRole("SUPER_ADMIN", "超级管理员"));

        Long noDeleteRoleId = seedRole(NO_DELETE_ROLE, "测试用货主无删除权限角色");
        link(noDeleteId, noDeleteRoleId);
        // 数据范围给 ALL：本类验证的是「缺哪个权限点就不行」，不是数据范围。角色默认的 CUSTOM
        // 空明细按规格等价于「无权限」，会把下面的「能读到这条货主」变成 404，测到的就不是权限点了
        jdbcTemplate.update("UPDATE sys_role SET data_scope_type = 'ALL' WHERE id = ?", noDeleteRoleId);
        // 只放行列表 / 建档 / 编辑三个点：删除单独成点（V036），这里要验证的是「缺它就不行」
        jdbcTemplate.update("""
                INSERT INTO sys_role_menu (role_id, menu_id)
                SELECT ?, id FROM sys_menu
                WHERE perms IN ('system:owner:list', 'system:owner:create', 'system:owner:update')
                ON CONFLICT (role_id, menu_id) DO NOTHING
                """, noDeleteRoleId);
    }

    @Test
    @DisplayName("全链路：草稿 → 合作中 → 暂停 → 合作中 → 终止（带确认），终止后不可恢复")
    void shouldWalkThroughFullLifecycle() throws Exception {
        String token = consoleToken();
        JsonNode created = createOwner(token, ownerBody("生命周期甲LFCX2", "LCA2", "DRAFT"));
        assertThat(created.path("code").asInt()).as("建档失败：%s", created).isZero();
        String ownerId = created.path("data").path("id").asText();
        assertThat(statusOf(token, ownerId)).as("保存草稿入口必须落 DRAFT").isEqualTo("DRAFT");

        // 草稿提交为合作中：规格「草稿可提交为潜在或合作中」
        assertThat(changeStatus(token, ownerId, "ACTIVE", false).path("code").asInt()).isZero();
        assertThat(statusOf(token, ownerId)).isEqualTo("ACTIVE");

        // 合作中 → 暂停，历史数据仍可查（详情照常 200）
        assertThat(changeStatus(token, ownerId, "SUSPENDED", false).path("code").asInt()).isZero();
        assertThat(statusOf(token, ownerId)).isEqualTo("SUSPENDED");

        // 暂停 → 合作中
        assertThat(changeStatus(token, ownerId, "ACTIVE", false).path("code").asInt()).isZero();
        assertThat(statusOf(token, ownerId)).isEqualTo("ACTIVE");

        // 终止未确认：拒绝，且提示里带真实的商品数与账号数（这条货主两样都没有）
        JsonNode notAcknowledged = changeStatus(token, ownerId, "TERMINATED", null);
        assertThat(notAcknowledged.path("code").asInt())
                .as("终止未确认影响清单必须被拒：%s", notAcknowledged)
                .isEqualTo(CONFLICT_CODE);
        assertThat(notAcknowledged.path("message").asText())
                .contains("终止将影响 0 个商品与 0 个账号")
                .contains("请先确认影响清单");
        assertThat(statusOf(token, ownerId)).as("被拒绝的终止不得落库").isEqualTo("ACTIVE");

        // 确认后终止
        assertThat(changeStatus(token, ownerId, "TERMINATED", true).path("code").asInt()).isZero();
        assertThat(statusOf(token, ownerId)).isEqualTo("TERMINATED");

        // 终止后改回合作中：拒绝，提示里说清当前状态与「终态」
        JsonNode revive = changeStatus(token, ownerId, "ACTIVE", true);
        assertThat(revive.path("code").asInt()).as("终止后不可恢复：%s", revive).isEqualTo(CONFLICT_CODE);
        assertThat(revive.path("message").asText())
                .contains("TERMINATED")
                .contains("ACTIVE")
                .contains("终态");
        assertThat(statusOf(token, ownerId)).as("被拒绝的流转不得落库").isEqualTo("TERMINATED");

        // 重复提交终止：幂等成功，且不要求确认（幂等重放没有新的后果要确认）
        assertThat(changeStatus(token, ownerId, "TERMINATED", null).path("code").asInt())
                .as("同状态提交按成功处理")
                .isZero();
        assertThat(statusOf(token, ownerId)).isEqualTo("TERMINATED");
    }

    @Test
    @DisplayName("流转参数：目标状态未知、不允许的流向各有明确错误码与提示")
    void shouldRejectIllegalTransitionsAndUnknownStatus() throws Exception {
        String token = consoleToken();
        JsonNode created = createOwner(token, ownerBody("生命周期乙LFCX3", "LCA3", "ACTIVE"));
        assertThat(created.path("code").asInt()).as("建档失败：%s", created).isZero();
        String ownerId = created.path("data").path("id").asText();

        JsonNode unknown = changeStatus(token, ownerId, "FOO", false);
        assertThat(unknown.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(unknown.path("message").asText()).as("提示里要带出实际提交的值").contains("FOO");

        // 合作中 → 草稿 不在流转表里
        JsonNode illegal = changeStatus(token, ownerId, "DRAFT", false);
        assertThat(illegal.path("code").asInt()).isEqualTo(CONFLICT_CODE);
        assertThat(illegal.path("message").asText())
                .contains("ACTIVE")
                .contains("DRAFT")
                .as("提示里必须列出允许的目标状态")
                .contains("SUSPENDED")
                .contains("TERMINATED");

        // 货主不存在：40400，而不是「目标状态不允许」
        JsonNode missing = changeStatus(token, "999999", "ACTIVE", false);
        assertThat(missing.path("code").asInt()).isEqualTo(NOT_FOUND_CODE);
        assertThat(missing.path("message").asText()).isEqualTo("货主不存在");
    }

    @Test
    @DisplayName("影响清单：账号数含停用账号、排除已删除账号；商品数按未删除的 sku 计")
    void shouldReportRealTerminationImpact() throws Exception {
        String token = consoleToken();
        JsonNode created = createOwner(token, ownerBody("影响清单丙LFCX4", "LCA4", "ACTIVE"));
        assertThat(created.path("code").asInt()).as("建档失败：%s", created).isZero();
        String ownerId = created.path("data").path("id").asText();
        long ownerIdValue = Long.parseLong(ownerId);

        // 造 2 个仍存在的账号（其中一个是停用状态）与 1 个已删除的账号：
        // 停用的账号仍然存在，必须计入；已删除的账号按口径排除
        long activeAccountId = seedOwnerAccount(ownerIdValue, "lfc-owner-acct-a", "ACTIVE");
        seedOwnerAccount(ownerIdValue, "lfc-owner-acct-b", "DISABLED");
        long deletedAccountId = seedOwnerAccount(ownerIdValue, "lfc-owner-acct-c", "ACTIVE");
        jdbcTemplate.update("UPDATE sys_user SET deleted_at = now() WHERE id = ?", deletedAccountId);

        // 造 1 件仍存在、1 件已删除的商品：sku 属主数据域，本批没有商品接口，直接按表结构写入
        insertSku(ownerIdValue, "LCA4000001", "影响清单商品甲");
        long deletedSkuId = insertSku(ownerIdValue, "LCA4000002", "影响清单商品乙");
        jdbcTemplate.update("UPDATE sku SET deleted_at = now() WHERE id = ?", deletedSkuId);

        JsonNode impact = getJson(token, "/api/console/owners/" + ownerId + "/termination-impact");
        assertThat(impact.path("code").asInt()).as("取影响清单失败：%s", impact).isZero();
        assertThat(impact.path("data").path("skuCount").asLong()).as("商品数排除已删除的 sku").isEqualTo(1L);
        assertThat(impact.path("data").path("accountCount").asLong())
                .as("账号数含停用账号、排除已删除账号")
                .isEqualTo(2L);

        // 未确认终止时提示里的数字必须与影响清单一致：两处口径不同会让人无法据此判断
        JsonNode rejected = changeStatus(token, ownerId, "TERMINATED", false);
        assertThat(rejected.path("message").asText()).contains("终止将影响 1 个商品与 2 个账号");

        // 终止只改货主状态：商品与账号都保持原样（规格明令不得自动修改商品档案状态）
        assertThat(countRows("SELECT count(*) FROM sku WHERE owner_id = ? AND deleted_at IS NULL", ownerIdValue))
                .as("终止不得删除或改动商品")
                .isEqualTo(1);
        assertThat(countRows("SELECT count(*) FROM sys_user WHERE owner_id = ? AND deleted_at IS NULL", ownerIdValue))
                .as("终止不得停用或删除账号")
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sys_user WHERE id = ?", String.class, activeAccountId))
                .as("终止不得改动账号状态")
                .isEqualTo("ACTIVE");

        // 货主不存在：40400
        JsonNode missing = getJson(token, "/api/console/owners/999999/termination-impact");
        assertThat(missing.path("code").asInt()).isEqualTo(NOT_FOUND_CODE);
    }

    @Test
    @DisplayName("删除草稿：列表查不到、详情 404、重复删除 404、编码与前缀仍被占用")
    void shouldDeleteDraftAndKeepCodeAndPrefixOccupied() throws Exception {
        String token = consoleToken();
        String prefix = "LCA5";
        JsonNode created = createOwner(token, ownerBody("待删草稿丁LFCX5", prefix, "DRAFT"));
        assertThat(created.path("code").asInt()).as("建档失败：%s", created).isZero();
        String ownerId = created.path("data").path("id").asText();
        String ownerCode = created.path("data").path("code").asText();

        // 非草稿不可删：先把它提交为合作中，再试一次
        JsonNode activeOwner = createOwner(token, ownerBody("待删合作中戊LFCX5", "LCA6", "ACTIVE"));
        assertThat(activeOwner.path("code").asInt()).isZero();
        String activeOwnerId = activeOwner.path("data").path("id").asText();
        JsonNode rejected = deleteOwner(token, activeOwnerId);
        assertThat(rejected.path("code").asInt()).isEqualTo(CONFLICT_CODE);
        assertThat(rejected.path("message").asText())
                .contains("ACTIVE")
                .contains("草稿")
                .contains("终止");
        assertThat(getJson(token, "/api/console/owners/" + activeOwnerId).path("code").asInt())
                .as("被拒绝的删除不得落库")
                .isZero();

        // 草稿删除成功
        assertThat(deleteOwner(token, ownerId).path("code").asInt()).isZero();

        // 软删除：行还在，deleted_at 已置。物理删除会让编码与前缀被释放，与本用例下一条断言冲突
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM owner WHERE id = ?", Boolean.class, Long.parseLong(ownerId)))
                .as("删除必须是软删除（置 deleted_at），不是物理删除")
                .isTrue();

        // 列表查不到、详情 404
        JsonNode listed = getJson(token, "/api/console/owners?keyword=" + ownerCode).path("data");
        assertThat(listed.path("total").asInt()).as("已删除的货主不得出现在列表里").isZero();
        assertThat(getJson(token, "/api/console/owners/" + ownerId).path("code").asInt())
                .as("已删除的货主与「不存在」同口径")
                .isEqualTo(NOT_FOUND_CODE);

        // 重复删除：40400（与详情同口径），不二次写入，也不把记录写活
        assertThat(deleteOwner(token, ownerId).path("code").asInt()).isEqualTo(NOT_FOUND_CODE);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM owner WHERE id = ?", Boolean.class, Long.parseLong(ownerId)))
                .as("重复删除不得把记录写回未删除")
                .isTrue();

        // 编码仍被占用：ux_owner_code 是全表唯一索引，直接 INSERT 同一编码必须撞索引
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO owner (code, name, sku_prefix, owner_type, settlement_entity, country, city, address_line)
                VALUES (?, '复用编码用例', 'LCA7', 'BRAND', '复用编码结算主体', 'CN', '深圳', '科技园路 9 号')
                """, ownerCode))
                .as("软删除不释放系统编码——编码永不复用")
                .isInstanceOf(DataIntegrityViolationException.class);

        // 前缀仍被占用：接口提示里还会指出占用方（那条已删除的货主）。
        // 外部码刻意换一个：两次撞唯一索引时 PostgreSQL 只报其中一条，撞两条会让这条用例
        // 变成「看数据库先报哪个」，而这里要验的是前缀
        JsonNode prefixConflict = createOwner(token,
                ownerBody("抢已删前缀己LFCX5", prefix, "DRAFT", "EXT-DELETED-PREFIX"));
        assertThat(prefixConflict.path("code").asInt()).isEqualTo(SKU_PREFIX_TAKEN_CODE);
        assertThat(prefixConflict.path("message").asText())
                .contains(prefix)
                .contains("（占用方 " + ownerCode + "）");
    }

    @Test
    @DisplayName("无 system:owner:delete 的账号调删除：HTTP 403，货主保持原样")
    void shouldRejectDeletionWithoutPermission() throws Exception {
        String token = consoleToken();
        JsonNode created = createOwner(token, ownerBody("无删除权限庚LFCX6", "LCA8", "DRAFT"));
        assertThat(created.path("code").asInt()).as("建档失败：%s", created).isZero();
        String ownerId = created.path("data").path("id").asText();

        String noDeleteToken = login(NO_DELETE_LOGIN_NAME).path("data").path("token").asText();

        // 先证明这个账号是能进这条路线的：详情需要 system:owner:list，它持有该点
        assertThat(getJson(noDeleteToken, "/api/console/owners/" + ownerId).path("code").asInt()).isZero();

        mockMvc.perform(delete("/api/console/owners/" + ownerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + noDeleteToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        // 403 之外还要证明业务逻辑没有执行：只有「先拦后执行」才叫未授权接口不执行业务逻辑
        assertThat(getJson(token, "/api/console/owners/" + ownerId).path("code").asInt())
                .as("被拒绝的删除不得落库")
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NULL FROM owner WHERE id = ?", Boolean.class, Long.parseLong(ownerId)))
                .as("被拒绝的删除不得写入 deleted_at")
                .isTrue();
    }

    /** 用操作人账号登录取令牌；未改密时这里会拿不到可用令牌，属于测试自身的前提失败。 */
    private String consoleToken() throws Exception {
        JsonNode login = login(OPERATOR_LOGIN_NAME);
        assertThat(login.path("code").asInt()).as("操作人账号登录失败：%s", login).isZero();
        assertThat(login.path("data").path("mustChangePassword").asBoolean())
                .as("操作人账号不应处于待改密状态")
                .isFalse();
        return login.path("data").path("token").asText();
    }

    private JsonNode login(String loginName) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginPayload(loginName, PASSWORD));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createOwner(String token, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/console/owners")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * 调状态流转接口。
     *
     * @param token              操作人令牌
     * @param ownerId            货主标识
     * @param targetStatus       目标状态编码
     * @param acknowledgedImpact 是否确认影响清单；{@code null} 表示干脆不传这个字段
     * @return 接口响应
     */
    private JsonNode changeStatus(String token, String ownerId, String targetStatus, Boolean acknowledgedImpact)
            throws Exception {
        String body = acknowledgedImpact == null
                ? objectMapper.writeValueAsString(new StatusPayload(targetStatus, null))
                : objectMapper.writeValueAsString(new StatusPayload(targetStatus, acknowledgedImpact));
        MvcResult result = mockMvc.perform(put("/api/console/owners/" + ownerId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode deleteOwner(String token, String ownerId) throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/console/owners/" + ownerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String token, String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** 取详情里的当前状态；详情拿不到时失败信息里带出响应，便于定位是权限还是数据问题。 */
    private String statusOf(String token, String ownerId) throws Exception {
        JsonNode detail = getJson(token, "/api/console/owners/" + ownerId);
        assertThat(detail.path("code").asInt()).as("取详情失败：%s", detail).isZero();
        return detail.path("data").path("status").asText();
    }

    /**
     * 构造一份能通过字典校验的建档请求体：客户类型 BRAND、国家 CN、州省 CN-GD 都是种子里的启用项，
     * 大陆省份要求邮编，因此 POSTAL_CODE 必填。
     *
     * @param name          客户名称，用例自身保证唯一性标记落在里面
     * @param skuPrefix     SKU 前缀，4 位大写字母或数字且不含 0/O/1/I
     * @param initialStatus 初始状态
     * @return JSON 请求体
     */
    private String ownerBody(String name, String skuPrefix, String initialStatus) throws Exception {
        return ownerBody(name, skuPrefix, initialStatus, "EXT-" + skuPrefix);
    }

    /**
     * 同上，但外部码由调用方给出。
     *
     * <p>需要一个单独的重载，是因为有的用例要故意撞 SKU 前缀。外部码若也跟着前缀走就会同时撞两个索引，
     * 而 PostgreSQL 只报其中一条——那时的断言验的是「数据库先发现哪条」，不是业务规则。
     *
     * @param name          客户名称
     * @param skuPrefix     SKU 前缀
     * @param initialStatus 初始状态
     * @param externalCode  外部码
     * @return JSON 请求体
     */
    private String ownerBody(String name, String skuPrefix, String initialStatus, String externalCode)
            throws Exception {
        return objectMapper.writeValueAsString(new CreateOwnerPayload(
                name,
                "生命周期简称",
                "Lifecycle Test Owner",
                externalCode,
                skuPrefix,
                "BRAND",
                "生命周期测试结算主体",
                "CN",
                "CN-GD",
                "深圳",
                "南山区",
                "518000",
                "科技园路 1 号",
                "生命周期测试备注",
                initialStatus));
    }

    /**
     * 造一个货主账号。
     *
     * <p>本批没有开主账号的接口（属 3.3），但影响清单要数 {@code sys_user}，因此直接按表结构写入。
     * 货主账号不要求邮箱（{@code ck_user_email_required} 只约束内部员工）。
     *
     * @param ownerId   所属货主标识
     * @param loginName 登录名，用例自身保证唯一
     * @param status    账号状态：{@code ACTIVE} 或 {@code DISABLED}
     * @return 账号标识
     */
    private long seedOwnerAccount(long ownerId, String loginName, String status) {
        jdbcTemplate.update("""
                INSERT INTO sys_user (login_name, display_name, subject_type, owner_id, password_hash,
                                      must_change_password, status)
                SELECT ?, ?, 'OWNER', ?, ?, false, ?
                WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE lower(login_name) = lower(?))
                """, loginName, loginName, ownerId, passwordHasher.hash(PASSWORD), status, loginName);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE lower(login_name) = lower(?)", Long.class, loginName);
    }

    /**
     * 按表结构写一件商品。
     *
     * <p>商品接口属 {@code implement-sku}，本批只能直接写库；要证明商品计数不是恒为 0，这是唯一的路。
     *
     * @param ownerId      所属货主标识
     * @param skuId        十位业务编码，四位前缀 + 六位流水
     * @param name         商品名称
     * @return 商品标识
     */
    private long insertSku(long ownerId, String skuId, String name) {
        jdbcTemplate.update("""
                INSERT INTO sku (sku_id, owner_id, owner_sku_code, name, uom)
                VALUES (?, ?, ?, ?, 'PCS')
                """, skuId, ownerId, "OSC-" + skuId, name);
        return jdbcTemplate.queryForObject("SELECT id FROM sku WHERE sku_id = ?", Long.class, skuId);
    }

    private int countRows(String sql, long value) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, value);
        return count == null ? 0 : count;
    }

    /**
     * 按编码取角色标识；不存在时按给定名称创建。
     *
     * <p>用 {@code WHERE NOT EXISTS} 而不是 {@code ON CONFLICT}：超管角色由迁移建立，
     * 这里的目标是「不存在就补一个」，而 {@code ON CONFLICT} 覆盖不了「一行都没有」的情形。
     */
    private Long seedRole(String code, String name) {
        jdbcTemplate.update("""
                INSERT INTO sys_role (code, name)
                SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = ?)
                """, code, name, code);
        return jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE code = ?", Long.class, code);
    }

    /** 建一个已改密的启用账号并返回其标识；已存在时只重置密码，保持用例可重复执行。 */
    private Long seedAccount(String loginName, String displayName, String email) {
        String passwordHash = passwordHasher.hash(PASSWORD);
        jdbcTemplate.update("""
                INSERT INTO sys_user (login_name, display_name, subject_type, password_hash,
                                      must_change_password, status, email)
                SELECT ?, ?, 'STAFF', ?, false, 'ACTIVE', ?
                WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE lower(login_name) = lower(?))
                """, loginName, displayName, passwordHash, email, loginName);
        jdbcTemplate.update("""
                UPDATE sys_user SET password_hash = ?, must_change_password = false, status = 'ACTIVE'
                WHERE lower(login_name) = lower(?)
                """, passwordHash, loginName);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE lower(login_name) = lower(?)", Long.class, loginName);
    }

    /** 幂等写入用户角色关联；主键是 (user_id, role_id)，判重交给数据库。 */
    private void link(Long userId, Long roleId) {
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)
                ON CONFLICT (user_id, role_id) DO NOTHING
                """, userId, roleId);
    }

    /**
     * 建档请求体的最小映射，理由同 {@code OwnerQueryIntegrationTest}：按 HTTP 上的 JSON 验证，
     * 复用生产侧的 Request 类型会掩盖字段名被改而测试仍然通过的情况。
     */
    private record CreateOwnerPayload(
            String name,
            String shortName,
            String nameEn,
            String externalCode,
            String skuPrefix,
            String ownerType,
            String settlementEntity,
            String country,
            String province,
            String city,
            String district,
            String postalCode,
            String addressLine,
            String remark,
            String initialStatus) {
    }

    /**
     * 状态流转请求体的最小映射。
     *
     * @param targetStatus       目标状态编码
     * @param acknowledgedImpact 是否确认影响清单；{@code null} 表示不传该字段
     */
    private record StatusPayload(String targetStatus, Boolean acknowledgedImpact) {
    }

    /**
     * 登录请求体的最小映射。
     *
     * @param loginName 登录名
     * @param password  明文密码
     */
    private record LoginPayload(String loginName, String password) {
    }
}

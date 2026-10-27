package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.auth.domain.port.PasswordHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据范围相关集成测试的共用基类：容器与 MockMvc 来自 {@link AbstractIntegrationTest}，
 * 这里补造数（账号、角色、货主、负责人）与请求编组（登录、建角色）。
 *
 * <p>货主列表、详情与终止影响清单的调用 helper 在 {@link AbstractOwnerDataScopeIntegrationTest}：
 * 只有过滤用例用得上，放在这里会让配置用例读到一个与它无关的接口契约。
 *
 * <p>为什么抽出基类：配置侧与过滤侧曾各复制一份同样的脚本，而且已经分叉——同一个 {@code seedRole}
 * 在两处含义相反（一处是角色名，一处是范围类型）。这里拆成 {@link #seedRole}（只保证角色存在）
 * 与 {@link #seedScopedRole}（连范围与权限点一起造），避免造数含义混进被测断言。
 *
 * <p>造数一律直接写 SQL，不走接口：接口正是被测对象，用它准备数据会让「接口坏了」表现为一堆
 * 无关用例失败。请求体也不复用生产侧请求类型——同一个类序列化再反序列化会掩盖字段名被改。
 *
 * <p>整个测试 JVM 共用一个库：造数按业务键判重后复用，每个用例须自带唯一编码、登录名与关键字标记。
 */
abstract class AbstractDataScopeIntegrationTest extends AbstractIntegrationTest {

    /** 测试账号统一密码；密码强度规则由生产侧单测覆盖，这里只需要能登进来。 */
    protected static final String PASSWORD = "Admin@1234";

    protected static final int BAD_REQUEST_CODE = 40000;
    protected static final int FORBIDDEN_CODE = 40300;
    protected static final int NOT_FOUND_CODE = 40400;
    protected static final int CONFLICT_CODE = 40900;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected PasswordHasher passwordHasher;

    /** 登录并返回原始响应；登录失败即断言失败。 */
    protected JsonNode login(String loginName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(loginName, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("code").asInt()).as("登录失败：%s", response).isZero();
        return response;
    }

    /** 登录并取出令牌；待改密账号会被拦到改密页，拿它测业务接口测到的是拦截器，因此在这里判失败。 */
    protected String loginToken(String loginName) throws Exception {
        JsonNode login = login(loginName);
        assertThat(login.path("data").path("mustChangePassword").asBoolean())
                .as("测试账号不应处于待改密状态")
                .isFalse();
        return login.path("data").path("token").asText();
    }

    /** 以令牌发 POST 并返回响应；非 200 由 MockMvc 断言失败。 */
    protected JsonNode postJson(String token, String path, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** 以令牌发 PUT 并返回响应；非 200 由 MockMvc 断言失败。 */
    protected JsonNode putJson(String token, String path, String body) throws Exception {
        MvcResult result = mockMvc.perform(put(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** 以令牌发 GET 并返回响应；非 200 由 MockMvc 断言失败。 */
    protected JsonNode getJson(String token, String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** 建角色；{@code dataScopeType} 与 {@code ownerIds} 为 {@code null} 时等于省略这两个字段。 */
    protected JsonNode createRole(String token, String code, String name, List<String> menuIds,
                                  String dataScopeType, List<String> ownerIds) throws Exception {
        String body = objectMapper.writeValueAsString(
                new CreateRolePayload(code, name, null, menuIds, dataScopeType, ownerIds));
        return postJson(token, "/api/console/roles", body);
    }

    /** 建角色，省略数据范围字段。 */
    protected JsonNode createRole(String token, String code, String name, List<String> menuIds) throws Exception {
        return createRole(token, code, name, menuIds, null, null);
    }

    /** 从建角色响应里取角色标识，顺带断言这次创建成功。 */
    protected static String createdRoleId(JsonNode createdRole) {
        assertThat(createdRole.path("code").asInt()).as("建角色失败：%s", createdRole).isZero();
        return createdRole.path("data").path("id").asText();
    }

    /** 详情里的标识按契约以字符串返回，断言前统一成字符串列表。 */
    protected static List<String> textsOf(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    /**
     * 造一个指定状态的货主并返回标识。
     *
     * <p>编码与 SKU 前缀都是全表唯一索引，用例重复执行时不该撞索引，因此已存在就复用；前缀须避开
     * {@code 0/O/1/I}，库约束只要求 {@code [A-Z0-9]{4}}，但种子数据要能被 {@code SkuPrefix} 读回。
     */
    protected long seedOwner(String code, String name, String skuPrefix, String status) {
        jdbcTemplate.update("""
                INSERT INTO owner (code, name, sku_prefix, owner_type, settlement_entity, country, province, city,
                                   district, postal_code, address_line, status)
                SELECT ?, ?, ?, 'THIRD_PARTY', '集成测试结算主体', 'CN', 'CN-GD', '深圳', '南山区', '518000',
                       '测试路 1 号', ?
                WHERE NOT EXISTS (SELECT 1 FROM owner WHERE code = ?)
                """, code, name, skuPrefix, status, code);
        return jdbcTemplate.queryForObject("SELECT id FROM owner WHERE code = ?", Long.class, code);
    }

    /**
     * 按编码取角色标识，不存在时创建；只保证角色存在，不碰范围与状态。
     *
     * <p>用 {@code WHERE NOT EXISTS} 而不是 {@code ON CONFLICT}：目标是「不存在就补一个」，
     * 而 {@code ON CONFLICT} 覆盖不了「一行都没有」的情形。
     */
    protected long seedRole(String code, String name) {
        jdbcTemplate.update("""
                INSERT INTO sys_role (code, name) SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = ?)
                """, code, name, code);
        return jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE code = ?", Long.class, code);
    }

    /**
     * 建一个带 {@code system:owner:list} 权限点、启用中、指定数据范围类型的角色。
     *
     * <p>只授这一个权限点：过滤用例验证的是数据范围，不授会让用例拿到 403，测到的就不是范围了。
     * 范围、状态与明细每次显式归位并整批替换，否则上一次运行留下的停用状态或残留明细会让
     * 「之前/之后」的对比不成立。
     */
    protected long seedScopedRole(String code, String scopeType, long... ownerIds) {
        long roleId = seedRole(code, "范围过滤角色 " + code);
        jdbcTemplate.update("UPDATE sys_role SET data_scope_type = ?, status = 'ACTIVE' WHERE id = ?",
                scopeType, roleId);
        jdbcTemplate.update("""
                INSERT INTO sys_role_menu (role_id, menu_id) SELECT ?, id FROM sys_menu
                WHERE perms = 'system:owner:list' ON CONFLICT (role_id, menu_id) DO NOTHING
                """, roleId);
        jdbcTemplate.update("DELETE FROM sys_data_scope_owner WHERE role_id = ?", roleId);
        for (long ownerId : ownerIds) {
            jdbcTemplate.update("INSERT INTO sys_data_scope_owner (role_id, owner_id) VALUES (?, ?)",
                    roleId, ownerId);
        }
        return roleId;
    }

    /** 内置超管角色的标识：权限在鉴权时动态展开，范围是迁移里的初始数据 {@code ALL}。 */
    protected long superAdminRole() {
        return jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE code = 'SUPER_ADMIN'", Long.class);
    }

    /** 建一个已改密的启用员工账号，邮箱由登录名派生。 */
    protected long seedStaff(String loginName) {
        return seedStaff(loginName, "范围过滤员工");
    }

    /** 建一个已改密的启用员工账号。 */
    protected long seedStaff(String loginName, String displayName) {
        return seedAccount(loginName, displayName, "STAFF", null, loginName + "@example.com");
    }

    /** 建一个绑定指定货主的货主账号：它的范围由所属货主固定，不接受覆盖配置。 */
    protected long seedOwnerAccount(String loginName, String displayName, long ownerId) {
        return seedAccount(loginName, displayName, "OWNER", ownerId, null);
    }

    /**
     * 建号或重置测试账号。
     *
     * <p>插入之后再无条件改写密码与状态：用例之间共用一个库，上一次运行留下的旧密码或停用状态
     * 会让本次的登录断言不成立。邮箱只给员工（{@code ck_user_email_required}），货主账号留空。
     */
    private long seedAccount(String loginName, String displayName, String subjectType, Long ownerId, String email) {
        String passwordHash = passwordHasher.hash(PASSWORD);
        jdbcTemplate.update("""
                INSERT INTO sys_user (login_name, display_name, subject_type, owner_id, password_hash,
                                      must_change_password, status, email)
                SELECT ?, ?, ?, ?, ?, false, 'ACTIVE', ?
                WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE lower(login_name) = lower(?))
                """, loginName, displayName, subjectType, ownerId, passwordHash, email, loginName);
        jdbcTemplate.update("""
                UPDATE sys_user SET password_hash = ?, must_change_password = false, status = 'ACTIVE'
                WHERE lower(login_name) = lower(?)
                """, passwordHash, loginName);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE lower(login_name) = lower(?)", Long.class, loginName);
    }

    /** 幂等写入用户角色关联；主键是 (user_id, role_id)，判重交给数据库。 */
    protected void link(long userId, long roleId) {
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?) ON CONFLICT (user_id, role_id) DO NOTHING
                """, userId, roleId);
    }

    /** 写用户级覆盖，整批替换明细。 */
    protected void setUserOverride(long userId, String scopeType, long... ownerIds) {
        jdbcTemplate.update("UPDATE sys_user SET data_scope_type = ? WHERE id = ?", scopeType, userId);
        jdbcTemplate.update("DELETE FROM sys_data_scope_owner WHERE user_id = ?", userId);
        for (long ownerId : ownerIds) {
            jdbcTemplate.update("INSERT INTO sys_data_scope_owner (user_id, owner_id) VALUES (?, ?)",
                    userId, ownerId);
        }
    }

    /** 清除用户级覆盖：类型写 {@code NULL}，明细删除。 */
    protected void clearUserOverride(long userId) {
        jdbcTemplate.update("UPDATE sys_user SET data_scope_type = NULL WHERE id = ?", userId);
        jdbcTemplate.update("DELETE FROM sys_data_scope_owner WHERE user_id = ?", userId);
    }

    /**
     * 登记负责人关系。
     *
     * <p>先删后插而不是 {@code ON CONFLICT DO NOTHING}：一个货主只能有一个主负责人（唯一索引），
     * 用例要求「本次的主负责人就是它」，重复执行时不能留下上一次的那个人。
     */
    protected void assign(long ownerId, long userId) {
        jdbcTemplate.update("DELETE FROM owner_assignee WHERE owner_id = ? AND relation_type = 'PRIMARY'", ownerId);
        jdbcTemplate.update("INSERT INTO owner_assignee (owner_id, user_id, relation_type) VALUES (?, ?, 'PRIMARY')",
                ownerId, userId);
    }

    /** 移除某人对某货主的负责人关系。 */
    protected void unassign(long ownerId, long userId) {
        jdbcTemplate.update("DELETE FROM owner_assignee WHERE owner_id = ? AND user_id = ?", ownerId, userId);
    }

    /** 改角色状态：{@code ACTIVE} 或 {@code DISABLED}。 */
    protected void setRoleStatus(long roleId, String status) {
        jdbcTemplate.update("UPDATE sys_role SET status = ? WHERE id = ?", status, roleId);
    }

    /** 建角色请求体的最小映射，按 HTTP 上的 JSON 字段名验证契约。 */
    protected record CreateRolePayload(String code, String name, String remark, List<String> menuIds,
                                       String dataScopeType, List<String> ownerIds) {
    }

    /** 数据范围配置请求体的最小映射，角色与用户端点共用。 */
    protected record DataScopePayload(String dataScopeType, List<String> ownerIds) {
    }

    /** 登录请求体的最小映射。 */
    protected record LoginPayload(String loginName, String password) {
    }
}

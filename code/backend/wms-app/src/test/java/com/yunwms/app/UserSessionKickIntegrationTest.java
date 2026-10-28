package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.auth.domain.port.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 踢下线接口的端到端验证。
 *
 * <p>要证的是单测证不了的四件事：{@code V041} 的权限点真的被接口消费；同一账号的<b>每一张</b>令牌
 * 都立刻失效（只失效正在用的那一张等于没踢）；踢下线不改账号状态，被踢的人重新登录即可（与「停用」
 * 是两件事）；审计里留下一条「谁把谁踢下线」的记录。
 *
 * <p>操作人用「挂内置超管角色的已改密账号」而不是种子账号 {@code superadmin}：后者带着「需先改密」
 * 标记，改它又会污染 {@code BootstrapIntegrationTest} 依赖的初始状态（测试类共用一个库）。
 */
@DisplayName("踢下线")
class UserSessionKickIntegrationTest extends AbstractIntegrationTest {

    /** 操作人：已改密启用、挂内置超管角色，因此拥有 {@code V041} 新加的权限点。 */
    private static final String OPERATOR_LOGIN_NAME = "usk-operator";
    /** 只有 {@code system:user:update} 的账号，用于证明踢下线必须单独授权。 */
    private static final String UPDATER_LOGIN_NAME = "usk-updater";
    private static final String PASSWORD = "Admin@1234";
    private static final String UPDATER_ROLE = "USK_UPDATER";
    private static final String READER_ROLE = "USK_READER";

    /** 错误码来自 {@code ErrorCode}：40100 未登录或登录已失效，40300 无权访问，40400 不存在。 */
    private static final int UNAUTHORIZED_CODE = 40100;
    private static final int FORBIDDEN_CODE = 40300;
    private static final int NOT_FOUND_CODE = 40400;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @BeforeEach
    void prepareAccounts() {
        link(seedAccount(OPERATOR_LOGIN_NAME, "踢下线操作员", "usk-operator@example.com"),
                seedRole("SUPER_ADMIN", "超级管理员"));
        // 刻意只给编辑权限点：踢下线若与编辑共用授权，下面那条 403 用例会通过，本批的取舍就落空了
        link(seedAccount(UPDATER_LOGIN_NAME, "只有编辑权限", "usk-updater@example.com"),
                seedRole(UPDATER_ROLE, "只有编辑权限的角色", menuId("system:user:update")));
    }

    @Test
    @DisplayName("V041：权限点挂在「用户管理」菜单下，与同级 F 节点不撞排序号")
    void shouldSeedKickPermissionUnderUserMenu() {
        Integer matched = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM sys_menu f
                         JOIN sys_menu c ON c.id = f.parent_id
                WHERE f.menu_type = 'F' AND f.perms = 'system:user:kick' AND f.name = '踢下线'
                  AND c.path = '/system/users'
                  AND (SELECT count(*) FROM sys_menu s
                       WHERE s.parent_id = f.parent_id AND s.menu_type = 'F' AND s.sort_no = f.sort_no) = 1
                """, Integer.class);
        // 挂错父节点的权限点在配置页里根本勾不到；撞号不报错，只会让两个按钮的先后变成偶然
        assertThat(matched).isOne();
    }

    @Test
    @DisplayName("踢下线：同一账号的两张令牌都立刻 401，重新登录后仍可正常使用")
    void shouldInvalidateEverySessionOfTarget() throws Exception {
        String token = consoleToken();
        String targetId = seedReader("usk-target", "被踢用户", "usk-target@example.com");
        String tokenA = loginToken("usk-target");
        String tokenB = loginToken("usk-target");
        // 两次登录必须拿到两张不同的令牌，否则「全部会话」这条断言会空转
        assertThat(tokenA).isNotEqualTo(tokenB);
        // 前置：两张令牌各自可用，否则后面的 401 证明不了是「踢下线」让它们失效
        assertThat(getJson(tokenA, "/api/console/users").path("code").asInt()).isZero();
        assertThat(getJson(tokenB, "/api/console/users").path("code").asInt()).isZero();

        JsonNode kicked = deleteJson(token, "/api/console/users/" + targetId + "/sessions");
        assertThat(kicked.path("code").asInt()).as("踢下线失败：%s", kicked).isZero();

        // 全部会话：不是只踢掉「旧的那一张」，否则被踢人刷新一下页面就又回来了
        assertUnauthorized(tokenA);
        assertUnauthorized(tokenB);

        // 踢下线不是停用：账号状态没动，重新登录立刻又能用
        assertThat(getJson(loginToken("usk-target"), "/api/console/users").path("code").asInt()).isZero();
    }

    @Test
    @DisplayName("踢自己：返回成功，自己的令牌随即 401；审计记下动作与操作人")
    void shouldAllowKickingSelfAndRecordAudit() throws Exception {
        long operatorId = userId(OPERATOR_LOGIN_NAME);
        String token = consoleToken();

        assertThat(deleteJson(token, "/api/console/users/" + operatorId + "/sessions").path("code").asInt()).isZero();
        // 允许踢自己的代价就是这一条：本次会话被自己打掉，权限与角色都还在库里
        assertUnauthorized(token);

        // 审计是唯一能回答「谁在什么时候把谁踢下线」的地方；操作人由审计实现从请求上下文取
        Map<String, Object> record = jdbcTemplate.queryForMap("""
                SELECT action, operator_id FROM sys_audit_log
                WHERE biz_type = 'USER' AND biz_id = ? AND summary = ?
                ORDER BY created_at DESC LIMIT 1
                """, OPERATOR_LOGIN_NAME, "踢下线用户 " + OPERATOR_LOGIN_NAME);
        assertThat(record.get("action")).isEqualTo("UPDATE");
        assertThat(record.get("operator_id")).isEqualTo(operatorId);
    }

    @Test
    @DisplayName("账号不存在：返回 40400，HTTP 仍为 200")
    void shouldRejectMissingUser() throws Exception {
        JsonNode response = deleteJson(consoleToken(), "/api/console/users/999999/sessions");

        // 业务结果一律 HTTP 200，由 code 表达；对不存在的标识静默成功会让调用方以为踢了人
        assertThat(response.path("code").asInt()).isEqualTo(NOT_FOUND_CODE);
        assertThat(response.path("message").asText()).isEqualTo("用户不存在");
    }

    @Test
    @DisplayName("权限边界：只有 system:user:update 的账号踢人得 403，目标会话仍然可用")
    void shouldRejectKickWithoutPermission() throws Exception {
        String targetId = seedReader("usk-denied-target", "被保护用户", "usk-denied@example.com");
        String targetToken = loginToken("usk-denied-target");

        mockMvc.perform(delete("/api/console/users/" + targetId + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken(UPDATER_LOGIN_NAME)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        // 403 之外还要证明业务逻辑没有执行：目标会话仍然可用
        assertThat(getJson(targetToken, "/api/console/users").path("code").asInt()).isZero();
    }

    private void assertUnauthorized(String token) throws Exception {
        mockMvc.perform(get("/api/console/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED_CODE));
    }

    private String consoleToken() throws Exception {
        return loginToken(OPERATOR_LOGIN_NAME);
    }

    private String loginToken(String loginName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(loginName, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode login = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(login.path("code").asInt()).as("%s 登录失败：%s", loginName, login).isZero();
        return login.path("data").path("token").asText();
    }

    private JsonNode getJson(String token, String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode deleteJson(String token, String path) throws Exception {
        MvcResult result = mockMvc.perform(delete(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** 造一个「已改密 + 只有列表权限」的账号：它的令牌在踢下线前是能用的。 */
    private String seedReader(String loginName, String displayName, String email) {
        Long userId = seedAccount(loginName, displayName, email);
        link(userId, seedRole(READER_ROLE, "只读用户列表", menuId("system:user:list")));
        return Long.toString(userId);
    }

    /** 取 {@code V027} 与 {@code V041} 定义的权限点标识；取不到说明迁移没跑或没生效。 */
    private Long menuId(String perms) {
        return jdbcTemplate.queryForObject("SELECT id FROM sys_menu WHERE perms = ?", Long.class, perms);
    }

    /** 按编码取角色标识；不存在时按给定名称创建，并可选地勾选权限点。 */
    private Long seedRole(String code, String name, Long... permsMenuIds) {
        jdbcTemplate.update("""
                INSERT INTO sys_role (code, name)
                SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = ?)
                """, code, name, code);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE code = ?", Long.class, code);
        for (Long permsMenuId : permsMenuIds) {
            jdbcTemplate.update("""
                    INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)
                    ON CONFLICT (role_id, menu_id) DO NOTHING
                    """, roleId, permsMenuId);
        }
        return roleId;
    }

    /** 建一个已改密的启用账号并返回其标识；判重交给 {@code WHERE NOT EXISTS}，库由整个测试 JVM 共用。 */
    private Long seedAccount(String loginName, String displayName, String email) {
        jdbcTemplate.update("""
                INSERT INTO sys_user (login_name, display_name, subject_type, password_hash,
                                      must_change_password, status, email)
                SELECT ?, ?, 'STAFF', ?, false, 'ACTIVE', ?
                WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE lower(login_name) = lower(?))
                """, loginName, displayName, passwordHasher.hash(PASSWORD), email, loginName);
        return userId(loginName);
    }

    /** 幂等写入用户角色关联；主键是 (user_id, role_id)，判重交给数据库。 */
    private void link(Long userId, Long roleId) {
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)
                ON CONFLICT (user_id, role_id) DO NOTHING
                """, userId, roleId);
    }

    private Long userId(String loginName) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE lower(login_name) = lower(?)", Long.class, loginName);
    }

    /**
     * 登录请求体的最小映射：契约要按「HTTP 上的 JSON」验证，复用生产侧类型会掩盖字段名被改的情况。
     *
     * @param loginName 登录名
     * @param password  明文密码
     */
    private record LoginPayload(String loginName, String password) {
    }
}

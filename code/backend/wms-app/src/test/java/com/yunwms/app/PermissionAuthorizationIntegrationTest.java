package com.yunwms.app;

import cn.dev33.satoken.stp.StpInterface;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口级权限点校验的端到端验证。
 *
 * <p>要证的是四件在单测里证不了的事：权限点真的被接口消费、未授权请求真的返回 403 且不落业务数据、
 * 「只需登录」的接口不会被权限点误伤、以及超管的权限是动态展开的（新增权限点无需补授权数据）。
 *
 * <p>不复用种子账号 {@code superadmin}：它带着「需先改密」标记，拦截器在改密前只放行改密接口，
 * 而且改它的状态会让 {@code BootstrapIntegrationTest} 依赖的初始状态不成立（两个测试类可能共用一个库）。
 * 这里用 SQL 造两个已改密的账号，分别挂着 {@code V025} 的超管角色与一个没有任何权限点的角色。
 *
 * <p>权限点的权威定义在 {@code V025}，本类只读不写——除了最后一条用例：它必须往 {@code sys_menu}
 * 插入一个新权限点，才能证明「新增权限点后超管自动拥有」，这正是规格里最容易退化成静态清单的一条。
 */
@DisplayName("接口权限点校验")
class PermissionAuthorizationIntegrationTest extends AbstractIntegrationTest {

    /** 持有内置超管角色的账号。 */
    private static final String ADMIN_LOGIN_NAME = "perm-admin";
    /** 只持有无权限点角色的账号。 */
    private static final String NO_PERMISSION_LOGIN_NAME = "perm-nobody";

    private static final String PASSWORD = "Admin@1234";
    private static final String INITIAL_PASSWORD = "Init@1234";

    /** 用于验证动态展开的权限点：没有任何接口挂它，只检查它是否出现在超管的权限列表里。 */
    private static final String NEWLY_SEEDED_PERMS = "system:user:delete";

    /** 无权限点角色编码，用例自造；迁移里除超管外不预置角色。 */
    private static final String NO_PERMISSION_ROLE = "TEST_NO_PERMISSION";

    /** 错误码来自 {@code ErrorCode}：40300 无权访问。 */
    private static final int FORBIDDEN_CODE = 40300;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    /** 直接断言 Sa-Token 拿到的权限列表：HTTP 层只能证明结果，证明不了「怎么算出来的」。 */
    @Autowired
    private StpInterface stpInterface;

    /**
     * 准备两个账号及其角色关联，并把密码重置回已知值。
     *
     * <p>每个用例前重设密码哈希：改密用例会把密码换掉，而用例之间共用一个库，
     * 不重置的话后续用例会因为「登录失败」这种与主题无关的原因挂掉，排查时白费一轮。
     */
    @BeforeEach
    void prepareAccounts() {
        Long adminRoleId = seedRole("SUPER_ADMIN", "超级管理员");
        Long noPermissionRoleId = seedRole(NO_PERMISSION_ROLE, "测试用无权限角色");

        Long adminId = seedAccount(ADMIN_LOGIN_NAME, "权限管理员", "perm-admin@example.com");
        Long nobodyId = seedAccount(NO_PERMISSION_LOGIN_NAME, "无权限用户", "perm-nobody@example.com");

        link(adminId, adminRoleId);
        link(nobodyId, noPermissionRoleId);
    }

    @Test
    @DisplayName("超管建号：持有内置角色即拥有权限点，建号成功")
    void shouldAllowSuperAdminToCreateUser() throws Exception {
        JsonNode response = createUser(adminToken(), "perm-created-by-admin", "perm-admin-created@example.com");

        assertThat(response.path("code").asInt()).isZero();
        assertThat(countUser("perm-created-by-admin")).isEqualTo(1);
    }

    @Test
    @DisplayName("未授权用户建号：HTTP 403 且业务码 40300，库里不留账号")
    void shouldRejectUserWithoutPermission() throws Exception {
        String token = login(NO_PERMISSION_LOGIN_NAME).path("data").path("token").asText();

        mockMvc.perform(post("/api/console/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody("perm-should-not-exist", "perm-denied@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        // 403 之外还要证明业务逻辑没有执行：只有「先拦后执行」才叫未授权接口不执行业务逻辑
        assertThat(countUser("perm-should-not-exist"))
                .as("被拒绝的建号请求不得留下账号")
                .isZero();
    }

    @Test
    @DisplayName("同一无权限用户改自己的密码：只需登录，正常通过")
    void shouldAllowPasswordChangeWithoutAnyPermission() throws Exception {
        String token = login(NO_PERMISSION_LOGIN_NAME).path("data").path("token").asText();

        mockMvc.perform(put("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordPayload(PASSWORD, "Changed@1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 换过密码才算真的改了：只断言 code=0 时，改密服务即使什么都没写也会通过
        assertThat(login(NO_PERMISSION_LOGIN_NAME, "Changed@1234").path("code").asInt()).isZero();
    }

    @Test
    @DisplayName("新增权限点：超管无需任何授权数据即可拥有")
    void shouldExpandSuperAdminPermissionsDynamically() throws Exception {
        Long adminId = userId(ADMIN_LOGIN_NAME);
        assertThat(stpInterface.getPermissionList(adminId, "login"))
                .as("超管必须拥有 V025 定义的建号权限点")
                .contains("system:user:create");

        // 不写 sys_role_menu，只往 sys_menu 加一条：模拟「版本升级带来新权限点」
        jdbcTemplate.update("""
                INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
                SELECT p.id, 'F', '删除用户', ?, 20
                FROM sys_menu p
                WHERE p.menu_type = 'C' AND p.path = '/system/users'
                  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = ?)
                """, NEWLY_SEEDED_PERMS, NEWLY_SEEDED_PERMS);

        assertThat(stpInterface.getPermissionList(adminId, "login"))
                .as("新增权限点后超管应自动拥有，不需要补 sys_role_menu")
                .contains(NEWLY_SEEDED_PERMS);
    }

    /** 用超管账号登录取令牌。 */
    private String adminToken() throws Exception {
        JsonNode login = login(ADMIN_LOGIN_NAME);
        assertThat(login.path("code").asInt()).as("超管账号登录失败：%s", login).isZero();
        return login.path("data").path("token").asText();
    }

    private JsonNode login(String loginName) throws Exception {
        return login(loginName, PASSWORD);
    }

    private JsonNode login(String loginName, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(loginName, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createUser(String token, String loginName, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/console/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserBody(loginName, email)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createUserBody(String loginName, String email) throws Exception {
        return objectMapper.writeValueAsString(new CreateUserPayload(
                loginName, "权限用例员工", "STAFF", null, email, null, INITIAL_PASSWORD, List.of()));
    }

    /**
     * 按编码取角色标识；不存在时按给定名称创建。
     *
     * <p>超管角色由 {@code V025} 建立，本方法对它只读取。用 WHERE NOT EXISTS 而不是 ON CONFLICT：
     * 迁移已经建过它，这里的目标是「不存在就补一个」，而 ON CONFLICT 无法覆盖「一行都没有」的情形。
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

    private int countUser(String loginName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sys_user WHERE lower(login_name) = lower(?)", Integer.class, loginName);
        return count == null ? 0 : count;
    }

    /**
     * 建号请求体的最小映射。
     *
     * <p>不复用生产侧的 {@code CreateUserRequest}：用同一个类型序列化再反序列化，
     * 会掩盖字段名被改而测试仍然通过的情况——接口契约要按「HTTP 上的 JSON」来验证。
     */
    private record CreateUserPayload(
            String loginName,
            String displayName,
            String subjectType,
            String ownerId,
            String email,
            String wecomUserId,
            String initialPassword,
            List<String> roleIds) {
    }

    /**
     * 改密请求体的最小映射，理由同上。
     *
     * @param oldPassword 旧密码明文
     * @param newPassword 新密码明文
     */
    private record ChangePasswordPayload(String oldPassword, String newPassword) {
    }

    /**
     * 登录请求体的最小映射，理由同上。
     *
     * @param loginName 登录名
     * @param password  明文密码
     */
    private record LoginPayload(String loginName, String password) {
    }
}

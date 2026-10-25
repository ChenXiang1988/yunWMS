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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 建号接口 {@code POST /api/console/users} 的端到端验证。
 *
 * <p>这里要证的是「接口 + 事务 + 库约束」三者接起来之后仍然成立：单测能证明用例如何判定，
 * 但证明不了 {@code must_change_password} 真的落了库、唯一索引真的拦住了大小写不同的重名、
 * 外键冲突真的被翻译成了 40000、以及失败时账号没有留在库里。
 *
 * <p>操作人不复用种子账号 {@code superadmin}：它带着「需先改密」标记，而拦截器在改密前只放行
 * 改密接口，建号接口根本到不了；替它改密又会污染 {@code BootstrapIntegrationTest} 依赖的初始状态
 * （两个测试类可能共用一个库）。因此每个用例前用 SQL 造一个已改密的普通员工账号，
 * 密码哈希由生产同款的 {@link PasswordHasher} 现场生成，不在测试里复制凭据。
 */
@DisplayName("管理端建号")
class UserCreationIntegrationTest extends AbstractIntegrationTest {

    /** 操作人账号：已改密、已启用的普通员工，只作为「谁在建号」的会话来源。 */
    private static final String OPERATOR_LOGIN_NAME = "console-operator";
    private static final String OPERATOR_PASSWORD = "Admin@1234";

    /** 新账号的初始密码，需满足密码策略。 */
    private static final String INITIAL_PASSWORD = "Init@1234";

    /** 用例自造的角色编码：角色体系在 2.2 落地前角色表为空，迁移里不预置。 */
    private static final String ROLE_CODE = "TEST_ROLE";

    /** 错误码来自 {@code ErrorCode}：40000 参数问题，40901 登录名已被占用。 */
    private static final int BAD_REQUEST_CODE = 40000;
    private static final int LOGIN_NAME_TAKEN_CODE = 40901;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    /**
     * 准备一个可用的会话主体。
     *
     * <p>用 {@code WHERE NOT EXISTS} 而不是 {@code ON CONFLICT}：登录名的唯一索引建在
     * {@code lower(login_name)} 这个表达式上，按列名推断不到它。重复执行时插入零行是正常结果，
     * 正对应用例之间共用一个库的情形。
     */
    @BeforeEach
    void prepareOperatorAccount() {
        jdbcTemplate.update("""
                INSERT INTO sys_user (login_name, display_name, subject_type, password_hash,
                                      must_change_password, status, email)
                SELECT ?, ?, 'STAFF', ?, false, 'ACTIVE', ?
                WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE lower(login_name) = ?)
                """,
                OPERATOR_LOGIN_NAME, "建号操作员", passwordHasher.hash(OPERATOR_PASSWORD),
                "operator@example.com", OPERATOR_LOGIN_NAME);

        // 建号接口自 V025 起要求权限点 system:user:create，因此操作人必须真的拥有它。
        // 这里借用内置超管角色（它的权限点由鉴权时动态展开），而不是给测试造一条授权数据：
        // 造授权数据就要跟着改 sys_role_menu，那正是规格禁止的做法。
        // 「没有权限点会被拒」不在本类验证，见 PermissionAuthorizationIntegrationTest。
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id)
                SELECT u.id, r.id
                FROM sys_user u
                         CROSS JOIN sys_role r
                WHERE lower(u.login_name) = lower(?)
                  AND r.code = 'SUPER_ADMIN'
                ON CONFLICT (user_id, role_id) DO NOTHING
                """, OPERATOR_LOGIN_NAME);
    }

    @Test
    @DisplayName("建员工成功：落库为待改密且启用，新账号能用初始密码登录并被要求改密")
    void shouldCreateStaffAndLetNewAccountLogin() throws Exception {
        String token = consoleToken();

        JsonNode created = createUser(token, new CreateUserPayload(
                "zhangsan", "张三", "STAFF", null, "z@example.com", "wx_zhangsan",
                INITIAL_PASSWORD, List.of()));

        assertThat(created.path("code").asInt()).isZero();
        // 标识以字符串返回：超过 2^53 的 Long 在 JS 里会丢精度
        assertThat(created.path("data").path("id").asText()).isNotBlank();
        assertThat(created.path("data").path("loginName").asText()).isEqualTo("zhangsan");

        Long operatorId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE login_name = ?", Long.class, OPERATOR_LOGIN_NAME);
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT must_change_password, status, password_hash, created_by, updated_by, wecom_user_id
                FROM sys_user WHERE login_name = ?
                """, "zhangsan");
        assertThat(row.get("must_change_password"))
                .as("初始密码只够换一次登录")
                .isEqualTo(true);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        // 落库的必须是哈希：明文一旦进库，改密也换不回已经外流的记录
        assertThat(String.valueOf(row.get("password_hash"))).startsWith("$2a$").doesNotContain(INITIAL_PASSWORD);
        assertThat(row.get("created_by")).isEqualTo(operatorId);
        assertThat(row.get("updated_by")).isEqualTo(operatorId);
        // 规格「内部员工 MUST 记录企业微信 userid」：列由 V024 引入，这里确认它真的写进去了
        assertThat(row.get("wecom_user_id")).isEqualTo("wx_zhangsan");

        JsonNode login = login("zhangsan", INITIAL_PASSWORD);
        assertThat(login.path("code").asInt()).isZero();
        assertThat(login.path("data").path("mustChangePassword").asBoolean())
                .as("新建账号必须被强制改密，否则初始密码会长期有效")
                .isTrue();
    }

    @Test
    @DisplayName("建员工成功：角色为空的数组与省略字段等价，都不写角色关联")
    void shouldCreateStaffWithoutRoles() throws Exception {
        String token = consoleToken();

        JsonNode created = createUser(token, staffPayload("wangwu", "w@example.com"));

        assertThat(created.path("code").asInt()).isZero();
        Integer roles = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM sys_user_role ur
                JOIN sys_user u ON u.id = ur.user_id
                WHERE u.login_name = ?
                """, Integer.class, "wangwu");
        assertThat(roles).isZero();
    }

    @Test
    @DisplayName("建员工并分配角色：关联表按集合语义写入，同一角色只落一行")
    void shouldAssignRolesWithoutDuplicating() throws Exception {
        // 角色体系要到 2.2 才落地，角色表当前为空；这里由用例自己造一行，
        // 而不是往迁移里预置角色——预置的角色只能靠猜，将来必然要再迁一次
        jdbcTemplate.update("""
                INSERT INTO sys_role (code, name)
                SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = ?)
                """, ROLE_CODE, "测试角色", ROLE_CODE);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE code = ?", Long.class, ROLE_CODE);

        JsonNode created = createUser(consoleToken(), new CreateUserPayload(
                "zhouba", "周八", "STAFF", null, "zb@example.com", null, INITIAL_PASSWORD,
                List.of(roleId, roleId)));

        assertThat(created.path("code").asInt()).isZero();
        Integer roles = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM sys_user_role ur
                JOIN sys_user u ON u.id = ur.user_id
                WHERE u.login_name = ?
                """, Integer.class, "zhouba");
        // 同一个角色传两次是集合语义，去重后只写一行：复合主键也不允许两行
        assertThat(roles).isEqualTo(1);
    }

    @Test
    @DisplayName("货主用户不给 ownerId：拒绝建号，库里不留账号")
    void shouldRejectOwnerWithoutOwnerId() throws Exception {
        JsonNode response = createUser(consoleToken(), new CreateUserPayload(
                "owner01", "货主账号", "OWNER", null, "o@example.com", null, INITIAL_PASSWORD, List.of()));

        assertThat(response.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(response.path("message").asText()).isEqualTo("货主用户必须指定所属货主");
        assertThat(countUser("owner01")).isZero();
    }

    @Test
    @DisplayName("货主用户指定不存在的货主：外键拦下并转成 40000，库里不留账号")
    void shouldRejectOwnerThatDoesNotExist() throws Exception {
        JsonNode response = createUser(consoleToken(), new CreateUserPayload(
                "owner99", "不存在的货主账号", "OWNER", 999L, "o99@example.com", null, INITIAL_PASSWORD, List.of()));

        assertThat(response.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        // 这条同时证明 V024 的外键真的生效：wms-auth 查不到 owner 表，只有数据库能拦下这个 id
        assertThat(response.path("message").asText()).isEqualTo("指定的货主不存在");
        assertThat(countUser("owner99")).isZero();
    }

    @Test
    @DisplayName("员工不给邮箱：拒绝建号，库里不留账号")
    void shouldRejectStaffWithoutEmail() throws Exception {
        JsonNode response = createUser(consoleToken(), new CreateUserPayload(
                "zhaoliu", "赵六", "STAFF", null, null, null, INITIAL_PASSWORD, List.of()));

        assertThat(response.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(response.path("message").asText()).isEqualTo("内部员工必须填写邮箱");
        assertThat(countUser("zhaoliu")).isZero();
    }

    @Test
    @DisplayName("登录名重复且大小写不同：返回 40901，不落第二个账号")
    void shouldRejectDuplicateLoginNameIgnoringCase() throws Exception {
        String token = consoleToken();
        assertThat(createUser(token, staffPayload("lisi", "lisi@example.com")).path("code").asInt()).isZero();

        JsonNode duplicated = createUser(token, staffPayload("LiSi", "lisi2@example.com"));

        assertThat(duplicated.path("code").asInt()).isEqualTo(LOGIN_NAME_TAKEN_CODE);
        assertThat(duplicated.path("message").asText()).isEqualTo("登录名已被占用");
        assertThat(countUser("lisi")).isEqualTo(1);
    }

    @Test
    @DisplayName("角色不存在：拒绝建号并回滚，账号不留库")
    void shouldRollbackWholeRegistrationWhenRoleMissing() throws Exception {
        JsonNode response = createUser(consoleToken(), new CreateUserPayload(
                "sunqi", "孙七", "STAFF", null, "s@example.com", null, INITIAL_PASSWORD, List.of(999L)));

        assertThat(response.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(response.path("message").asText()).isEqualTo("指定的角色不存在");
        // 账号先写、角色后写：这里验证的是两张表在同一个事务里，前一步不留下孤儿账号
        assertThat(countUser("sunqi")).isZero();
    }

    /** 一个内部员工的建号请求体，角色为空。 */
    private static CreateUserPayload staffPayload(String loginName, String email) {
        return new CreateUserPayload(loginName, "测试员工", "STAFF", null, email, null,
                INITIAL_PASSWORD, List.of());
    }

    /** 用操作人账号登录取令牌；未改密时这里会拿不到可用令牌，属于测试自身的前提失败。 */
    private String consoleToken() throws Exception {
        JsonNode login = login(OPERATOR_LOGIN_NAME, OPERATOR_PASSWORD);
        assertThat(login.path("code").asInt()).as("操作人账号登录失败：%s", login).isZero();
        assertThat(login.path("data").path("mustChangePassword").asBoolean())
                .as("操作人账号不应处于待改密状态")
                .isFalse();
        return login.path("data").path("token").asText();
    }

    private JsonNode login(String loginName, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginPayload(loginName, password));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createUser(String token, CreateUserPayload payload) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/console/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
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
     *
     * @param loginName       登录名
     * @param displayName     展示名
     * @param subjectType     主体类型编码
     * @param ownerId         所属货主标识，可为 {@code null}
     * @param email           邮箱，可为 {@code null}
     * @param wecomUserId     企业微信 userid，可为 {@code null}
     * @param initialPassword 初始密码明文
     * @param roleIds         角色标识，可为空数组
     */
    private record CreateUserPayload(
            String loginName,
            String displayName,
            String subjectType,
            Long ownerId,
            String email,
            String wecomUserId,
            String initialPassword,
            List<Long> roleIds) {
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

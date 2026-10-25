package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.auth.domain.port.PasswordHasher;
import com.yunwms.auth.domain.port.UserQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户编辑、启停与重置密码的端到端验证。
 *
 * <p>要证的是单测证不了的四件事：{@code V028} 的重置密码权限点真的被接口消费、
 * 停用与重置之后既有令牌真的失效（规格「既有会话立即失效」只能靠真实会话链路验证）、
 * 停用后无法登录而启用后可以、以及重置出来的临时口令会被强制改密。
 *
 * <p>操作人不复用种子账号 {@code superadmin}：它带着「需先改密」标记，拦截器在改密前只放行
 * 改密接口；替它改密又会污染 {@code BootstrapIntegrationTest} 依赖的初始状态（测试类共用一个库）。
 * 这里用 SQL 造一个挂着内置超管角色的已改密账号——超管权限在鉴权时动态展开，
 * 因此它天然拥有本批新加的权限点。
 *
 * <p>用例之间不隔离数据，因此每个用例用自己的登录名、角色编码与货主编码，断言只针对自己造的那几条。
 * 唯一例外是「最后一个可用超管」的判定：它要求系统里只有一个可用超管，只能在事务里临时停用其他超管
 * 再回滚，见该用例的说明。
 */
@DisplayName("用户编辑与启停")
class UserAdministrationIntegrationTest extends AbstractIntegrationTest {

    /** 操作人账号：已改密、已启用，挂内置超管角色。 */
    private static final String OPERATOR_LOGIN_NAME = "uadmin-operator";
    /** 只有 {@code system:user:update} 的账号，用于验证「能改状态、不能重置密码」。 */
    private static final String UPDATER_LOGIN_NAME = "uadmin-updater";
    private static final String PASSWORD = "Admin@1234";
    /** 账号初始密码与重置后的密码，两者都必须满足密码策略。 */
    private static final String INITIAL_PASSWORD = "Init@1234";
    private static final String RESET_PASSWORD = "Reset@1234";

    private static final String UPDATER_ROLE = "UADMIN_UPDATER";
    private static final String READER_ROLE = "UADMIN_READER";

    private static final String OWNER_CODE = "OWNER-UAD";
    private static final String OWNER_NAME = "编辑用例货主";
    private static final String OWNER_SKU_PREFIX = "UAD1";

    /** 错误码来自 {@code ErrorCode}：40000 参数问题，40100 未登录，40102 账号停用，40900 状态不允许。 */
    private static final int BAD_REQUEST_CODE = 40000;
    private static final int UNAUTHORIZED_CODE = 40100;
    private static final int LOGIN_ACCOUNT_DISABLED_CODE = 40102;
    private static final int CONFLICT_CODE = 40900;
    private static final int EMAIL_TAKEN_CODE = 40903;
    private static final int FORBIDDEN_CODE = 40300;
    /** 需先改密：HTTP 401，业务码 40104。 */
    private static final int MUST_CHANGE_PASSWORD_CODE = 40104;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private UserQuery userQuery;

    @BeforeEach
    void prepareAccounts() {
        link(seedAccount(OPERATOR_LOGIN_NAME, "用户治理操作员", "uadmin-operator@example.com"),
                seedRole("SUPER_ADMIN", "超级管理员", false));
        // 只给「编辑」权限点：本批要求重置密码单独受控，这条用例就是那条边界
        link(seedAccount(UPDATER_LOGIN_NAME, "只有编辑权限", "uadmin-updater@example.com"),
                seedRole(UPDATER_ROLE, "只有编辑权限的角色", false, menuId("system:user:update")));
        seedOwner();
    }

    @Test
    @DisplayName("V028：重置密码权限点挂在「用户管理」菜单下，排序在编辑之后")
    void shouldSeedResetPasswordPermission() {
        // 权限点的唯一用途是被接口消费，那一条由下面的 403 用例证明；这里核对它落在正确的位置上，
        // 否则它要么是别的菜单下的孤儿，要么与编辑共用同一个排序位而让配置页的顺序随机
        Integer matched = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM sys_menu f
                         JOIN sys_menu c ON c.id = f.parent_id
                WHERE f.menu_type = 'F'
                  AND f.perms = 'system:user:reset'
                  AND c.menu_type = 'C'
                  AND c.path = '/system/users'
                  AND f.sort_no = 30
                """, Integer.class);

        assertThat(matched).isOne();
    }

    @Test
    @DisplayName("编辑：三个字段生效，详情能读到；多传 loginName 被拒绝")
    void shouldEditProfileAndRejectUnknownFields() throws Exception {
        String token = consoleToken();
        String userId = createdUserId(createUser(token, new CreateUserPayload(
                "uadmin-edit", "待编辑用户", "STAFF", null, "uadmin-edit@example.com", "wx_old",
                INITIAL_PASSWORD, List.of())));

        JsonNode edited = putJson(token, "/api/console/users/" + userId,
                """
                {"displayName": "  改过的名字  ", "email": "uadmin-edited@example.com", "wecomUserId": "wx_new"}
                """);
        assertThat(edited.path("code").asInt()).isZero();

        JsonNode detail = getJson(token, "/api/console/users/" + userId).path("data");
        // 两侧空白被裁掉：带空格的展示名在列表里看起来像「有缩进」，而库里是两份不同的值
        assertThat(detail.path("displayName").asText()).isEqualTo("改过的名字");
        assertThat(detail.path("email").asText()).isEqualTo("uadmin-edited@example.com");
        assertThat(detail.path("wecomUserId").asText()).isEqualTo("wx_new");

        // 登录名是账号标识，不在可编辑字段里：多传要被拒绝，而不是静默忽略后让管理员以为改名生效了
        JsonNode rejected = putJson(token, "/api/console/users/" + userId,
                """
                {"displayName": "想顺手改登录名", "loginName": "uadmin-renamed"}
                """);
        assertThat(rejected.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(getJson(token, "/api/console/users/" + userId).path("data").path("loginName").asText())
                .as("请求被拒绝时不得有任何字段被改")
                .isEqualTo("uadmin-edit");
    }

    @Test
    @DisplayName("编辑：邮箱重复转 40903，员工清空邮箱转 40000，两者都不改任何一行")
    void shouldRejectDuplicateEmailAndBlankStaffEmail() throws Exception {
        String token = consoleToken();
        createdUserId(createUser(token, new CreateUserPayload(
                "uadmin-email-owner", "占位邮箱用户", "STAFF", null, "uadmin-taken@example.com", null,
                INITIAL_PASSWORD, List.of())));
        String userId = createdUserId(createUser(token, new CreateUserPayload(
                "uadmin-email-target", "被改邮箱用户", "STAFF", null, "uadmin-email-target@example.com", null,
                INITIAL_PASSWORD, List.of())));

        // 大小写不同的同一个邮箱同样算重复：唯一索引建在 lower(email) 上
        JsonNode duplicate = putJson(token, "/api/console/users/" + userId,
                """
                {"displayName": "被改邮箱用户", "email": "UADMIN-TAKEN@example.com"}
                """);
        assertThat(duplicate.path("code").asInt()).isEqualTo(EMAIL_TAKEN_CODE);
        assertThat(duplicate.path("message").asText()).isEqualTo("邮箱已被其他账号使用");

        // 内部员工必须有邮箱：由 ck_user_email_required 拦下，翻译成中文提示而不是 500
        JsonNode blank = putJson(token, "/api/console/users/" + userId, """
                {"displayName": "被改邮箱用户"}
                """);
        assertThat(blank.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(blank.path("message").asText()).isEqualTo("内部员工必须填写邮箱");

        JsonNode detail = getJson(token, "/api/console/users/" + userId).path("data");
        assertThat(detail.path("email").asText())
                .as("两次拒绝都不该改动原值")
                .isEqualTo("uadmin-email-target@example.com");
        assertThat(detail.path("displayName").asText()).isEqualTo("被改邮箱用户");
    }

    @Test
    @DisplayName("编辑：货主账号可以清空邮箱（与内部员工的必填口径不同）")
    void shouldAllowClearingOwnerEmail() throws Exception {
        String token = consoleToken();
        String userId = createdUserId(createUser(token, new CreateUserPayload(
                "uadmin-owner-clearing", "货主账号", "OWNER",
                Long.toString(ownerId(OWNER_CODE)), "owner-to-clear@example.com", null,
                INITIAL_PASSWORD, List.of())));

        JsonNode cleared = putJson(token, "/api/console/users/" + userId, """
                {"displayName": "货主账号"}
                """);

        assertThat(cleared.path("code").asInt()).isZero();
        // 清空要落成 NULL 而不是空串：email = '' 能绕过 ck_user_email_required
        assertThat(getJson(token, "/api/console/users/" + userId).path("data").path("email").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sys_user WHERE id = ? AND email = ''", Integer.class, Long.parseLong(userId)))
                .as("空串会绕过 ck_user_email_required")
                .isZero();
    }

    @Test
    @DisplayName("停用：既有令牌立即 401、无法再登录；启用后可以重新登录")
    void shouldDisableInvalidateSessionsAndBlockLogin() throws Exception {
        String token = consoleToken();
        String userId = seedReader("uadmin-disable", "待停用用户", "uadmin-disable@example.com");
        String userToken = login("uadmin-disable").path("data").path("token").asText();

        // 前置：这张令牌本来是能用的，否则下面的 401 不能证明「停用让它失效」
        assertThat(getJson(userToken, "/api/console/users").path("code").asInt()).isZero();

        JsonNode disabled = putJson(token, "/api/console/users/" + userId + "/status", """
                {"status": "DISABLED"}
                """);
        assertThat(disabled.path("code").asInt()).isZero();

        // 规格「停用某用户 → 该用户既有会话立即失效」：只改库不够，令牌还能用到过期
        mockMvc.perform(get("/api/console/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED_CODE));
        // 规格后半句：无法再次登录
        assertThat(login("uadmin-disable").path("code").asInt()).isEqualTo(LOGIN_ACCOUNT_DISABLED_CODE);

        assertThat(putJson(token, "/api/console/users/" + userId + "/status", """
                {"status": "ACTIVE"}
                """).path("code").asInt()).isZero();

        JsonNode relogin = login("uadmin-disable");
        assertThat(relogin.path("code").asInt()).isZero();
        assertThat(relogin.path("data").path("token").asText()).isNotBlank();
    }

    @Test
    @DisplayName("停用自己：409 拒绝，自己的令牌仍然可用")
    void shouldRejectDisablingSelf() throws Exception {
        String token = consoleToken();
        long operatorId = userId(OPERATOR_LOGIN_NAME);

        JsonNode response = putJson(token, "/api/console/users/" + operatorId + "/status", """
                {"status": "DISABLED"}
                """);

        // 409 段位而不是 40000：入参合法，被拒绝的是它造成的状态
        assertThat(response.path("code").asInt()).isEqualTo(CONFLICT_CODE);
        assertThat(response.path("message").asText()).isEqualTo("不能停用自己");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM sys_user WHERE id = ?", String.class, operatorId)).isEqualTo("ACTIVE");
        // 拒绝之后自己的会话不能被自己打掉
        assertThat(getJson(token, "/api/console/users").path("code").asInt()).isZero();
    }

    @Test
    @DisplayName("重置密码：旧令牌失效、旧密码登不进、新密码可登录且被强制改密")
    void shouldResetPasswordInvalidateSessionsAndForceChange() throws Exception {
        String token = consoleToken();
        String userId = seedReader("uadmin-reset", "重置用例用户", "uadmin-reset@example.com");
        String userToken = login("uadmin-reset").path("data").path("token").asText();
        assertThat(getJson(userToken, "/api/console/users").path("code").asInt()).isZero();

        JsonNode reset = putJson(token, "/api/console/users/" + userId + "/password",
                "{\"newPassword\": \"" + RESET_PASSWORD + "\"}");
        assertThat(reset.path("code").asInt()).isZero();

        // 凭据换了，别处手里的旧令牌必须立刻作废
        mockMvc.perform(get("/api/console/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UNAUTHORIZED_CODE));

        assertThat(login("uadmin-reset").path("code").asInt())
                .as("旧密码必须失效")
                .isEqualTo(40101);

        JsonNode relogin = login("uadmin-reset", RESET_PASSWORD);
        assertThat(relogin.path("code").asInt()).isZero();
        assertThat(relogin.path("data").path("mustChangePassword").asBoolean())
                .as("管理员重置出来的是临时口令，必须强制再改一次")
                .isTrue();

        // 待改密状态下除改密接口外一律 40104，证明「强制改密」真的生效而不是只写在响应里
        mockMvc.perform(get("/api/console/users").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + relogin.path("data").path("token").asText()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(MUST_CHANGE_PASSWORD_CODE));
    }

    @Test
    @DisplayName("重置密码：密码不合规一次返回全部违规项，不落库")
    void shouldReturnAllPasswordPolicyViolations() throws Exception {
        String token = consoleToken();
        String userId = seedReader("uadmin-weak", "弱密码用例", "uadmin-weak@example.com");

        JsonNode response = putJson(token, "/api/console/users/" + userId + "/password", """
                {"newPassword": "abc"}
                """);

        assertThat(response.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(response.path("message").asText())
                .contains("密码长度需为", "密码必须包含大写字母", "密码必须包含数字");
        // 原密码仍然有效：策略不通过时不能有任何一行被改
        assertThat(login("uadmin-weak").path("code").asInt()).isZero();
    }

    @Test
    @DisplayName("重置自己：409 拒绝，自己的令牌仍然可用")
    void shouldRejectResettingSelf() throws Exception {
        String token = consoleToken();
        long operatorId = userId(OPERATOR_LOGIN_NAME);

        JsonNode response = putJson(token, "/api/console/users/" + operatorId + "/password",
                "{\"newPassword\": \"" + RESET_PASSWORD + "\"}");

        assertThat(response.path("code").asInt()).isEqualTo(CONFLICT_CODE);
        assertThat(response.path("message").asText())
                .isEqualTo("不能重置自己的密码，请通过「修改密码」并提供当前密码");
        // 本接口不验旧密码，若能作用于自己就是一条绕过旧密码校验的通道
        assertThat(login(OPERATOR_LOGIN_NAME).path("code").asInt()).isZero();
    }

    @Test
    @DisplayName("权限边界：只有 system:user:update 的账号能改状态，但重置密码得 403")
    void shouldSeparateUpdateAndResetPermissions() throws Exception {
        String targetId = seedReader("uadmin-denied-target", "被保护用户", "uadmin-denied@example.com");
        String token = login(UPDATER_LOGIN_NAME).path("data").path("token").asText();

        // 有 system:user:update：启停可用（幂等的同状态提交）
        assertThat(putJson(token, "/api/console/users/" + targetId + "/status", """
                {"status": "ACTIVE"}
                """).path("code").asInt()).isZero();

        // 没有 system:user:reset：V028 的权限点必须真的挂在接口上
        mockMvc.perform(put("/api/console/users/" + targetId + "/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"" + RESET_PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        // 403 之外还要证明业务逻辑没有执行：旧密码仍然能登录
        assertThat(login("uadmin-denied-target").path("code").asInt()).isZero();
    }

    /**
     * 「最后一个可用超管」判定的反向分支：另有可用超管、或根本不持内置角色时都必须为假。
     *
     * <p>跑在会回滚的事务里（下一条用例同理）：要造出「只剩一个超管」的状态就得临时改动
     * {@code V023} 的 {@code superadmin}，而它是别的测试类的断言对象。测试事务不提交，
     * 其他测试类看不到任何变化。
     *
     * <p>为什么拆成两条用例、且每条只对同一个账号断言一次：MyBatis 的一级缓存按
     * 「语句 + 参数」缓存同一事务内的结果，而这里的准备动作走 {@link JdbcTemplate}
     * （不经过 MyBatis，因此不会清掉那个缓存）。同一个账号 id 查第二次会直接拿到旧值，
     * 断言就成了「在验缓存」。换一条用例即换一个事务与会话，缓存不会跨过来。
     */
    @Test
    @Transactional
    @DisplayName("最后一个可用超管：另有可用超管时不算，不持内置角色也不算")
    void shouldNotReportSuperAdminWhenAvailableOthersExist() {
        // 事务必须真的在：没有它（或它不默认回滚）时，下面的准备动作会直接写进共用库
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .as("本用例依赖测试事务回滚，缺事务时不得改动共用的库")
                .isTrue();
        Long builtinHolderId = seedAccount("uadmin-not-solo", "非独占超管用例", "uadmin-not-solo@example.com");
        Long normalId = seedAccount("uadmin-normal", "普通账号", "uadmin-normal@example.com");
        // 新建内置角色并只挂给 builtinHolderId：不动 V025 的 SUPER_ADMIN
        link(builtinHolderId, seedRole("UADMIN_NOT_SOLO_BUILTIN", "非独占用例内置角色", true));
        link(normalId, seedRole("UADMIN_NORMAL", "普通角色", false));

        assertThat(userQuery.isOnlyAvailableSuperAdmin(builtinHolderId))
                .as("系统里还有别的可用超管（V023 的 superadmin 等），此时不能算「最后一个」")
                .isFalse();
        assertThat(userQuery.isOnlyAvailableSuperAdmin(normalId))
                .as("不持有内置角色的账号永远不是可用超管")
                .isFalse();
    }

    /**
     * 「最后一个可用超管」判定的正向分支，以及「内置角色停用就不算可用」这一条口径。
     *
     * <p>准备顺序是要紧的：先把除这两个账号以外的可用超管全部停用，再停掉其中一个账号的内置角色，
     * 于是剩下那个就是唯一的可用超管。若把「停用内置角色」放到前面，两个账号会同时变成不可用，
     * 就验不出「独占时为真」了。
     */
    @Test
    @Transactional
    @DisplayName("最后一个可用超管：独占时算，内置角色停用后不再算")
    void shouldReportOnlyAvailableSuperAdmin() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive())
                .as("本用例会停用其他全部超管，必须靠测试事务回滚收尾")
                .isTrue();
        Long soloId = seedAccount("uadmin-solo", "独占超管用例", "uadmin-solo@example.com");
        Long roleDisabledId = seedAccount("uadmin-role-disabled", "角色被停用用例", "uadmin-role-disabled@example.com");
        link(soloId, seedRole("UADMIN_SOLO_BUILTIN", "独占用例内置角色", true));
        Long disabledBuiltinRoleId = seedRole("UADMIN_DISABLED_BUILTIN", "将被停用的内置角色", true);
        link(roleDisabledId, disabledBuiltinRoleId);

        // 停用除这两者以外的全部可用超管（事务内，用例结束回滚）
        jdbcTemplate.update("""
                UPDATE sys_user SET status = 'DISABLED'
                WHERE id NOT IN (?, ?)
                  AND id IN (SELECT ur.user_id
                             FROM sys_user_role ur
                                      JOIN sys_role r ON r.id = ur.role_id
                             WHERE r.builtin = true AND r.status = 'ACTIVE' AND r.deleted_at IS NULL)
                """, soloId, roleDisabledId);
        // 只剩 soloId 的内置角色还启用中：它成了唯一的可用超管
        jdbcTemplate.update("UPDATE sys_role SET status = 'DISABLED' WHERE id = ?", disabledBuiltinRoleId);

        assertThat(userQuery.isOnlyAvailableSuperAdmin(soloId))
                .as("其他可用超管都停用后，剩下的这一个就是最后一个，停用它等于把系统锁死")
                .isTrue();
        assertThat(userQuery.isOnlyAvailableSuperAdmin(roleDisabledId))
                .as("内置角色停用后鉴权侧不展开权限点，这种账号挡不住任何配置操作")
                .isFalse();
    }

    /** 用操作人账号登录取令牌；未改密时这里会拿不到可用令牌，属于测试自身的前提失败。 */
    private String consoleToken() throws Exception {
        JsonNode login = login(OPERATOR_LOGIN_NAME);
        assertThat(login.path("code").asInt()).as("操作人账号登录失败：%s", login).isZero();
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

    private JsonNode createUser(String token, CreateUserPayload payload) throws Exception {
        return postJson(token, "/api/console/users", objectMapper.writeValueAsString(payload));
    }

    private JsonNode postJson(String token, String path, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode putJson(String token, String path, String body) throws Exception {
        MvcResult result = mockMvc.perform(put(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
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

    /** 取建号响应里的标识；失败时直接失败，避免后续断言被 null 带偏。 */
    private static String createdUserId(JsonNode createdUser) {
        assertThat(createdUser.path("code").asInt()).as("建号失败：%s", createdUser).isZero();
        return createdUser.path("data").path("id").asText();
    }

    /** 造一个「已改密 + 只有列表权限」的账号：它的令牌在停用与重置前是能用的。 */
    private String seedReader(String loginName, String displayName, String email) {
        Long userId = seedAccount(loginName, displayName, email);
        link(userId, seedRole(READER_ROLE, "只读用户列表", false, menuId("system:user:list")));
        return Long.toString(userId);
    }

    /** 取 {@code V027} 定义的权限点标识；取不到说明迁移没跑或没生效。 */
    private Long menuId(String perms) {
        return jdbcTemplate.queryForObject("SELECT id FROM sys_menu WHERE perms = ?", Long.class, perms);
    }

    private Long ownerId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM owner WHERE code = ?", Long.class, code);
    }

    /**
     * 造一个货主：编辑用例要验证「货主账号可以清空邮箱」，而货主表由 {@code V001} 建立、
     * 当前没有接口能建。
     */
    private void seedOwner() {
        jdbcTemplate.update("""
                INSERT INTO owner (code, name, sku_prefix, owner_type, settlement_entity, country, city, address_line)
                SELECT ?, ?, ?, 'THIRD_PARTY', '集成测试结算主体', 'CN', '上海', '测试路 2 号'
                WHERE NOT EXISTS (SELECT 1 FROM owner WHERE code = ?)
                """, OWNER_CODE, OWNER_NAME, OWNER_SKU_PREFIX, OWNER_CODE);
    }

    /** 按编码取角色标识；不存在时按给定名称创建，并可选地勾选权限点。 */
    private Long seedRole(String code, String name, boolean builtin, Long... permsMenuIds) {
        jdbcTemplate.update("""
                INSERT INTO sys_role (code, name, builtin)
                SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = ?)
                """, code, name, builtin, code);
        Long roleId = jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE code = ?", Long.class, code);
        for (Long permsMenuId : permsMenuIds) {
            jdbcTemplate.update("""
                    INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)
                    ON CONFLICT (role_id, menu_id) DO NOTHING
                    """, roleId, permsMenuId);
        }
        return roleId;
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

    /**
     * 建号请求体的最小映射。
     *
     * <p>不复用生产侧的 {@code CreateUserRequest}：用同一个类型序列化再反序列化，会掩盖
     * 字段名被改而测试仍然通过的情况——接口契约要按「HTTP 上的 JSON」来验证。
     *
     * @param loginName       登录名
     * @param displayName     展示名
     * @param subjectType     主体类型编码
     * @param ownerId         所属货主标识，字符串形式；内部员工传 {@code null}
     * @param email           邮箱；货主用户可为 {@code null}
     * @param wecomUserId     企业微信 userid，可为 {@code null}
     * @param initialPassword 初始密码明文
     * @param roleIds         建号时同时分配的角色标识，按契约以字符串传
     */
    private record CreateUserPayload(String loginName,
                                     String displayName,
                                     String subjectType,
                                     String ownerId,
                                     String email,
                                     String wecomUserId,
                                     String initialPassword,
                                     List<String> roleIds) {
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

package com.yunwms.app;

import cn.dev33.satoken.stp.StpInterface;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.auth.application.dto.UserPage;
import com.yunwms.auth.application.query.UserQueryService;
import com.yunwms.auth.domain.port.PasswordHasher;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户管理接口（列表 / 详情 / 分配角色）的端到端验证。
 *
 * <p>要证的是单测证不了的五件事：{@code V027} 的两个权限点真的被接口消费、
 * 「建角色 → 建用户 → 分配角色 → 该用户登录后按角色获得权限」这条链真的闭合、
 * 货主名称真的随列表带出、替换语义真的把关联换成提交的那一批（含空数组清空）、
 * 以及列表的 SQL 条数不随页内行数增长。
 *
 * <p>操作人不复用种子账号 {@code superadmin}：它带着「需先改密」标记，拦截器在改密前只放行
 * 改密接口；替它改密又会污染 {@code BootstrapIntegrationTest} 依赖的初始状态（测试类共用一个库）。
 * 这里用 SQL 造一个挂着内置超管角色的已改密账号——超管权限在鉴权时动态展开，
 * 因此它天然拥有 {@code V027} 新加的权限点。
 *
 * <p>用例之间不隔离数据，因此每个用例用自己的登录名与角色编码，断言只针对自己造的那几条。
 */
@DisplayName("用户管理")
class UserManagementIntegrationTest extends AbstractIntegrationTest {

    /** 操作人账号：已改密、已启用，挂内置超管角色。 */
    private static final String OPERATOR_LOGIN_NAME = "user-mgmt-operator";
    /** 无任何权限点的账号，用于验证 403。 */
    private static final String NO_PERMISSION_LOGIN_NAME = "user-mgmt-nobody";
    private static final String PASSWORD = "Admin@1234";
    /** 新建账号的初始密码，需满足密码策略。 */
    private static final String INITIAL_PASSWORD = "Init@1234";

    /** 无权限点角色编码，用例自造；迁移里除超管外不预置角色。 */
    private static final String NO_PERMISSION_ROLE = "TEST_USER_MGMT_NO_PERMISSION";

    /**
     * 用例自造的货主：{@code code} 唯一，{@code sku_prefix} 必须是 4 位大写字母或数字。
     *
     * <p>前缀以 {@code X} 结尾而不是数字 {@code 1}：库里的 {@code ck_owner_sku_prefix} 只要求
     * {@code [A-Z0-9]{4}}，而规格与 {@code SkuPrefix} 值对象额外排除 {@code 0}/{@code O}/{@code 1}/{@code I}。
     * 本行是绕过值对象直接写库的种子数据，用 {@code 1} 会让货主列表在读到它时按「库里的前缀不合规」
     * 当场失败——那条约束是刻意的，脏数据该在读取时暴露，种子因此必须是合规值。
     */
    private static final String OWNER_CODE = "OWNER-UMT";
    private static final String OWNER_NAME = "集成测试货主";
    private static final String OWNER_SKU_PREFIX = "UMTX";

    /** 错误码来自 {@code ErrorCode}：40000 参数问题，40300 无权访问，40400 不存在，40900 状态不允许。 */
    private static final int BAD_REQUEST_CODE = 40000;
    private static final int FORBIDDEN_CODE = 40300;
    private static final int NOT_FOUND_CODE = 40400;
    /** 自我保护用 409 段位：入参合法，被拒绝的是它造成的状态；本批不给单独的码（见分配角色用例的说明）。 */
    private static final int SELF_BUILTIN_ROLE_REQUIRED_CODE = 40900;

    /** 语句计数器，全类共用一个实例：拦截器挂在一个共享的 SqlSessionFactory 上。 */
    private static final StatementCounter STATEMENT_COUNTER = new StatementCounter();

    /** 拦截器只能挂一次：挂两次会把每条语句数成两条。上下文在测试类之间复用，因此用静态标记。 */
    private static boolean counterInstalled;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private UserQueryService userQueryService;

    /** 直接断言 Sa-Token 拿到的权限列表：证明「分配了角色 → 就有了该角色的权限点」。 */
    @Autowired
    private StpInterface stpInterface;

    @BeforeEach
    void prepareAccounts() {
        if (!counterInstalled) {
            // 运行期挂载即可：MyBatis 在每次创建 Executor 时读取插件链，挂载后的下一次请求就会生效
            sqlSessionFactory.getConfiguration().addInterceptor(STATEMENT_COUNTER);
            counterInstalled = true;
        }
        STATEMENT_COUNTER.reset();

        Long operatorId = seedAccount(OPERATOR_LOGIN_NAME, "用户管理操作员", "user-mgmt-operator@example.com");
        Long nobodyId = seedAccount(NO_PERMISSION_LOGIN_NAME, "用户管理无权限", "user-mgmt-nobody@example.com");
        // 超管权限由鉴权时动态展开，这里不写 sys_role_menu：那正是规格禁止的做法
        link(operatorId, seedRole("SUPER_ADMIN", "超级管理员"));
        link(nobodyId, seedRole(NO_PERMISSION_ROLE, "测试用无权限角色"));
        seedOwner();
    }

    @Test
    @DisplayName("链路闭合：建角色 → 建用户 → 分配角色 → 该用户登录后拿到该角色与它授权的权限点")
    void shouldCloseRoleUserPermissionChain() throws Exception {
        String token = consoleToken();
        String roleCode = "UMT_READER";

        // ① 建角色，并勾上 V027 新加的列表权限点
        String roleId = roleIdOf(createRole(token, roleCode, "用户读权限", List.of(menuId("system:user:list"))));

        // ② 建用户：先不给角色，证明后面的权限真的来自这次分配
        String userId = createdUserId(createUser(token, new CreateUserPayload(
                "umt-reader", "用户读权限账号", "STAFF", null, "umt-reader@example.com",
                null, INITIAL_PASSWORD, List.of())));

        // ③ 分配角色
        JsonNode assigned = putRoles(token, userId, List.of(roleId));
        assertThat(assigned.path("code").asInt()).as("分配角色失败：%s", assigned).isZero();

        // ④ 该用户登录后，登录响应里的角色就是刚分配的那个
        JsonNode login = login("umt-reader", INITIAL_PASSWORD);
        assertThat(login.path("code").asInt()).as("新账号登录失败：%s", login).isZero();
        List<String> roles = texts(login.path("data").path("roles"));
        assertThat(roles).containsExactly(roleCode);

        // ⑤ 该角色勾选的权限点真的落到这个人的权限列表里：这才是「按角色获得权限」的证据。
        // 只看登录响应里的角色编码只能证明关联写进去了，证明不了鉴权侧会用它。
        assertThat(stpInterface.getPermissionList(Long.parseLong(userId), "login"))
                .as("分配了勾选 system:user:list 的角色，鉴权侧应当给出该权限点")
                .contains("system:user:list");
    }

    @Test
    @DisplayName("列表：分页信封正确，角色与货主名称随行带出，主体类型与状态可筛选")
    void shouldListUsersWithRolesAndOwnerName() throws Exception {
        String token = consoleToken();
        String roleId = roleIdOf(createRole(token, "UMT_LIST", "列表用例角色", List.of()));
        Long ownerId = ownerId(OWNER_CODE);

        String staffId = createdUserId(createUser(token, new CreateUserPayload(
                "umt-staff-lister", "列表员工", "STAFF", null, "umt-staff-lister@example.com",
                null, INITIAL_PASSWORD, List.of(roleId))));
        // 货主用户只需建出来，断言走列表查询：它的标识本身没有额外信息
        createdUserId(createUser(token, new CreateUserPayload(
                "umt-owner-lister", "列表货主用户", "OWNER", Long.toString(ownerId), null,
                null, INITIAL_PASSWORD, List.of())));
        String disabledId = createdUserId(createUser(token, new CreateUserPayload(
                "umt-disabled-lister", "列表停用用户", "STAFF", null, "umt-disabled-lister@example.com",
                null, INITIAL_PASSWORD, List.of())));
        // 停用属于 2.1c，本批没有接口，只能直接改库构造这个状态
        jdbcTemplate.update("UPDATE sys_user SET status = 'DISABLED' WHERE id = ?", Long.parseLong(disabledId));

        JsonNode page = getJson(token, "/api/console/users?keyword=lister&pageNo=1&pageSize=2").path("data");

        assertThat(page.path("pageNo").asInt()).isEqualTo(1);
        assertThat(page.path("pageSize").asInt()).isEqualTo(2);
        assertThat(page.path("total").asInt()).as("三条登录名都含 lister").isEqualTo(3);
        assertThat(page.path("records")).hasSize(2);

        JsonNode staffRow = getJson(token, "/api/console/users?keyword=umt-staff-lister").path("data");
        JsonNode staff = staffRow.path("records").get(0);
        assertThat(staff.path("id").asText()).isEqualTo(staffId);
        assertThat(staff.path("subjectType").asText()).isEqualTo("STAFF");
        assertThat(staff.path("status").asText()).isEqualTo("ACTIVE");
        // 内部员工不绑定货主：两个字段都是 null，前端据此显示「—」
        assertThat(staff.path("ownerId").isNull()).isTrue();
        assertThat(staff.path("ownerName").isNull()).isTrue();
        assertThat(texts(staff.path("roles"))).containsExactly("UMT_LIST");

        JsonNode ownerRow = getJson(token, "/api/console/users?keyword=umt-owner-lister").path("data");
        JsonNode owner = ownerRow.path("records").get(0);
        assertThat(owner.path("subjectType").asText()).isEqualTo("OWNER");
        assertThat(owner.path("ownerId").asText()).isEqualTo(Long.toString(ownerId));
        assertThat(owner.path("ownerName").asText())
                .as("货主名称必须随列表带出，否则前端要再拉一次货主列表自己映射")
                .isEqualTo(OWNER_NAME);

        // 按主体类型筛
        JsonNode ownerOnly = getJson(token, "/api/console/users?keyword=lister&subjectType=OWNER").path("data");
        assertThat(ownerOnly.path("total").asInt()).isEqualTo(1);
        assertThat(ownerOnly.path("records").get(0).path("loginName").asText()).isEqualTo("umt-owner-lister");

        // 按状态筛
        JsonNode disabledOnly = getJson(token, "/api/console/users?keyword=lister&status=DISABLED").path("data");
        assertThat(disabledOnly.path("total").asInt()).isEqualTo(1);
        assertThat(disabledOnly.path("records").get(0).path("loginName").asText()).isEqualTo("umt-disabled-lister");
    }

    @Test
    @DisplayName("列表用例：SQL 固定不超过 4 条，与页内行数无关")
    void shouldQueryUserPageWithFixedNumberOfStatements() {
        // 直接调用用例而不是走 HTTP：HTTP 请求还会经过鉴权链路，而鉴权侧的查询是
        // SaTokenPermissionResolver 的既有行为（每次鉴权 3 条单表查询），混进来会让这条断言
        // 在测「鉴权有多快」而不是「列表有没有 N+1」。含鉴权的实测条数见下一条用例。
        STATEMENT_COUNTER.reset();

        UserPage page = userQueryService.page(1, 20, null, null, null);

        int statements = STATEMENT_COUNTER.count();
        System.out.println("用户列表用例 SQL 条数：" + statements);
        assertThat(statements)
                .as("列表用例执行了 %d 条 SQL（上限 4）：计数 + 分页 + 整页角色，"
                        + "页内有货主账号时再加一条货主名称、全是内部员工时省掉它；"
                        + "逐行补数据会让它随行数增长", statements)
                .isLessThanOrEqualTo(4);
        assertThat(page.records()).as("用例返回空页时也应是 0 条而不是 null").isNotNull();
    }

    @Test
    @DisplayName("列表接口全链路 SQL 条数（含鉴权）实测")
    void shouldReportWholeRequestStatementCount() throws Exception {
        String token = consoleToken();

        STATEMENT_COUNTER.reset();
        JsonNode response = getJson(token, "/api/console/users?pageNo=1&pageSize=20");
        int statements = STATEMENT_COUNTER.count();

        System.out.println("用户列表接口全链路 SQL 条数（含鉴权）：" + statements);
        assertThat(response.path("code").asInt()).isZero();
        // 预算 7 = 鉴权 3 条（用户角色 / 角色 / 权限点，见 SaTokenPermissionResolver 的说明）
        // + 列表最多 4 条（计数 / 分页 / 整页角色 / 页内有货主账号时的货主名称）。
        // 列表侧一旦退化成逐行查询，这里立刻多出 n 条，而这条测试是唯一能看见它的地方。
        assertThat(statements)
                .as("全链路执行了 %d 条 SQL（预算 7 = 鉴权 3 + 列表最多 4）", statements)
                .isLessThanOrEqualTo(7);
    }

    @Test
    @DisplayName("详情：返回列表字段，外加邮箱、企业微信 userid、覆盖值（为空）与角色标识")
    void shouldReturnUserDetail() throws Exception {
        String token = consoleToken();
        String roleId = roleIdOf(createRole(token, "UMT_DETAIL", "详情用例角色", List.of()));
        String userId = createdUserId(createUser(token, new CreateUserPayload(
                "umt-detail", "详情用户", "STAFF", null, "umt-detail@example.com",
                "wx_umt_detail", INITIAL_PASSWORD, List.of(roleId))));

        JsonNode detail = getJson(token, "/api/console/users/" + userId).path("data");

        assertThat(detail.path("id").asText()).isEqualTo(userId);
        assertThat(detail.path("loginName").asText()).isEqualTo("umt-detail");
        assertThat(detail.path("displayName").asText()).isEqualTo("详情用户");
        assertThat(detail.path("subjectType").asText()).isEqualTo("STAFF");
        assertThat(detail.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(detail.path("email").asText()).isEqualTo("umt-detail@example.com");
        assertThat(detail.path("wecomUserId").asText()).isEqualTo("wx_umt_detail");
        // 未配置覆盖值时必须原样是 null：继承角色默认值的判断在前端，后端不能替它挑一个值
        assertThat(detail.path("dataScopeType").isNull()).isTrue();
        assertThat(detail.path("createdAt").asText()).isNotBlank();
        assertThat(detail.path("lastLoginAt").isNull()).as("刚建的账号还没登录过").isTrue();
        assertThat(texts(detail.path("roles"))).containsExactly("UMT_DETAIL");
        assertThat(texts(detail.path("roleIds"))).containsExactly(roleId);
    }

    @Test
    @DisplayName("详情：用户不存在返回 404 段错误码，HTTP 仍为 200")
    void shouldRejectMissingUserDetail() throws Exception {
        JsonNode response = getJson(consoleToken(), "/api/console/users/999999");

        // 业务结果一律 HTTP 200，由 code 表达；只有 401 与 403 用状态码
        assertThat(response.path("code").asInt()).isEqualTo(NOT_FOUND_CODE);
        assertThat(response.path("message").asText()).isEqualTo("用户不存在");
    }

    @Test
    @DisplayName("分配角色是替换语义：两个换成一个只剩一个，再提交空数组则清空全部")
    void shouldReplaceRolesInsteadOfAppending() throws Exception {
        String token = consoleToken();
        String roleA = roleIdOf(createRole(token, "UMT_REPLACE_A", "替换用例角色 A", List.of()));
        String roleB = roleIdOf(createRole(token, "UMT_REPLACE_B", "替换用例角色 B", List.of()));
        String userId = createdUserId(createUser(token, new CreateUserPayload(
                "umt-replace", "替换用例用户", "STAFF", null, "umt-replace@example.com",
                null, INITIAL_PASSWORD, List.of(roleA, roleB))));

        assertThat(roleCodesOfUser(userId)).containsExactlyInAnyOrder("UMT_REPLACE_A", "UMT_REPLACE_B");

        assertThat(putRoles(token, userId, List.of(roleB)).path("code").asInt()).isZero();
        // 替换而不是追加：只提交 B 时 A 必须消失，否则「提交少两个」会被悄悄当成「只加不减」
        assertThat(roleCodesOfUser(userId)).containsExactly("UMT_REPLACE_B");

        // 空数组是合法入参：移除全部角色（登录后由引导页拦住，不是未授权状态）
        assertThat(putRoles(token, userId, List.of()).path("code").asInt()).isZero();
        assertThat(roleCodesOfUser(userId)).isEmpty();
    }

    @Test
    @DisplayName("分配角色：角色不存在整单拒绝，原有角色一个不丢")
    void shouldRejectUnknownRoleAndKeepExistingRoles() throws Exception {
        String token = consoleToken();
        String roleId = roleIdOf(createRole(token, "UMT_KEEP", "保留用例角色", List.of()));
        String userId = createdUserId(createUser(token, new CreateUserPayload(
                "umt-keep", "保留用例用户", "STAFF", null, "umt-keep@example.com",
                null, INITIAL_PASSWORD, List.of(roleId))));

        JsonNode response = putRoles(token, userId, List.of("999999"));

        assertThat(response.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(response.path("message").asText()).isEqualTo("指定的角色不存在");
        assertThat(roleCodesOfUser(userId))
                .as("整单拒绝：先删后插若在失败前执行过，原有角色会消失")
                .containsExactly("UMT_KEEP");
    }

    @Test
    @DisplayName("分配角色：省略 roleIds 字段被拒绝，原有角色不变")
    void shouldRejectMissingRoleIdsField() throws Exception {
        String token = consoleToken();
        String roleId = roleIdOf(createRole(token, "UMT_MISSING_FIELD", "缺字段用例角色", List.of()));
        String userId = createdUserId(createUser(token, new CreateUserPayload(
                "umt-missing-field", "缺字段用例用户", "STAFF", null, "umt-missing-field@example.com",
                null, INITIAL_PASSWORD, List.of(roleId))));

        JsonNode response = putJson(token, "/api/console/users/" + userId + "/roles", "{}");

        // 省略字段与空数组在 HTTP 上都能表示「没有角色」，但前者更可能是前端没想清楚要提交什么，
        // 而它的后果是悄悄清空这个人的全部角色
        assertThat(response.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(roleCodesOfUser(userId)).containsExactly("UMT_MISSING_FIELD");
    }

    @Test
    @DisplayName("分配角色：把自己最后的初始角色换掉被拒绝，超管角色仍在")
    void shouldRejectRemovingOwnBuiltinRole() throws Exception {
        String token = consoleToken();
        long operatorId = userId(OPERATOR_LOGIN_NAME);

        JsonNode response = putRoles(token, Long.toString(operatorId), List.of());

        // 409 段位而不是 40000：入参合法，被拒绝的是它造成的状态
        assertThat(response.path("code").asInt()).isEqualTo(SELF_BUILTIN_ROLE_REQUIRED_CODE);
        assertThat(response.path("message").asText()).isEqualTo("不能移除自己最后的内置角色");
        assertThat(roleCodesOfUser(Long.toString(operatorId)))
                .as("拒绝时不得动数据：超管角色一旦被删，界面就没有恢复入口了")
                .contains("SUPER_ADMIN");
    }

    @Test
    @DisplayName("无权限用户：列表与分配角色都返回 HTTP 403，且角色关联不变")
    void shouldRejectEndpointsWithoutPermission() throws Exception {
        // 被操作的对象由操作人建好，避免 403 与「对象不存在」混在一起
        String operatorToken = consoleToken();
        String roleId = roleIdOf(createRole(operatorToken, "UMT_DENIED", "无权限用例角色", List.of()));
        String userId = createdUserId(createUser(operatorToken, new CreateUserPayload(
                "umt-denied-target", "被保护的用户", "STAFF", null, "umt-denied-target@example.com",
                null, INITIAL_PASSWORD, List.of(roleId))));

        String token = login(NO_PERMISSION_LOGIN_NAME).path("data").path("token").asText();
        mockMvc.perform(get("/api/console/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));
        mockMvc.perform(put("/api/console/users/" + userId + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AssignRolesPayload(List.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        // 403 之外还要证明业务逻辑没有执行：只有「先拦后执行」才叫未授权接口不执行业务逻辑
        assertThat(roleCodesOfUser(userId)).containsExactly("UMT_DENIED");
    }

    /** 用操作人账号登录取令牌；未改密时这里会拿不到可用令牌，属于测试自身的前提失败。 */
    private String consoleToken() throws Exception {
        JsonNode login = login(OPERATOR_LOGIN_NAME, PASSWORD);
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

    private JsonNode createRole(String token, String code, String name, List<Long> menuIds) throws Exception {
        // 请求体里的标识按契约以字符串传：JS 拿 Long 会丢精度，后端两种写法都得收
        List<String> menuIdTexts = menuIds.stream().map(String::valueOf).toList();
        return postJson(token, "/api/console/roles",
                objectMapper.writeValueAsString(new CreateRolePayload(code, name, menuIdTexts)));
    }

    private JsonNode createUser(String token, CreateUserPayload payload) throws Exception {
        return postJson(token, "/api/console/users", objectMapper.writeValueAsString(payload));
    }

    private JsonNode putRoles(String token, String userId, List<String> roleIds) throws Exception {
        return putJson(token, "/api/console/users/" + userId + "/roles",
                objectMapper.writeValueAsString(new AssignRolesPayload(roleIds)));
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

    /** 取建角色响应里的标识；失败时直接失败，避免后续断言被 null 带偏。 */
    private static String roleIdOf(JsonNode createdRole) {
        assertThat(createdRole.path("code").asInt()).as("建角色失败：%s", createdRole).isZero();
        return createdRole.path("data").path("id").asText();
    }

    /** 取建号响应里的标识；失败时直接失败，理由同上。 */
    private static String createdUserId(JsonNode createdUser) {
        assertThat(createdUser.path("code").asInt()).as("建号失败：%s", createdUser).isZero();
        return createdUser.path("data").path("id").asText();
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    /** 取 {@code V027} 定义的权限点标识；取不到说明迁移没跑或没生效。 */
    private Long menuId(String perms) {
        return jdbcTemplate.queryForObject("SELECT id FROM sys_menu WHERE perms = ?", Long.class, perms);
    }

    private List<String> roleCodesOfUser(String userId) {
        return jdbcTemplate.queryForList("""
                SELECT r.code
                FROM sys_user_role ur
                         JOIN sys_role r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                ORDER BY r.sort_no, r.id
                """, String.class, Long.parseLong(userId));
    }

    private Long ownerId(String code) {
        return jdbcTemplate.queryForObject("SELECT id FROM owner WHERE code = ?", Long.class, code);
    }

    /**
     * 造一个货主：用户列表要显示货主名称，而货主表由 {@code V001} 建立、当前没有接口能建。
     *
     * <p>用 {@code WHERE NOT EXISTS} 而不是 {@code ON CONFLICT}：{@code ux_owner_code} 是全量唯一索引，
     * 而这里的目标是「不存在就补一个」，重复执行时插入零行是正常结果。
     */
    private void seedOwner() {
        jdbcTemplate.update("""
                INSERT INTO owner (code, name, sku_prefix, owner_type, settlement_entity, country, city, address_line)
                SELECT ?, ?, ?, 'THIRD_PARTY', '集成测试结算主体', 'CN', '上海', '测试路 1 号'
                WHERE NOT EXISTS (SELECT 1 FROM owner WHERE code = ?)
                """, OWNER_CODE, OWNER_NAME, OWNER_SKU_PREFIX, OWNER_CODE);
    }

    /** 按编码取角色标识；不存在时按给定名称创建。 */
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

    /**
     * 建角色请求体的最小映射。
     *
     * <p>不复用生产侧的 {@code CreateRoleRequest}：用同一个类型序列化再反序列化，会掩盖
     * 字段名被改而测试仍然通过的情况——接口契约要按「HTTP 上的 JSON」来验证。
     *
     * @param code    角色编码
     * @param name    角色名称
     * @param menuIds 勾选的菜单与权限点标识，按契约以字符串传
     */
    private record CreateRolePayload(String code, String name, List<String> menuIds) {
    }

    /**
     * 建号请求体的最小映射，理由同上。
     *
     * <p>{@code ownerId} 用 {@code String}：契约要求标识在 JSON 里是字符串，
     * 而后端两种写法都得收。{@code email} 与 {@code wecomUserId} 可为 {@code null}。
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
     * 分配角色请求体的最小映射，理由同上。
     *
     * @param roleIds 替换后的角色标识，按契约以字符串传
     */
    private record AssignRolesPayload(List<String> roleIds) {
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

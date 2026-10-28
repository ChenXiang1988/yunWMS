package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 货主主账号开通的端到端验证：{@code POST /api/console/owners/{id}/account}。
 *
 * <p>要证的是单测证不了的几件事：
 * <ul>
 *   <li>开出来的号<b>真能登录</b>，且返回 {@code mustChangePassword = true}；</li>
 *   <li>它<b>没有拿到任何角色</b>：{@code sys_user_role} 一条都没有。给货主账号种一个 {@code builtin}
 *       角色会让它按 {@code builtin} 动态展开全部权限点，等于平台超管；</li>
 *   <li>待改密拦截在这条链路上生效：用该账号调受保护接口得 40104（HTTP 401），改密之后不再是 401；</li>
 *   <li>两种 409 的文案确实不同：重复开号说「已开通主账号」并带出登录名，登录名被别的货主占用时说
 *       「已被其他账号占用」；</li>
 *   <li>被拒绝的请求<b>不留下账号</b>——建号在对端的独立事务里提交，事后无法回退；</li>
 *   <li>{@code system:owner:account} 真被接口消费：只有其他货主权限点的账号调用得 403 且账号未落库；</li>
 *   <li>账号的<b>归属在接口层不可改绑</b>：编辑货主的请求体里没有 {@code ownerId}，带上它是 40000。</li>
 * </ul>
 *
 * <p>操作人不用种子账号 {@code superadmin}（它带着「需先改密」标记，改它又会污染
 * {@code BootstrapIntegrationTest} 依赖的初始状态），而是用 SQL 造一个挂着内置超管角色的已改密账号：
 * 超管权限在鉴权时动态展开，因此它天然拥有 {@code V033} / {@code V036} / {@code V037} 的权限点。
 *
 * <p>用例之间不隔离数据（同一个测试 JVM 共用一个库），因此每个用例用自己的 SKU 前缀与业务键。
 */
@DisplayName("货主主账号开通")
class OwnerAccountProvisionIntegrationTest extends AbstractDataScopeIntegrationTest {

    /** 操作人账号：已改密、已启用，挂内置超管角色。 */
    private static final String OPERATOR_LOGIN_NAME = "owner-account-operator";
    /** 持有货主列表 / 建档 / 编辑权限、但没有开号权限的账号。 */
    private static final String NO_ACCOUNT_PERMISSION_LOGIN_NAME = "owner-account-no-permission";
    /** 无开号权限的角色编码，用例自造；迁移里除超管外不预置角色。 */
    private static final String NO_ACCOUNT_PERMISSION_ROLE = "TEST_OWNER_NO_ACCOUNT";

    /** 开号时设定的初始密码，需满足密码策略（8–20 位且含大写、小写与数字）。 */
    private static final String INITIAL_PASSWORD = "Owner@1234";
    /** 首次登录后改成的密码。 */
    private static final String NEW_PASSWORD = "Owner@5678";
    /** 待改密状态下访问业务接口的错误码（{@code ErrorCode.MUST_CHANGE_PASSWORD}）。 */
    private static final int MUST_CHANGE_PASSWORD_CODE = 40104;

    @BeforeEach
    void prepareAccounts() {
        link(seedStaff(OPERATOR_LOGIN_NAME, "开号操作员"), superAdminRole());

        long noPermissionRoleId = seedRole(NO_ACCOUNT_PERMISSION_ROLE, "测试用货主无开号权限角色");
        link(seedStaff(NO_ACCOUNT_PERMISSION_LOGIN_NAME, "无开号权限"), noPermissionRoleId);
        // 数据范围给 ALL：本类验证的是「缺哪个权限点就不行」，不是数据范围。角色默认的 CUSTOM
        // 空明细按规格等价于「无权限」，会把「能读到这条货主」变成 404，测到的就不是权限点了
        jdbcTemplate.update("UPDATE sys_role SET data_scope_type = 'ALL', status = 'ACTIVE' WHERE id = ?",
                noPermissionRoleId);
        // 只放行列表 / 建档 / 编辑三个点：开号单独成点（V037），这里要验证的是「缺它就不行」
        jdbcTemplate.update("""
                INSERT INTO sys_role_menu (role_id, menu_id)
                SELECT ?, id FROM sys_menu
                WHERE perms IN ('system:owner:list', 'system:owner:create', 'system:owner:update')
                ON CONFLICT (role_id, menu_id) DO NOTHING
                """, noPermissionRoleId);
    }

    @Test
    @DisplayName("开号成功：登录名取货主编码、账号待改密且无任何角色，用该账号能登录")
    void shouldCreateLoginableAccountWithoutRoles() throws Exception {
        String token = consoleToken();
        JsonNode owner = createOwner(token, "开号甲OAAC2", "AQA2", "ACTIVE");
        String ownerId = owner.path("id").asText();
        String ownerCode = owner.path("code").asText();

        JsonNode created = openAccount(token, ownerId, "张三", INITIAL_PASSWORD);

        assertThat(created.path("code").asInt()).as("开号失败：%s", created).isZero();
        // 标识以字符串返回：超过 2^53 的 Long 在 JS 里会丢精度
        assertThat(created.path("data").path("accountId").asText()).isNotBlank();
        assertThat(created.path("data").path("loginName").asText())
                .as("登录名必须由系统按货主系统编码生成")
                .isEqualTo(ownerCode);

        Long operatorId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE lower(login_name) = lower(?)", Long.class, OPERATOR_LOGIN_NAME);
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT subject_type, owner_id, display_name, must_change_password, status,
                       password_hash, email, wecom_user_id, created_by
                FROM sys_user WHERE lower(login_name) = lower(?)
                """, ownerCode);
        assertThat(row.get("subject_type")).as("货主账号的主体类型必须是 OWNER").isEqualTo("OWNER");
        assertThat(((Number) row.get("owner_id")).longValue()).isEqualTo(Long.parseLong(ownerId));
        assertThat(row.get("display_name")).isEqualTo("张三");
        assertThat(row.get("must_change_password")).as("规格：初始密码必须首次登录修改").isEqualTo(true);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        // 落库的必须是哈希：明文一旦进库，改密也换不回已经外流的记录
        assertThat(String.valueOf(row.get("password_hash"))).startsWith("$2a$").doesNotContain(INITIAL_PASSWORD);
        // 规格：账号本身 MUST NOT 保存手机号；邮箱同属联系信息，也没有来源可填
        assertThat(row.get("email")).isNull();
        assertThat(row.get("wecom_user_id")).isNull();
        assertThat(row.get("created_by")).isEqualTo(operatorId);

        assertThat(countUserRoles(ownerCode))
                .as("货主账号不得持有任何角色：内置角色会让它按 builtin 展开全部权限点，等于平台超管")
                .isZero();

        JsonNode login = loginWithPassword(ownerCode, INITIAL_PASSWORD);
        assertThat(login.path("code").asInt()).as("开出来的号必须能登录：%s", login).isZero();
        assertThat(login.path("data").path("mustChangePassword").asBoolean())
                .as("初始密码只够换一次登录")
                .isTrue();
        assertThat(login.path("data").path("subjectType").asText()).isEqualTo("OWNER");
        assertThat(login.path("data").path("ownerId").asText()).isEqualTo(ownerId);
        assertThat(login.path("data").path("roles")).isEmpty();
    }

    @Test
    @DisplayName("开号后未改密：调受保护接口得 40104；改密后不再被拦（权限点属 3.4/4.4，因此是 403）")
    void shouldBlockBusinessEndpointsUntilPasswordChanged() throws Exception {
        String token = consoleToken();
        JsonNode owner = createOwner(token, "开号乙OAAC3", "AQA3", "ACTIVE");
        String ownerId = owner.path("id").asText();
        String ownerCode = owner.path("code").asText();
        assertThat(openAccount(token, ownerId, "李四", INITIAL_PASSWORD).path("code").asInt()).isZero();

        String initialToken = loginWithPassword(ownerCode, INITIAL_PASSWORD).path("data").path("token").asText();

        // 待改密状态下除改密接口外一律 40104：HTTP 状态是 401，错误码才是区分「去登录页 / 去改密页」的依据
        mockMvc.perform(get("/api/console/owners").header(HttpHeaders.AUTHORIZATION, "Bearer " + initialToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(MUST_CHANGE_PASSWORD_CODE));

        assertThat(changePassword(initialToken, INITIAL_PASSWORD, NEW_PASSWORD).path("code").asInt())
                .as("待改密状态下改密接口必须放行")
                .isZero();

        JsonNode relogin = loginWithPassword(ownerCode, NEW_PASSWORD);
        assertThat(relogin.path("code").asInt()).isZero();
        assertThat(relogin.path("data").path("mustChangePassword").asBoolean()).isFalse();

        // 改密之后不再被 40104 拦：货主侧的角色与权限点属 3.4/4.4，此刻它一个权限点都没有，
        // 因此这里能拿到的是 403（无权访问），而不是「未登录」或「需先改密」
        String changedToken = relogin.path("data").path("token").asText();
        mockMvc.perform(get("/api/console/owners").header(HttpHeaders.AUTHORIZATION, "Bearer " + changedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));
    }

    @Test
    @DisplayName("重复开号：40900，提示带该货主已有的登录名，库里仍只有一条账号")
    void shouldRejectDuplicateProvision() throws Exception {
        String token = consoleToken();
        JsonNode owner = createOwner(token, "开号丙OAAC4", "AQA4", "ACTIVE");
        String ownerId = owner.path("id").asText();
        String ownerCode = owner.path("code").asText();
        assertThat(openAccount(token, ownerId, "王五", INITIAL_PASSWORD).path("code").asInt()).isZero();

        JsonNode duplicated = openAccount(token, ownerId, "王五", INITIAL_PASSWORD);

        assertThat(duplicated.path("code").asInt()).isEqualTo(CONFLICT_CODE);
        assertThat(duplicated.path("message").asText())
                .as("管理员要靠这句话确认「刚才那次其实成功了」")
                .contains("已开通主账号")
                .contains(ownerCode);
        assertThat(countUsers(ownerCode)).as("同一货主最多一个主账号").isEqualTo(1);
    }

    @Test
    @DisplayName("草稿货主开号：40900，提示带当前状态，库中不留账号")
    void shouldRejectDraftOwner() throws Exception {
        String token = consoleToken();
        JsonNode owner = createOwner(token, "开号丁OAAC5", "AQA5", "DRAFT");
        String ownerId = owner.path("id").asText();
        String ownerCode = owner.path("code").asText();

        JsonNode rejected = openAccount(token, ownerId, "赵六", INITIAL_PASSWORD);

        assertThat(rejected.path("code").asInt()).isEqualTo(CONFLICT_CODE);
        assertThat(rejected.path("message").asText())
                .as("必须说清当前状态，否则管理员不知道该先做什么")
                .contains("DRAFT")
                .contains("ACTIVE");
        assertThat(countUsers(ownerCode)).as("被拒绝的开号不得落库").isZero();
    }

    @Test
    @DisplayName("登录名被其他货主占用：40900，提示是「其他账号占用」，与重复开号的文案不同")
    void shouldRejectLoginNameOwnedByAnotherOwner() throws Exception {
        String token = consoleToken();
        JsonNode target = createOwner(token, "开号戊OAAC6", "AQA6", "ACTIVE");
        JsonNode other = createOwner(token, "开号己OAAC7", "AQA7", "ACTIVE");
        String ownerCode = target.path("code").asText();

        // 正常路径造不出这种数据：某货主的登录名指向了另一个货主。它只能由绕过规则的写入产生，
        // 而本用例要证明的正是「真出现时提示的是另一句话」，所以直接按表结构把它写进去。
        // 登录名故意写成小写：唯一索引建在 lower(login_name) 上，反查若按原值比较就找不到这一行，
        // 那样会绕过「被其他货主占用」的判断、直接撞唯一索引得到 40901——两种结果的区别正是这条用例要钉住的
        seedOwnerAccount(ownerCode.toLowerCase(Locale.ROOT), "被占用的登录名",
                Long.parseLong(other.path("id").asText()));

        JsonNode rejected = openAccount(token, target.path("id").asText(), "钱七", INITIAL_PASSWORD);

        assertThat(rejected.path("code").asInt()).isEqualTo(CONFLICT_CODE);
        assertThat(rejected.path("message").asText())
                .contains("其他账号占用")
                .contains(ownerCode)
                .as("两种 409 的处置动作不同，文案必须能区分")
                .doesNotContain("已开通主账号");
    }

    @Test
    @DisplayName("无 system:owner:account 的账号调开号：HTTP 403，且账号未落库")
    void shouldRejectWithoutPermission() throws Exception {
        String token = consoleToken();
        JsonNode owner = createOwner(token, "开号庚OAAC8", "AQA8", "ACTIVE");
        String ownerId = owner.path("id").asText();
        String ownerCode = owner.path("code").asText();

        String noPermissionToken = loginToken(NO_ACCOUNT_PERMISSION_LOGIN_NAME);
        // 先证明这个账号能进这条路线的相邻接口：详情需要 system:owner:list，它持有该点
        assertThat(getJson(noPermissionToken, "/api/console/owners/" + ownerId).path("code").asInt()).isZero();

        mockMvc.perform(post("/api/console/owners/" + ownerId + "/account")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + noPermissionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AccountPayload("孙八", INITIAL_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        assertThat(countUsers(ownerCode)).as("被拒绝的开号不得落库").isZero();
    }

    @Test
    @DisplayName("请求体带 loginName 或 phone：40000，且账号未落库")
    void shouldRejectUnknownRequestFields() throws Exception {
        String token = consoleToken();
        JsonNode owner = createOwner(token, "开号辛OAAC9", "AQA9", "ACTIVE");
        String ownerId = owner.path("id").asText();
        String ownerCode = owner.path("code").asText();

        // 登录名由系统按货主编码生成；手机号属联系人档案（3.5）。两者都不是「忽略就好」，
        // 静默忽略会让调用方以为「我指定的登录名 / 手机号生效了」，因此多传即拒
        String withLoginName = """
                {"displayName":"周九","initialPassword":"%s","loginName":"HACKER0001"}
                """.formatted(INITIAL_PASSWORD);
        String withPhone = """
                {"displayName":"周九","initialPassword":"%s","phone":"+86 138 0000 0000"}
                """.formatted(INITIAL_PASSWORD);

        for (String body : new String[]{withLoginName, withPhone}) {
            JsonNode rejected = postJson(token, "/api/console/owners/" + ownerId + "/account", body);
            assertThat(rejected.path("code").asInt())
                    .as("多余字段必须被拒绝，请求体：%s", body)
                    .isEqualTo(BAD_REQUEST_CODE);
        }

        assertThat(countUsers(ownerCode)).as("被拒绝的开号不得落库").isZero();
    }

    @Test
    @DisplayName("货主不存在：40400，提示为「货主不存在」")
    void shouldRejectUnknownOwner() throws Exception {
        JsonNode rejected = openAccount(consoleToken(), "999999", "吴十", INITIAL_PASSWORD);

        assertThat(rejected.path("code").asInt()).isEqualTo(NOT_FOUND_CODE);
        assertThat(rejected.path("message").asText()).isEqualTo("货主不存在");
    }

    @Test
    @DisplayName("主账号归属不可改绑：编辑请求体带 ownerId 得 40000，账号仍挂在原货主名下")
    void shouldRejectOwnerIdInUpdateRequest() throws Exception {
        String token = consoleToken();
        JsonNode owner = createOwner(token, "开号壬OAACA", "AQB2", "ACTIVE");
        String ownerId = owner.path("id").asText();
        String ownerCode = owner.path("code").asText();
        assertThat(openAccount(token, ownerId, "郑十一", INITIAL_PASSWORD).path("code").asInt()).isZero();
        String anotherOwnerId = createOwner(token, "开号癸OAACB", "AQB3", "ACTIVE").path("id").asText();

        // 对照组：同一个请求体去掉 ownerId 必须成功。没有这一步，「带 ownerId 得 40000」可能只是因为
        // 别的字段不合规（少了必填项、字典项不存在），那样测到的就不是「归属不可改绑」
        JsonNode updated = putJson(token, "/api/console/owners/" + ownerId,
                objectMapper.writeValueAsString(updateBody(null, "开号壬OAACA")));
        assertThat(updated.path("code").asInt()).as("对照组应当成功：%s", updated).isZero();

        JsonNode rejected = putJson(token, "/api/console/owners/" + ownerId,
                objectMapper.writeValueAsString(updateBody(anotherOwnerId, "开号壬OAACA")));

        // 归属取自路径变量与账号自身的主体字段，请求体里没有也不接受 ownerId：改绑等于把一个账号
        // 连同它的登录名、密码、数据范围一起搬到另一个货主名下，那是独立用例，不是一个可选字段
        assertThat(rejected.path("code").asInt()).as("编辑不接收 ownerId：%s", rejected).isEqualTo(BAD_REQUEST_CODE);
        assertThat(accountOwnerId(ownerCode)).as("被拒绝的改绑不得留下任何痕迹")
                .isEqualTo(Long.parseLong(ownerId));
    }

    /** 用操作人账号登录取令牌；未改密时这里会拿不到可用令牌，属于测试自身的前提失败。 */
    private String consoleToken() throws Exception {
        return loginToken(OPERATOR_LOGIN_NAME);
    }

    /**
     * 走接口建一个货主。
     *
     * @param token         操作人令牌，需持有 {@code system:owner:create}
     * @param name          客户名称，用例自身保证唯一性标记落在里面
     * @param skuPrefix     SKU 前缀，4 位大写字母或数字且不含 0/O/1/I
     * @param initialStatus 初始状态：{@code DRAFT} 或 {@code ACTIVE}
     * @return 响应里的 {@code data} 节点（含 {@code id} 与 {@code code}）
     */
    private JsonNode createOwner(String token, String name, String skuPrefix, String initialStatus) throws Exception {
        String body = objectMapper.writeValueAsString(new CreateOwnerPayload(
                name, "开号简称", "Account Provision Test", "EXT-" + skuPrefix, skuPrefix, "BRAND",
                "开号测试结算主体", "CN", "CN-GD", "深圳", "南山区", "518000", "科技园路 1 号", null, initialStatus));
        JsonNode response = postJson(token, "/api/console/owners", body);
        assertThat(response.path("code").asInt()).as("建档失败：%s", response).isZero();
        return response.path("data");
    }

    /**
     * 调开通主账号接口。
     *
     * @param token           操作人令牌
     * @param ownerId         货主标识
     * @param displayName     主账号姓名
     * @param initialPassword 初始密码
     * @return 接口响应
     */
    private JsonNode openAccount(String token, String ownerId, String displayName, String initialPassword)
            throws Exception {
        return postJson(token, "/api/console/owners/" + ownerId + "/account",
                objectMapper.writeValueAsString(new AccountPayload(displayName, initialPassword)));
    }

    /** 用指定密码登录，返回原始响应；业务码由调用方断言。 */
    private JsonNode loginWithPassword(String loginName, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PasswordLoginPayload(loginName, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** 改本人密码，返回原始响应。 */
    private JsonNode changePassword(String token, String oldPassword, String newPassword) throws Exception {
        return putJson(token, "/api/auth/password",
                objectMapper.writeValueAsString(new ChangePasswordPayload(oldPassword, newPassword)));
    }

    /**
     * 编辑货主的请求体。
     *
     * <p>用 Map 而不是 record：只为「带不带 {@code ownerId}」造出两份请求体，而记录类型里的 {@code null}
     * 会被 {@code default-property-inclusion: always} 序列化成 {@code "ownerId":null}——显式的
     * {@code null} 依然是一个未知字段，对照组会因此也被拒成 40000，用例就分不清拒绝的原因了。
     *
     * @param ownerId 追加进请求体的货主标识；{@code null} 表示不出现该字段（即正常编辑的契约）
     * @param name    客户名称，其余字段取建档时的合法值
     * @return 可直接序列化的请求体
     */
    private Map<String, Object> updateBody(String ownerId, String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("shortName", "开号简称");
        body.put("nameEn", "Account Provision Test");
        body.put("ownerType", "BRAND");
        body.put("settlementEntity", "开号测试结算主体");
        body.put("country", "CN");
        body.put("province", "CN-GD");
        body.put("city", "深圳");
        body.put("district", "南山区");
        body.put("postalCode", "518000");
        body.put("addressLine", "科技园路 1 号");
        if (ownerId != null) {
            body.put("ownerId", ownerId);
        }
        return body;
    }

    /** 取账号归属的货主，用于断言归属没有被改掉；账号不存在时返回 {@code null}。 */
    private Long accountOwnerId(String loginName) {
        return jdbcTemplate.queryForObject(
                "SELECT owner_id FROM sys_user WHERE lower(login_name) = lower(?)", Long.class, loginName);
    }

    /** 按登录名数账号，用于断言「被拒绝的请求没有留下账号」。 */
    private int countUsers(String loginName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sys_user WHERE lower(login_name) = lower(?)", Integer.class, loginName);
        return count == null ? 0 : count;
    }

    /** 数某账号持有的角色数，用于断言「货主账号一个角色都没有」。 */
    private int countUserRoles(String loginName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM sys_user_role ur
                JOIN sys_user u ON u.id = ur.user_id
                WHERE lower(u.login_name) = lower(?)
                """, Integer.class, loginName);
        return count == null ? 0 : count;
    }

    /**
     * 建档请求体的最小映射。
     *
     * <p>不复用生产侧的 Request 类型：用同一个类型序列化再反序列化，会掩盖字段名被改而测试仍然通过的情况。
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
     * 开号请求体的最小映射：只有两个字段。
     *
     * @param displayName     主账号姓名
     * @param initialPassword 初始密码明文
     */
    private record AccountPayload(String displayName, String initialPassword) {
    }

    /**
     * 登录请求体的最小映射，按「用指定密码登录」的需要定义（基类那份固定用一个测试密码）。
     *
     * @param loginName 登录名
     * @param password  明文密码
     */
    private record PasswordLoginPayload(String loginName, String password) {
    }

    /**
     * 改密请求体的最小映射。
     *
     * @param oldPassword 当前密码
     * @param newPassword 新密码
     */
    private record ChangePasswordPayload(String oldPassword, String newPassword) {
    }
}

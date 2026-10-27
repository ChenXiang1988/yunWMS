package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.auth.domain.port.PasswordHasher;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 货主列表与详情接口的端到端验证：{@code GET /api/console/owners} 与 {@code GET /api/console/owners/{id}}。
 *
 * <p>要证的是单测证不了的几件事：XML 里的筛选条件真的能在 PostgreSQL 上跑通（ILIKE、状态与类型等值、
 * LIMIT/OFFSET）、{@code total} 真的按筛选条件计数而不是本页条数、详情把地址六字段与前缀原文原样带出、
 * {@code V033} 的 {@code system:owner:list} 真的会被接口消费（无该权限的账号拿到 403）、
 * 前缀冲突的提示里真的带着占用方编码，以及列表的 SQL 条数不随货主数量增长。
 *
 * <p>操作人不用种子账号 {@code superadmin}：它带着「需先改密」标记，改它又会污染
 * {@code BootstrapIntegrationTest} 依赖的初始状态（同一个测试 JVM 共用一个库）。这里用 SQL 造一个
 * 挂着内置超管角色的已改密账号——超管权限在鉴权时动态展开，因此它天然拥有 {@code V033} 的权限点。
 *
 * <p>用例之间不隔离数据，因此每个用例用自己的 SKU 前缀与业务键，断言只针对自己造的那几条。
 */
@DisplayName("货主列表与详情")
class OwnerQueryIntegrationTest extends AbstractIntegrationTest {

    /** 操作人账号：已改密、已启用，挂内置超管角色。 */
    private static final String OPERATOR_LOGIN_NAME = "owner-query-operator";
    /** 无任何权限点的账号，用于验证 403。 */
    private static final String NO_PERMISSION_LOGIN_NAME = "owner-query-nobody";
    private static final String PASSWORD = "Admin@1234";
    /** 无权限点角色编码，用例自造；迁移里除超管外不预置角色。 */
    private static final String NO_PERMISSION_ROLE = "TEST_OWNER_QUERY_NO_PERMISSION";

    /** 错误码来自 {@code ErrorCode}：40000 参数问题，40300 无权访问，40400 资源不存在，40905 前缀被占用。 */
    private static final int BAD_REQUEST_CODE = 40000;
    private static final int FORBIDDEN_CODE = 40300;
    private static final int NOT_FOUND_CODE = 40400;
    private static final int SKU_PREFIX_TAKEN_CODE = 40905;

    /**
     * 语句计数器：整个测试 JVM 共用一个 {@code SqlSessionFactory}，因此全类共用一个实例。
     *
     * <p>拦截器只能挂一次，挂两次会把每条语句数成两条，故用一个静态标记保护。
     */
    private static final StatementCounter STATEMENT_COUNTER = new StatementCounter();
    private static boolean counterInstalled;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void prepareAccounts() {
        if (!counterInstalled) {
            // 运行期挂载即可：MyBatis 在每次创建 Executor 时读取插件链，挂载后的下一次请求就会生效
            sqlSessionFactory.getConfiguration().addInterceptor(STATEMENT_COUNTER);
            counterInstalled = true;
        }
        STATEMENT_COUNTER.reset();

        Long operatorId = seedAccount(OPERATOR_LOGIN_NAME, "货主查询操作员", "owner-query-operator@example.com");
        Long nobodyId = seedAccount(NO_PERMISSION_LOGIN_NAME, "货主查询无权限", "owner-query-nobody@example.com");
        link(operatorId, seedRole("SUPER_ADMIN", "超级管理员"));
        link(nobodyId, seedRole(NO_PERMISSION_ROLE, "测试用无权限角色"));
    }

    @Test
    @DisplayName("列表：新建的两个货主都能查到，按关键字、状态、客户类型筛得对，total 按条件计数")
    void shouldListCreatedOwnersWithFilters() throws Exception {
        String token = consoleToken();
        // 两个货主共用同一个名称标记，才能用一条 keyword 覆盖两行；前缀与名称各自唯一
        String marker = "列表演示甲LSTX1";
        JsonNode activeOwner = createOwner(token, ownerBody(marker, "QWAB", "BRAND", "ACTIVE"));
        assertThat(activeOwner.path("code").asInt()).as("建档失败：%s", activeOwner).isZero();
        String activeCode = activeOwner.path("data").path("code").asText();

        JsonNode draftOwner = createOwner(token, ownerBody("列表演示乙LSTX1", "QWAC", "BRAND", "DRAFT"));
        assertThat(draftOwner.path("code").asInt()).as("建档失败：%s", draftOwner).isZero();
        String draftCode = draftOwner.path("data").path("code").asText();

        // 关键字同时匹配编码与名称：用编码查，命中同一行
        JsonNode byCode = getJson(token, "/api/console/owners?keyword=" + activeCode).path("data");
        assertThat(byCode.path("total").asInt()).isEqualTo(1);
        JsonNode row = byCode.path("records").get(0);
        assertThat(row.path("code").asText()).isEqualTo(activeCode);
        assertThat(row.path("name").asText()).isEqualTo(marker);
        assertThat(row.path("ownerType").asText()).isEqualTo("BRAND");
        assertThat(row.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(row.path("id").asText()).as("标识以字符串传输").isNotBlank();
        // 时间必须带时区且是 ISO 8601：前端按它做本地化展示，也按它排序
        assertThat(row.path("updatedAt").asText())
                .as("updatedAt 应为 ISO 8601 带时区（UTC）")
                .matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$");
        // 主负责人与 SKU 数属后续任务，本批不返回字段——返回 null 或 0 会让前端以为「有这列但没数据」
        assertThat(row.has("assignee")).as("主负责人属任务 3.5，不得以 null 占位").isFalse();
        assertThat(row.has("skuCount")).as("SKU 数依赖商品模块，不得以 0 占位").isFalse();

        // 名称关键字命中两行
        JsonNode byMarker = getJson(token, "/api/console/owners?keyword=LSTX1").path("data");
        assertThat(byMarker.path("total").asInt()).as("两个货主共用同一个名称标记，应命中 2 行").isEqualTo(2);

        // 分页：total 是满足条件的总数，不是本页条数
        JsonNode secondPage = getJson(token, "/api/console/owners?keyword=LSTX1&pageNo=2&pageSize=1").path("data");
        assertThat(secondPage.path("pageNo").asInt()).isEqualTo(2);
        assertThat(secondPage.path("pageSize").asInt()).isEqualTo(1);
        assertThat(secondPage.path("total").asInt()).as("翻到第二页时 total 仍是总数").isEqualTo(2);
        assertThat(secondPage.path("records").size()).isEqualTo(1);

        // 状态筛选：草稿只命中乙
        JsonNode drafts = getJson(token, "/api/console/owners?keyword=LSTX1&status=DRAFT").path("data");
        assertThat(drafts.path("total").asInt()).isEqualTo(1);
        assertThat(drafts.path("records").get(0).path("code").asText()).isEqualTo(draftCode);
        assertThat(drafts.path("records").get(0).path("status").asText()).isEqualTo("DRAFT");

        // 客户类型筛选：两个都是 BRAND，换 FORWARDER 必须查不到，否则上面的命中不能证明筛选生效
        JsonNode forwarders = getJson(token, "/api/console/owners?keyword=LSTX1&ownerType=FORWARDER").path("data");
        assertThat(forwarders.path("total").asInt()).isZero();
        assertThat(forwarders.path("records").size()).isZero();
    }

    @Test
    @DisplayName("列表参数：页码为 0、条数为 0 或 201、状态编码未知都返回 40000")
    void shouldRejectInvalidListParameters() throws Exception {
        String token = consoleToken();

        for (String query : new String[]{"pageNo=0", "pageSize=0", "pageSize=201", "status=FOO"}) {
            JsonNode response = getJson(token, "/api/console/owners?" + query);
            // 拒绝而不是静默截断：静默截成 200 时前端会把「只看到 200 条」当成「一共 200 条」
            assertThat(response.path("code").asInt()).as("参数 %s 必须被拒绝：%s", query, response)
                    .isEqualTo(BAD_REQUEST_CODE);
        }

        JsonNode unknownStatus = getJson(token, "/api/console/owners?status=FOO");
        assertThat(unknownStatus.path("message").asText())
                .as("提示里要带上实际提交的状态值，否则用户不知道是哪个值不对")
                .contains("FOO");
    }

    @Test
    @DisplayName("详情：基本信息页签的字段全部返回，含 SKU 前缀与地址六字段")
    void shouldReturnCompleteDetail() throws Exception {
        String token = consoleToken();
        JsonNode created = createOwner(token, ownerBody("详情用例丙DETX2", "QWAD", "FORWARDER", "ACTIVE"));
        assertThat(created.path("code").asInt()).as("建档失败：%s", created).isZero();
        String ownerId = created.path("data").path("id").asText();
        String ownerCode = created.path("data").path("code").asText();

        JsonNode detail = getJson(token, "/api/console/owners/" + ownerId);
        assertThat(detail.path("code").asInt()).as("取详情失败：%s", detail).isZero();
        JsonNode data = detail.path("data");

        assertThat(data.path("id").asText()).isEqualTo(ownerId);
        assertThat(data.path("code").asText()).isEqualTo(ownerCode);
        assertThat(data.path("name").asText()).isEqualTo("详情用例丙DETX2");
        assertThat(data.path("nameEn").asText()).isEqualTo("Integration Test Owner");
        assertThat(data.path("shortName").asText()).isEqualTo("集成测试简称");
        assertThat(data.path("externalCode").asText()).isEqualTo("EXT-QWAD");
        // 前缀是只读字段，详情必须原样回显，编辑表单据此展示「已锁定」
        assertThat(data.path("skuPrefix").asText()).isEqualTo("QWAD");
        assertThat(data.path("ownerType").asText()).isEqualTo("FORWARDER");
        assertThat(data.path("settlementEntity").asText()).isEqualTo("集成测试结算主体");
        assertThat(data.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(data.path("country").asText()).isEqualTo("CN");
        assertThat(data.path("province").asText()).isEqualTo("CN-GD");
        assertThat(data.path("city").asText()).isEqualTo("深圳");
        assertThat(data.path("district").asText()).isEqualTo("南山区");
        assertThat(data.path("postalCode").asText()).isEqualTo("518000");
        assertThat(data.path("addressLine").asText()).isEqualTo("科技园路 1 号");
        assertThat(data.path("remark").asText()).isEqualTo("集成测试备注");
        assertThat(data.path("createdAt").asText()).matches("^\\d{4}-\\d{2}-\\d{2}T.*Z$");
        assertThat(data.path("updatedAt").asText()).matches("^\\d{4}-\\d{2}-\\d{2}T.*Z$");
        // 联系人、税号、账号三个页签属 3.5 / 3.6 / 3.3：不返回占位字段
        assertThat(data.has("contacts")).isFalse();
        assertThat(data.has("taxRegistrations")).isFalse();
        assertThat(data.has("accounts")).isFalse();
        assertThat(data.has("assignee")).isFalse();
        assertThat(data.has("skuCount")).isFalse();
    }

    @Test
    @DisplayName("详情：货主不存在返回 40400，文案与编辑接口一致")
    void shouldRejectMissingOwnerDetail() throws Exception {
        JsonNode response = getJson(consoleToken(), "/api/console/owners/999999");

        // 业务结果一律 HTTP 200，由 code 表达；只有 401 与 403 用状态码
        assertThat(response.path("code").asInt()).isEqualTo(NOT_FOUND_CODE);
        assertThat(response.path("message").asText()).isEqualTo("货主不存在");
    }

    @Test
    @DisplayName("无 system:owner:list 权限的账号调列表与详情：HTTP 403 且业务码 40300")
    void shouldRejectListWithoutPermission() throws Exception {
        // 先造一条确实存在的货主：否则详情那条 403 可能只是「这个 id 本来就不存在」的另一种表现
        JsonNode created = createOwner(consoleToken(),
                ownerBody("无权限用例丁PERMX4", "QWAF", "BRAND", "ACTIVE"));
        assertThat(created.path("code").asInt()).as("建档失败：%s", created).isZero();
        String ownerId = created.path("data").path("id").asText();

        String token = login(NO_PERMISSION_LOGIN_NAME).path("data").path("token").asText();

        mockMvc.perform(get("/api/console/owners")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        // 列表与详情共用同一个权限点：能看列表的人本来就能点进详情，所以两者都必须被拦
        mockMvc.perform(get("/api/console/owners/" + ownerId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));
    }

    @Test
    @DisplayName("前缀冲突：40905 的提示里带占用方编码，占用方被软删除后仍然指出它")
    void shouldRevealOccupierCodeOnPrefixConflict() throws Exception {
        String token = consoleToken();
        String prefix = "QWAE";

        JsonNode first = createOwner(token, ownerBody("占用方货主OCCX3", prefix, "BRAND", "ACTIVE"));
        assertThat(first.path("code").asInt()).as("建档失败：%s", first).isZero();
        String occupierId = first.path("data").path("id").asText();
        String occupierCode = first.path("data").path("code").asText();

        JsonNode conflict = createOwner(token, ownerBody("抢前缀货主OCCX3", prefix, "BRAND", "ACTIVE"));
        // 40905 而不是泛化的 40900：前端要把提示挂在 SKU 前缀字段上
        assertThat(conflict.path("code").asInt()).isEqualTo(SKU_PREFIX_TAKEN_CODE);
        assertThat(conflict.path("message").asText())
                .as("规格要求「拒绝保存并指出占用方编码」")
                .isEqualTo("SKU 前缀已被占用：" + prefix + "（占用方 " + occupierCode + "）");

        // 软删除不释放前缀：ux_owner_sku_prefix 是全表唯一索引，反查因此也必须按全表口径走。
        // 若反查带上 deleted_at IS NULL，这里会退回「只说前缀被占用」——用户对着一个界面上看不见的
        // 货主无从自查，只能反复试别的前缀
        jdbcTemplate.update("UPDATE owner SET deleted_at = now() WHERE id = ?", Long.parseLong(occupierId));

        JsonNode conflictAfterDelete = createOwner(token, ownerBody("抢前缀货主2OCCX3", prefix, "BRAND", "ACTIVE"));
        assertThat(conflictAfterDelete.path("code").asInt()).isEqualTo(SKU_PREFIX_TAKEN_CODE);
        assertThat(conflictAfterDelete.path("message").asText())
                .as("占用方已软删除，仍要指出它是谁——前缀确实还被它占着")
                .contains("（占用方 " + occupierCode + "）");

        // 已删除的货主按「不存在」同口径：详情查不到
        assertThat(getJson(token, "/api/console/owners/" + occupierId).path("code").asInt())
                .isEqualTo(NOT_FOUND_CODE);
    }

    @Test
    @DisplayName("列表用例：SQL 条数固定，不随货主数量增长")
    void shouldQueryListWithFixedNumberOfStatements() throws Exception {
        String token = consoleToken();

        // 先取一次令牌，把登录链路的语句排除在计数之外
        STATEMENT_COUNTER.reset();

        JsonNode response = getJson(token, "/api/console/owners?pageNo=1&pageSize=200");
        assertThat(response.path("code").asInt()).isZero();
        int statements = STATEMENT_COUNTER.count();
        // 打印实测条数，供交付时核对
        System.out.println("货主列表（200 条一页）SQL 条数：" + statements);

        // 换成一条什么都匹配不到的关键字再数一次：行数变了而条数没变，才证明查询与行数无关
        STATEMENT_COUNTER.reset();
        JsonNode emptyPage = getJson(token, "/api/console/owners?keyword=NobodyMatchesThisKeyword");
        assertThat(emptyPage.path("data").path("total").asInt()).isZero();
        int emptyStatements = STATEMENT_COUNTER.count();
        System.out.println("货主列表（空结果）SQL 条数：" + emptyStatements);

        // 构成：鉴权 3（用户角色 / 角色 / 权限点，见 SaTokenPermissionResolver）
        //     + 数据范围 3（用户覆盖 / 用户角色 / 角色，见 DataScopeResolver；每请求只解析一次）
        //     + 计数 1 + 分页 1。
        // 逐行查负责人或 SKU 数会让它随行数增长，两条计数不等就是那条 N+1 退化
        assertThat(statements)
                .as("列表全链路执行了 %d 条 SQL，多于「鉴权 3 + 数据范围 3 + 计数 1 + 分页 1」说明引入了逐行查询",
                        statements)
                .isLessThanOrEqualTo(8);
        assertThat(statements)
                .as("少于 2 条说明计数器没挂上，这条断言会空转通过")
                .isGreaterThanOrEqualTo(2);
        assertThat(statements)
                .as("有数据与无数据两次查询的 SQL 条数必须相同（实测 %d 与 %d）", statements, emptyStatements)
                .isEqualTo(emptyStatements);
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

    private JsonNode getJson(String token, String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * 构造一份能通过字典校验的建档请求体：客户类型 BRAND、国家 CN、州省 CN-GD 都是种子里的启用项，
     * 大陆省份要求邮编，因此 POSTAL_CODE 必填。
     *
     * @param name          客户名称，用例自身保证唯一性标记落在里面
     * @param skuPrefix     SKU 前缀，4 位大写字母或数字且不含 0/O/1/I
     * @param ownerType     客户类型
     * @param initialStatus 初始状态
     * @return JSON 请求体
     */
    private String ownerBody(String name, String skuPrefix, String ownerType, String initialStatus)
            throws Exception {
        return objectMapper.writeValueAsString(new CreateOwnerPayload(
                name,
                "集成测试简称",
                "Integration Test Owner",
                "EXT-" + skuPrefix,
                skuPrefix,
                ownerType,
                "集成测试结算主体",
                "CN",
                "CN-GD",
                "深圳",
                "南山区",
                "518000",
                "科技园路 1 号",
                "集成测试备注",
                initialStatus));
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
     * 建档请求体的最小映射。
     *
     * <p>不复用生产侧的 {@code OwnerCreateRequest}：用同一个类型序列化再反序列化，会掩盖字段名被改
     * 而测试仍然通过的情况——接口契约要按「HTTP 上的 JSON」来验证。
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
     * 登录请求体的最小映射，理由同上。
     *
     * @param loginName 登录名
     * @param password  明文密码
     */
    private record LoginPayload(String loginName, String password) {
    }
}

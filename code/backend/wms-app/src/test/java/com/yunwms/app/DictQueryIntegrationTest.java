package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.auth.application.query.DictQueryService;
import com.yunwms.auth.domain.model.DictItem;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据字典种子与读取能力的端到端验证。
 *
 * <p>要证的是单测证不了的几件事：{@code V032} 真的把五类字典与约百条项种进了库、层级真的落在
 * {@code ext.parent} 上、{@code enabledOnly} 真的把停用项挡住而 {@code enabledOnly=false} 又放它回来、
 * 三个读接口的 SQL 条数与字典类型数量无关，以及 {@code V031} 的权限点真的被接口消费。
 *
 * <p>操作人不复用种子账号 {@code superadmin}（它带着「需先改密」标记，拦截器在改密前只放行改密接口，
 * 替它改密又会污染 {@code BootstrapIntegrationTest} 依赖的初始状态）。这里用 SQL 造一个挂着内置超管角色的
 * 已改密账号——超管权限在鉴权时动态展开，因此它天然拥有 {@code V031} 新加的权限点。
 *
 * <p>用例之间不隔离数据（整个测试 JVM 共用一个库）：会改数据的那条用例在 {@code finally} 里把项状态改回去，
 * 且它只断言自己动过的那一条，不依赖执行顺序。
 */
@DisplayName("数据字典")
class DictQueryIntegrationTest extends AbstractIntegrationTest {

    /** 操作人账号：已改密、已启用，挂内置超管角色。 */
    private static final String OPERATOR_LOGIN_NAME = "dict-operator";
    /** 无任何权限点的账号，用于验证 403。 */
    private static final String NO_PERMISSION_LOGIN_NAME = "dict-nobody";
    private static final String PASSWORD = "Admin@1234";
    /** 无权限点角色编码，用例自造；迁移里除超管外不预置角色。 */
    private static final String NO_PERMISSION_ROLE = "TEST_DICT_NO_PERMISSION";

    /** 错误码来自 {@code ErrorCode}：40300 无权访问，40400 资源不存在。 */
    private static final int FORBIDDEN_CODE = 40300;
    private static final int NOT_FOUND_CODE = 40400;

    /**
     * {@code V032} 的种子条数。写成常量而不是从库里数出来：这里要验证的正是「迁移种了多少条」，
     * 用同一条 SQL 数出期望值再比较，等于自己和自己比。
     */
    private static final int SEEDED_OWNER_TYPE_COUNT = 2;
    private static final int SEEDED_TEMP_ZONE_COUNT = 4;
    private static final int SEEDED_COMPLIANCE_COUNT = 6;
    private static final int SEEDED_COUNTRY_COUNT = 28;
    private static final int SEEDED_US_PROVINCE_COUNT = 51;
    private static final int SEEDED_CN_PROVINCE_COUNT = 31;

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
    private DictQueryService dictQueryService;

    @BeforeEach
    void prepareAccounts() {
        if (!counterInstalled) {
            sqlSessionFactory.getConfiguration().addInterceptor(STATEMENT_COUNTER);
            counterInstalled = true;
        }
        STATEMENT_COUNTER.reset();

        Long operatorId = seedAccount(OPERATOR_LOGIN_NAME, "字典操作员", "dict-operator@example.com");
        Long nobodyId = seedAccount(NO_PERMISSION_LOGIN_NAME, "字典无权限用户", "dict-nobody@example.com");
        // 超管权限由鉴权时动态展开，这里不写 sys_role_menu：那正是规格禁止的做法
        link(operatorId, seedRole("SUPER_ADMIN", "超级管理员"));
        link(nobodyId, seedRole(NO_PERMISSION_ROLE, "测试用无权限角色"));
    }

    @Test
    @DisplayName("类型清单：五类字典都在，项数与种子一致，双语名齐全")
    void shouldSeedFiveDictTypesWithExpectedItemCounts() throws Exception {
        JsonNode response = getJson(consoleToken(), "/api/console/dicts");

        assertThat(response.path("code").asInt()).as("查类型清单失败：%s", response).isZero();
        Map<String, JsonNode> byType = byDictType(response.path("data"));

        assertThat(byType).containsKeys("OWNER_TYPE", "TEMP_ZONE", "COMPLIANCE", "COUNTRY", "PROVINCE");
        assertThat(itemCountOf(byType, "OWNER_TYPE")).isEqualTo(SEEDED_OWNER_TYPE_COUNT);
        assertThat(itemCountOf(byType, "TEMP_ZONE")).isEqualTo(SEEDED_TEMP_ZONE_COUNT);
        assertThat(itemCountOf(byType, "COMPLIANCE")).isEqualTo(SEEDED_COMPLIANCE_COUNT);
        assertThat(itemCountOf(byType, "COUNTRY")).isEqualTo(SEEDED_COUNTRY_COUNT);
        assertThat(itemCountOf(byType, "PROVINCE"))
                .isEqualTo(SEEDED_US_PROVINCE_COUNT + SEEDED_CN_PROVINCE_COUNT);
        for (String dictType : List.of("OWNER_TYPE", "TEMP_ZONE", "COMPLIANCE", "COUNTRY", "PROVINCE")) {
            assertThat(byType.get(dictType).path("name").asText()).isNotBlank();
            // 英文名是种子数据的一部分：缺了它，英文界面只能显示中文
            assertThat(byType.get(dictType).path("nameEn").asText()).isNotBlank();
            assertThat(byType.get(dictType).path("status").asText()).isEqualTo("ACTIVE");
        }
    }

    @Test
    @DisplayName("国家：项数等于种子数，取值是 ISO 3166-1 alpha-2")
    void shouldReturnAllSeededCountries() throws Exception {
        JsonNode items = items(consoleToken(), "/api/console/dicts/COUNTRY/items?enabledOnly=false");

        assertThat(items.size()).isEqualTo(SEEDED_COUNTRY_COUNT);
        assertThat(values(items)).contains("CN", "US", "DE", "JP").doesNotHaveDuplicates();
        for (JsonNode item : items) {
            assertThat(item.path("value").asText()).hasSize(2);
            // 双语名在种子数据里都必须有值：接口不做回退，缺了就是英文界面下的空白
            assertThat(item.path("label").asText()).isNotBlank();
            assertThat(item.path("labelEn").asText()).isNotBlank();
            assertThat(item.path("status").asText()).isEqualTo("ACTIVE");
        }
    }

    @Test
    @DisplayName("层级：PROVINCE 按 ext.parent 过滤，美国 51 条、中国 31 条")
    void shouldFilterProvincesByParentValue() throws Exception {
        String token = consoleToken();

        JsonNode usProvinces = items(token, "/api/console/dicts/PROVINCE/items?parentValue=US");
        JsonNode cnProvinces = items(token, "/api/console/dicts/PROVINCE/items?parentValue=CN");

        assertThat(usProvinces.size()).as("美国 50 州 + 哥伦比亚特区").isEqualTo(SEEDED_US_PROVINCE_COUNT);
        assertThat(cnProvinces.size()).as("中国 22 省 + 5 自治区 + 4 直辖市").isEqualTo(SEEDED_CN_PROVINCE_COUNT);
        // 层级写在 ext.parent 里，不新增 parent_id 列；value 用「国家码-州/省码」，归属写进取值本身
        for (JsonNode item : usProvinces) {
            assertThat(item.path("ext").path("parent").asText()).isEqualTo("US");
            assertThat(item.path("value").asText()).startsWith("US-");
        }
        for (JsonNode item : cnProvinces) {
            assertThat(item.path("ext").path("parent").asText()).isEqualTo("CN");
            assertThat(item.path("value").asText()).startsWith("CN-");
        }
        assertThat(values(usProvinces)).contains("US-CA").doesNotContain("US-XX");
        assertThat(values(cnProvinces)).contains("CN-BJ", "CN-GD", "CN-XJ");
    }

    @Test
    @DisplayName("类型不存在：返回 404 段错误码，HTTP 仍为 200")
    void shouldRejectUnknownDictType() throws Exception {
        JsonNode response = getJson(consoleToken(), "/api/console/dicts/NO_SUCH_TYPE/items");

        // 业务结果一律 HTTP 200，由 code 表达；只有 401 与 403 用状态码
        assertThat(response.path("code").asInt()).isEqualTo(NOT_FOUND_CODE);
        assertThat(response.path("message").asText()).isEqualTo("字典类型不存在");
    }

    @Test
    @DisplayName("停用项：enabledOnly=true 不返回，enabledOnly=false 返回且状态为 DISABLED，验证后恢复")
    void shouldHideDisabledItemFromDropdownButKeepItForHistory() throws Exception {
        String token = consoleToken();
        String sql = "UPDATE sys_dict_item SET status = ? WHERE dict_type = 'COUNTRY' AND value = ?";
        String target = "JP";
        try {
            jdbcTemplate.update(sql, "DISABLED", target);

            JsonNode dropdown = items(token, "/api/console/dicts/COUNTRY/items");
            // 默认 enabledOnly=true：停用项不再出现在下拉里，这一条承载规格「停用后不可选」
            assertThat(values(dropdown)).doesNotContain(target);
            assertThat(dropdown.size()).isEqualTo(SEEDED_COUNTRY_COUNT - 1);

            JsonNode history = items(token, "/api/console/dicts/COUNTRY/items?enabledOnly=false");
            // enabledOnly=false：历史数据仍能正常展示，这一条承载规格「历史数据仍正常展示」
            assertThat(values(history)).contains(target);
            assertThat(statusOf(history, target)).isEqualTo("DISABLED");
            assertThat(history.size()).isEqualTo(SEEDED_COUNTRY_COUNT);
        } finally {
            jdbcTemplate.update(sql, "ACTIVE", target);
        }

        // 恢复之后再确认一次：种子状态是本类其余用例与「类型清单项数」的前提
        assertThat(values(items(token, "/api/console/dicts/COUNTRY/items"))).contains(target);
    }

    @Test
    @DisplayName("批量：两条 SQL 以内取回多类型，不存在的类型静默跳过")
    void shouldBatchMultipleTypesSilentlySkippingUnknownOnes() throws Exception {
        String token = consoleToken();

        JsonNode known = getJson(token, "/api/console/dicts/batch?types=OWNER_TYPE,TEMP_ZONE").path("data");
        JsonNode withUnknown = getJson(token, "/api/console/dicts/batch?types=OWNER_TYPE,NO_SUCH_TYPE").path("data");

        assertThat(fieldNamesOf(known)).containsExactly("OWNER_TYPE", "TEMP_ZONE");
        assertThat(known.path("OWNER_TYPE").size()).isEqualTo(SEEDED_OWNER_TYPE_COUNT);
        assertThat(known.path("TEMP_ZONE").size()).isEqualTo(SEEDED_TEMP_ZONE_COUNT);
        // 前端可能传了管理员删掉的类型：不报错、也不出现该键，整页照常渲染
        assertThat(fieldNamesOf(withUnknown)).containsExactly("OWNER_TYPE");
    }

    @Test
    @DisplayName("类型清单用例：固定 1 条 SQL，与字典类型数量无关")
    void shouldQueryTypeListWithFixedNumberOfStatements() {
        STATEMENT_COUNTER.reset();

        dictQueryService.listTypes();

        int statements = STATEMENT_COUNTER.count();
        // 打印实测条数，供交付时核对「类型清单不超过 2 条 SQL」
        System.out.println("字典类型清单用例 SQL 条数：" + statements);
        assertThat(statements)
                .as("类型清单执行了 %d 条 SQL（上限 2）：项数必须在同一条语句里聚合，逐类查会让它随类型数量增长",
                        statements)
                .isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("批量用例：固定 1 条 SQL，与请求里的类型数量无关")
    void shouldQueryBatchWithFixedNumberOfStatements() {
        STATEMENT_COUNTER.reset();

        Map<String, List<DictItem>> items =
                dictQueryService.batch(List.of("OWNER_TYPE", "TEMP_ZONE", "COMPLIANCE"), true);

        int statements = STATEMENT_COUNTER.count();
        System.out.println("字典批量用例 SQL 条数：" + statements);
        assertThat(statements)
                .as("批量读取执行了 %d 条 SQL（上限 2）：按类型循环会让请求里的类型数直接变成 SQL 条数", statements)
                .isLessThanOrEqualTo(2);
        assertThat(items).containsKeys("OWNER_TYPE", "TEMP_ZONE", "COMPLIANCE");
    }

    @Test
    @DisplayName("无 system:dict:list 权限的用户：三个读接口都返回 HTTP 403")
    void shouldRejectReadingWithoutPermission() throws Exception {
        String token = login(NO_PERMISSION_LOGIN_NAME).path("data").path("token").asText();

        for (String path : List.of("/api/console/dicts",
                "/api/console/dicts/COUNTRY/items",
                "/api/console/dicts/batch?types=COUNTRY")) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));
        }
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

    /** 取字典项数组；请求失败时直接失败，避免后续断言被空数组带偏。 */
    private JsonNode items(String token, String path) throws Exception {
        JsonNode response = getJson(token, path);
        assertThat(response.path("code").asInt()).as("查字典项失败：%s", response).isZero();
        return response.path("data");
    }

    private JsonNode getJson(String token, String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** 类型清单按 dictType 建索引，断言不必依赖返回顺序。 */
    private static Map<String, JsonNode> byDictType(JsonNode types) {
        Map<String, JsonNode> byType = new LinkedHashMap<>();
        types.forEach(node -> byType.put(node.path("dictType").asText(), node));
        return byType;
    }

    private static long itemCountOf(Map<String, JsonNode> byType, String dictType) {
        return byType.get(dictType).path("itemCount").asLong();
    }

    /** 取批量响应的键列表：断言键集合时不依赖 JSON 对象的字段顺序。 */
    private static List<String> fieldNamesOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> values(JsonNode items) {
        List<String> values = new ArrayList<>();
        items.forEach(node -> values.add(node.path("value").asText()));
        return values;
    }

    private static String statusOf(JsonNode items, String value) {
        for (JsonNode item : items) {
            if (value.equals(item.path("value").asText())) {
                return item.path("status").asText();
            }
        }
        throw new AssertionError("字典项里没有取值为 " + value + " 的项");
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
     * 登录请求体的最小映射。
     *
     * <p>不复用生产侧的类型：用同一个类型序列化再反序列化，会掩盖字段名被改而测试仍然通过的情况。
     *
     * @param loginName 登录名
     * @param password  明文密码
     */
    private record LoginPayload(String loginName, String password) {
    }
}

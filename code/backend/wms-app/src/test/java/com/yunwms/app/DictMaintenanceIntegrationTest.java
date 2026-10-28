package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 字典项维护四个写端点的端到端验证：{@code POST /dicts/{dictType}/items}、
 * {@code PUT /dicts/items/{id}}、{@code PUT /dicts/items/{id}/status}、{@code DELETE /dicts/items/{id}}。
 *
 * <p>要证的是单测证不了的几件事：
 * <ul>
 *   <li><b>验收标准「停用后不需重启即在引用处消失」</b>——停用后下一次 {@code enabledOnly=true} 查询里
 *       就不再出现它，而 {@code enabledOnly=false} 仍查得到、状态为 {@code DISABLED}，再启用又回来。
 *       这条只能整链路验：用例层改完状态、读侧下一次请求是否真的看不到，中间隔着一条带
 *       {@code status} 条件的 SQL；</li>
 *   <li><b>{@code ux_sys_dict_item_value} 是带 {@code deleted_at IS NULL} 的部分唯一索引</b>——
 *       「同值重复被拒（40900）」与「删除后同值可重建」是同一条索引的两面，只有真 PostgreSQL 会同时成立；</li>
 *   <li><b>40900 只能由数据库给出</b>——服务层不预查重（先查再插在并发下无效），因此提示里的类型与值
 *       原文必须确实来自约束名翻译，而不是来自某处提前拼好的字符串；</li>
 *   <li><b>入口校验落在 HTTP 契约上</b>——{@code sortNo} 必传、{@code value} 非空且不超 64 字符，
 *       三者都在 Controller 的声明式校验里，绕过接口调用证明不了这一点；</li>
 *   <li><b>{@code system:dict:update} 真的被四个写端点消费</b>，且 {@code system:dict:list} 不足以写：
 *       只读角色四个写端点全 403，且数据一行都没变。</li>
 * </ul>
 *
 * <p>操作人不用种子账号 {@code superadmin}（它带着「需先改密」标记，拦截器在改密前只放行改密接口，
 * 替它改密又会污染 {@code BootstrapIntegrationTest} 依赖的初始状态），而是用 SQL 造账号：超管权限在鉴权时
 * 动态展开，因此挂着内置超管角色的账号天然拥有 {@code V031} 的 {@code system:dict:update}。
 *
 * <p>字典类型一律用例自建（前缀 {@code TEST_DICT_MAINT_}），不动种子字典：{@code COUNTRY} /
 * {@code PROVINCE} / {@code OWNER_TYPE} / {@code TEMP_ZONE} / {@code COMPLIANCE} 与 {@code CONTACT_ROLE}
 * 被 {@code DictQueryIntegrationTest}、{@code OwnerContactIntegrationTest} 按条数与取值断言，往里面加项
 * 会让那些用例失败。造类型直接写 {@code sys_dict} 而不走接口：本批只有「项的维护」端点，没有字典类型
 * 的维护端点，造类型这件事没有接口可用。
 *
 * <p>用例之间不隔离数据（同一个测试 JVM 共用一个库），因此每个用例用自己的类型后缀与取值；
 * 断言只针对自己造的那几条，不依赖执行顺序。
 */
@DisplayName("字典项维护")
class DictMaintenanceIntegrationTest extends AbstractDataScopeIntegrationTest {

    /** 操作人账号：已改密、已启用，挂内置超管角色。 */
    private static final String OPERATOR_LOGIN_NAME = "dict-maint-operator";

    /** 只被授予 {@code system:dict:list} 的账号，用于证明读权限不足以写。 */
    private static final String LIST_ONLY_LOGIN_NAME = "dict-maint-list-only";

    /** 只读角色编码，用例自造；迁移里除超管外不预置角色。 */
    private static final String LIST_ONLY_ROLE = "TEST_DICT_MAINT_LIST_ONLY";

    /** 自建字典类型的统一前缀：一眼能看出这是本类造的数据，而不是种子字典。 */
    private static final String TEST_DICT_TYPE_PREFIX = "TEST_DICT_MAINT_";

    /** 从不插入 {@code sys_dict} 的类型编码，用于验证「类型不存在 → 40000」。 */
    private static final String UNKNOWN_DICT_TYPE = "TEST_DICT_MAINT_UNKNOWN_TYPE";

    /** 状态编码取自 {@code DictStatus} 的枚举名。 */
    private static final String ACTIVE = "ACTIVE";
    private static final String DISABLED = "DISABLED";

    @BeforeEach
    void prepareAccounts() {
        link(seedStaff(OPERATOR_LOGIN_NAME, "字典维护操作员"), superAdminRole());
        link(seedStaff(LIST_ONLY_LOGIN_NAME, "字典只读员工"), seedListOnlyRole());
    }

    @Test
    @DisplayName("新增：返回字符串形式的新标识，列表里能按该标识查到这一条")
    void shouldCreateItemAndFindItByStringId() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        String dictType = seedDictType("CREATE");
        String value = "V_CREATE";

        JsonNode created = createItem(token, dictType, new CreateItemPayload(value, "新建项", "Created", 7));

        assertThat(created.path("code").asInt()).as("新增字典项失败：%s", created).isZero();
        // 标识以字符串传输：超过 2^53 的整数在 JS 里会丢精度，等到出问题再改要动全部前端
        assertThat(created.path("data").path("id").isTextual())
                .as("标识必须以字符串形式返回：%s", created)
                .isTrue();
        String itemId = created.path("data").path("id").asText();
        assertThat(itemId).matches("\\d+");

        JsonNode row = itemOf(itemsOf(token, itemsPath(dictType)), value);
        assertThat(row.path("id").asText()).as("列表里的标识必须与新增返回的一致：%s", row).isEqualTo(itemId);
        assertThat(row.path("label").asText()).isEqualTo("新建项");
        assertThat(row.path("labelEn").asText()).isEqualTo("Created");
        assertThat(row.path("sortNo").asInt()).isEqualTo(7);
        // 请求体里没有 status：新建项一律启用，不由调用方指定
        assertThat(row.path("status").asText()).isEqualTo(ACTIVE);
    }

    @Test
    @DisplayName("入口校验：漏 sortNo、value 全空白、value 超 64 字符都回 40000 且不落库")
    void shouldRejectInvalidCreatePayload() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        String dictType = seedDictType("VALIDATION");

        // sortNo 列上有 DEFAULT 0，漏传不会报错而是把这一项静默排到最前，因此入口显式要求必传
        JsonNode missingSortNo = createItem(token, dictType,
                new CreateItemPayload("V_NO_SORT", "缺排序号", null, null));
        assertThat(missingSortNo.path("code").asInt())
                .as("漏 sortNo 必须被拦下：%s", missingSortNo)
                .isEqualTo(BAD_REQUEST_CODE);
        assertThat(missingSortNo.path("message").asText()).contains("sortNo");

        JsonNode blankValue = createItem(token, dictType, new CreateItemPayload("   ", "空取值", null, 0));
        assertThat(blankValue.path("code").asInt())
                .as("value 全空白必须被拦下：%s", blankValue)
                .isEqualTo(BAD_REQUEST_CODE);
        assertThat(blankValue.path("message").asText()).contains("value");

        JsonNode tooLongValue = createItem(token, dictType, new CreateItemPayload("V".repeat(65), "取值超长", null, 0));
        assertThat(tooLongValue.path("code").asInt())
                .as("value 超 64 字符必须被拦下：%s", tooLongValue)
                .isEqualTo(BAD_REQUEST_CODE);
        assertThat(tooLongValue.path("message").asText()).contains("value");

        // 三次都被入口拦下：被拒的写入一行都不该留下，否则下拉里会多出没人提交成功的选项
        assertThat(itemsOf(token, itemsPath(dictType) + "?enabledOnly=false"))
                .as("被拒绝的新增不得落库")
                .isEmpty();
    }

    @Test
    @DisplayName("类型不存在：新增回 40000（不是 40400），提示带类型原文且不落库")
    void shouldRejectCreateUnderUnknownDictType() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);

        JsonNode rejected = createItem(token, UNKNOWN_DICT_TYPE,
                new CreateItemPayload("V_ORPHAN", "孤儿项", null, 1));

        // 写到一个不存在的类型是「这次提交的参数不对」，因此是 40000；读一个不存在的类型才是 40400
        assertThat(rejected.path("code").asInt()).as("类型不存在：%s", rejected).isEqualTo(BAD_REQUEST_CODE);
        assertThat(rejected.path("message").asText())
                .as("提示要带类型原文，管理员据此回字典类型页确认：%s", rejected)
                .contains(UNKNOWN_DICT_TYPE);
        // 外键确实拦得住孤儿项，但这条断言证明的是「没有留下任何一行」，而不只是「报了个错」
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sys_dict_item WHERE dict_type = ?", Integer.class, UNKNOWN_DICT_TYPE))
                .as("类型不存在时不得落库")
                .isZero();
    }

    @Test
    @DisplayName("同值重复：同一类型下再建同值回 40900，提示含类型与值原文，库里只留一行")
    void shouldRejectDuplicateValueUnderSameDictType() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        String dictType = seedDictType("DUPLICATE");
        String value = "V_DUPLICATE";

        assertThat(createItem(token, dictType, new CreateItemPayload(value, "首个", null, 1)).path("code").asInt())
                .as("第一次新增必须成功，否则下面的 40900 可能是别的原因")
                .isZero();

        JsonNode duplicated = createItem(token, dictType, new CreateItemPayload(value, "重复", null, 2));

        // 判重只能由 ux_sys_dict_item_value 给出（服务层不预查），这条断言同时证明了部分唯一索引在生效
        assertThat(duplicated.path("code").asInt()).as("同值重复：%s", duplicated).isEqualTo(CONFLICT_CODE);
        assertThat(duplicated.path("message").asText())
                .as("提示要带类型与值原文，照它改就能过：%s", duplicated)
                .contains(dictType)
                .contains(value);
        assertThat(valuesOf(itemsOf(token, itemsPath(dictType) + "?enabledOnly=false")))
                .as("冲突的那次新增不得留下第二行")
                .containsExactly(value);
    }

    @Test
    @DisplayName("编辑：label 与 sortNo 的改动反映到查询与顺序；不存在的标识回 40400")
    void shouldUpdateEditableFieldsAndRejectMissingItem() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        String dictType = seedDictType("UPDATE");
        String firstValue = "V_UPDATE_FIRST";
        String secondValue = "V_UPDATE_SECOND";

        String firstId = itemIdOf(createItem(token, dictType,
                new CreateItemPayload(firstValue, "旧名", "Old", 10)));
        JsonNode second = createItem(token, dictType, new CreateItemPayload(secondValue, "第二项", null, 20));
        assertThat(second.path("code").asInt()).as("造第二项失败：%s", second).isZero();
        assertThat(valuesOf(itemsOf(token, itemsPath(dictType))))
                .as("编辑前的顺序由 sortNo 决定")
                .containsExactly(firstValue, secondValue);

        JsonNode updated = updateItem(token, firstId, new UpdateItemPayload("新名", "New", 30));
        assertThat(updated.path("code").asInt()).as("编辑失败：%s", updated).isZero();

        JsonNode after = itemsOf(token, itemsPath(dictType));
        JsonNode row = itemOf(after, firstValue);
        assertThat(row.path("label").asText()).isEqualTo("新名");
        assertThat(row.path("labelEn").asText()).isEqualTo("New");
        assertThat(row.path("sortNo").asInt()).isEqualTo(30);
        // value 与 status 不在可编辑范围内：编辑不该顺手改掉它们
        assertThat(row.path("status").asText()).isEqualTo(ACTIVE);
        // sortNo 真的参与排序：改到 30 之后排到了第二项之后，这才叫「排序号改生效了」
        assertThat(valuesOf(after)).containsExactly(secondValue, firstValue);

        // 取一个必然不存在的标识，而不是写死一个「目前还没用到」的小整数
        long missingId = jdbcTemplate.queryForObject(
                "SELECT coalesce(max(id), 0) + 1000000 FROM sys_dict_item", Long.class);
        JsonNode missing = updateItem(token, String.valueOf(missingId), new UpdateItemPayload("随便", null, 0));
        assertThat(missing.path("code").asInt()).as("编辑不存在的标识：%s", missing).isEqualTo(NOT_FOUND_CODE);
        assertThat(missing.path("message").asText()).isEqualTo("字典项不存在");
    }

    @Test
    @DisplayName("启停：停用后下次查询即从启用项里消失、不筛状态仍可见，再启用又出现")
    void shouldHideDisabledItemFromEnabledOnlyQuery() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        String dictType = seedDictType("STATUS");
        String value = "V_STATUS";
        String itemId = itemIdOf(createItem(token, dictType, new CreateItemPayload(value, "启停项", null, 1)));

        assertThat(valuesOf(itemsOf(token, itemsPath(dictType)))).as("启用状态下应当可选").contains(value);

        assertThat(changeStatus(token, itemId, DISABLED).path("code").asInt()).as("停用失败").isZero();

        // 验收标准：停用后下一次请求就不再出现在「仅启用」的结果里——不重启、不依赖缓存失效
        assertThat(valuesOf(itemsOf(token, itemsPath(dictType) + "?enabledOnly=true")))
                .as("停用项不得出现在下拉语义里")
                .doesNotContain(value);
        // 不带 enabledOnly 时默认就是 true：前端不传参数也该看不到它
        assertThat(valuesOf(itemsOf(token, itemsPath(dictType))))
                .as("enabledOnly 默认 true")
                .doesNotContain(value);
        // 历史回显仍要能查到它，且状态是 DISABLED——停用不是删除
        JsonNode history = itemsOf(token, itemsPath(dictType) + "?enabledOnly=false");
        assertThat(valuesOf(history)).contains(value);
        assertThat(itemOf(history, value).path("status").asText()).isEqualTo(DISABLED);

        assertThat(changeStatus(token, itemId, ACTIVE).path("code").asInt()).as("重新启用失败").isZero();
        assertThat(valuesOf(itemsOf(token, itemsPath(dictType)))).as("启用后应当重新出现").contains(value);
    }

    @Test
    @DisplayName("删除：两种查询都查不到、二次删除回 40400，同值可重新新增（部分唯一索引）")
    void shouldSoftDeleteThenAllowRecreateWithSameValue() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        String dictType = seedDictType("DELETE");
        String value = "V_DELETE";
        String itemId = itemIdOf(createItem(token, dictType, new CreateItemPayload(value, "待删项", null, 1)));

        assertThat(deleteItem(token, itemId).path("code").asInt()).as("删除失败").isZero();

        assertThat(valuesOf(itemsOf(token, itemsPath(dictType))))
                .as("删除后下拉里不得再有它")
                .doesNotContain(value);
        assertThat(valuesOf(itemsOf(token, itemsPath(dictType) + "?enabledOnly=false")))
                .as("逻辑删除对管理端同样不可见")
                .doesNotContain(value);
        // 逻辑删除：行还在、deleted_at 已置。物理删除会让引用过该值的历史数据无从解释
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM sys_dict_item WHERE id = ?", Boolean.class,
                Long.parseLong(itemId)))
                .as("删除必须是逻辑删除（置 deleted_at），不是物理删除")
                .isTrue();

        assertThat(deleteItem(token, itemId).path("code").asInt())
                .as("重复删除与「不存在」同口径")
                .isEqualTo(NOT_FOUND_CODE);

        // ux_sys_dict_item_value 是带 deleted_at IS NULL 的部分唯一索引：已删除的行不占用取值
        JsonNode recreated = createItem(token, dictType, new CreateItemPayload(value, "重建项", null, 2));
        assertThat(recreated.path("code").asInt())
                .as("已删除的同值项不得占用取值：%s", recreated)
                .isZero();
        assertThat(itemIdOf(recreated))
                .as("重建的是另一行标识，历史数据引用的仍是同一个 value")
                .isNotEqualTo(itemId);
        assertThat(valuesOf(itemsOf(token, itemsPath(dictType)))).containsExactly(value);
    }

    @Test
    @DisplayName("权限：只有 system:dict:list 的账号调用四个写端点全部 403，数据不变")
    void shouldRejectWritesWithoutUpdatePermission() throws Exception {
        String dictType = seedDictType("PERMISSION");
        String value = "V_PERMISSION";
        String label = "原始中文名";
        String itemId = itemIdOf(createItem(loginToken(OPERATOR_LOGIN_NAME), dictType,
                new CreateItemPayload(value, label, null, 1)));

        String token = loginToken(LIST_ONLY_LOGIN_NAME);
        String authorization = "Bearer " + token;
        // 对照组：有 list 权限点就能读。没有这一步，下面的 403 可能只是账号登不进来或路径写错
        assertThat(valuesOf(itemsOf(token, itemsPath(dictType)))).containsExactly(value);

        String createBody = objectMapper.writeValueAsString(
                new CreateItemPayload("V_PERMISSION_DENIED", "越权新增", null, 0));
        expectForbidden(post(itemsPath(dictType))
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody));
        String updateBody = objectMapper.writeValueAsString(new UpdateItemPayload("越权改名", null, 0));
        expectForbidden(put(itemPath(itemId))
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody));
        String statusBody = objectMapper.writeValueAsString(new StatusPayload(DISABLED));
        expectForbidden(put(itemPath(itemId) + "/status")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusBody));
        expectForbidden(delete(itemPath(itemId)).header(HttpHeaders.AUTHORIZATION, authorization));

        // 拦在鉴权阶段：业务逻辑没有执行，因此一行都没变——403 之外还要证这一条，才叫「未授权不执行」
        JsonNode after = itemsOf(loginToken(OPERATOR_LOGIN_NAME), itemsPath(dictType) + "?enabledOnly=false");
        assertThat(valuesOf(after)).as("越权新增不得落库").containsExactly(value);
        JsonNode row = itemOf(after, value);
        assertThat(row.path("label").asText()).as("越权编辑不得生效").isEqualTo(label);
        assertThat(row.path("status").asText()).as("越权停用不得生效").isEqualTo(ACTIVE);
    }

    /**
     * 造一个自有的字典类型并返回其编码。
     *
     * <p>直接写 {@code sys_dict} 而不走接口：本批只有「项的维护」端点，没有字典类型的维护端点。
     * 判重用 {@code WHERE NOT EXISTS} 而不是 {@code ON CONFLICT}：目标是「不存在就补一个」，
     * {@code ON CONFLICT} 覆盖不了「一行都没有」的情形。
     *
     * @param suffix 类型后缀，每个用例用自己的后缀，互不干扰
     * @return 类型编码，形如 {@code TEST_DICT_MAINT_CREATE}
     */
    private String seedDictType(String suffix) {
        String dictType = TEST_DICT_TYPE_PREFIX + suffix;
        jdbcTemplate.update("""
                INSERT INTO sys_dict (dict_type, name)
                SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE dict_type = ?)
                """, dictType, "字典维护测试 " + suffix, dictType);
        return dictType;
    }

    /**
     * 造一个只持有 {@code system:dict:list} 的角色并返回其标识。
     *
     * <p>权限点从 {@code sys_menu} 按 {@code perms} 取，不新建权限点：{@code V031} 已定义读与写两个点，
     * 本用例要证的是「读权限不足以写」，不再种第二份权限数据。
     *
     * @return 角色标识
     */
    private long seedListOnlyRole() {
        long roleId = seedRole(LIST_ONLY_ROLE, "字典只读角色");
        jdbcTemplate.update("""
                INSERT INTO sys_role_menu (role_id, menu_id)
                SELECT ?, id FROM sys_menu WHERE perms = 'system:dict:list'
                ON CONFLICT (role_id, menu_id) DO NOTHING
                """, roleId);
        return roleId;
    }

    /**
     * 某类字典项的集合路径。
     *
     * <p>抽出来是因为新增、查询与越权用例都要拼同一个前缀：路径写错一次会让 40400 出现在
     * 不该出现的用例里，而那种失败看起来像业务 bug。
     *
     * @param dictType 类型编码
     * @return 该类型下字典项的集合路径，不带 {@code enabledOnly}（接口默认 true）
     */
    private static String itemsPath(String dictType) {
        return "/api/console/dicts/" + dictType + "/items";
    }

    /** 单个字典项的路径，编辑与启停共用。 */
    private static String itemPath(String itemId) {
        return "/api/console/dicts/items/" + itemId;
    }

    /** 新增一条字典项，返回原始响应；业务码由调用方按场景断言。 */
    private JsonNode createItem(String token, String dictType, CreateItemPayload payload) throws Exception {
        return postJson(token, itemsPath(dictType), objectMapper.writeValueAsString(payload));
    }

    /** 编辑一条字典项，返回原始响应；业务码由调用方按场景断言。 */
    private JsonNode updateItem(String token, String itemId, UpdateItemPayload payload) throws Exception {
        return putJson(token, itemPath(itemId), objectMapper.writeValueAsString(payload));
    }

    /** 改动一条字典项的状态，返回原始响应；业务码由调用方按场景断言。 */
    private JsonNode changeStatus(String token, String itemId, String status) throws Exception {
        return putJson(token, itemPath(itemId) + "/status",
                objectMapper.writeValueAsString(new StatusPayload(status)));
    }

    /**
     * 删除一条字典项，返回响应。
     *
     * <p>基类没有编组 DELETE，而删除的响应体要参与断言（第二次删除得 40400），
     * 因此不能只断言 HTTP 状态。
     */
    private JsonNode deleteItem(String token, String itemId) throws Exception {
        MvcResult result = mockMvc.perform(delete(itemPath(itemId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * 取接口返回的字典项数组，并断言请求本身成功。
     *
     * <p>不把「数组为空」当成失败：空数组是合法结果，由调用方按场景断言。
     */
    private JsonNode itemsOf(String token, String path) throws Exception {
        JsonNode response = getJson(token, path);
        assertThat(response.path("code").asInt()).as("查字典项失败：%s", response).isZero();
        return response.path("data");
    }

    /** 取新增响应里的标识，顺带断言这次新增成功。 */
    private static String itemIdOf(JsonNode created) {
        assertThat(created.path("code").asInt()).as("新增字典项失败：%s", created).isZero();
        String itemId = created.path("data").path("id").asText();
        assertThat(itemId).as("新增响应必须回带标识：%s", created).isNotBlank();
        return itemId;
    }

    /** 列表里的取值，保持接口返回的顺序。 */
    private static List<String> valuesOf(JsonNode items) {
        List<String> values = new ArrayList<>();
        items.forEach(node -> values.add(node.path("value").asText()));
        return values;
    }

    /**
     * 按取值取一条字典项。
     *
     * <p>取不到时直接失败：返回一个空节点会让后续的字段断言读到一个不存在的东西而「看起来通过」。
     */
    private static JsonNode itemOf(JsonNode items, String value) {
        for (JsonNode item : items) {
            if (value.equals(item.path("value").asText())) {
                return item;
            }
        }
        throw new AssertionError("字典项里没有取值为 " + value + " 的项：" + items);
    }

    /**
     * 断言该请求被权限拦下：HTTP 403 且业务码 40300。
     *
     * <p>只有 401 与 403 用 HTTP 状态码，其余业务结果一律 HTTP 200，因此两者都要断言。
     */
    private void expectForbidden(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));
    }

    /**
     * 新增字典项的请求体。
     *
     * <p>不复用生产侧的 {@code DictItemCreateRequest}：用同一个类型序列化再反序列化，会掩盖字段名
     * 被改而测试仍然通过的情况——接口契约要按「HTTP 上的 JSON」来验证。
     */
    private record CreateItemPayload(String value, String label, String labelEn, Integer sortNo) {
    }

    /** 编辑字典项的请求体，全量替换语义；{@code sortNo} 为 {@code null} 表示漏传。 */
    private record UpdateItemPayload(String label, String labelEn, Integer sortNo) {
    }

    /** 启停字典项的请求体，状态取 {@code DictStatus} 的枚举名。 */
    private record StatusPayload(String status) {
    }
}

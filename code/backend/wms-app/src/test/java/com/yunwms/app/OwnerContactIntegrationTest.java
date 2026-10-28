package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 货主联系人的端到端验证：{@code /api/console/owners/{ownerId}/contacts} 的四个端点。
 *
 * <p>要证的是单测证不了的几件事：
 * <ul>
 *   <li><b>主联系人切换在真实库上成立</b>——{@code ux_owner_contact_primary} 是带
 *       {@code deleted_at IS NULL} 的部分唯一索引，只有真 PostgreSQL 会拒绝第二行「主」；
 *       而「旧主被改成非主」这件事单测只能证明调用顺序，证明不了落库结果；</li>
 *   <li><b>软删除</b>——行还在库里，列表里消失，第二次删除回 40400（与「不存在」同口径）；</li>
 *   <li><b>可见性随货主</b>——数据范围外的货主取联系人得 40400，而不是空列表：空列表的意思是
 *       「这个货主没有联系人」，与「你无权看这个货主」对调用方的下一步动作完全不同；</li>
 *   <li><b>角色取值由字典把关</b>——{@code CONTACT_ROLE} 之外的取值回 40000，且不落库；</li>
 *   <li><b>货主不存在</b>时新增与列表都回 40400，不返回空列表、不落库；</li>
 *   <li><b>三条审计记录挂在货主编码下</b>——联系人是货主的子资源，对象类型沿用 {@code OWNER}，
 *       从货主详情追溯时这几条必须出现（见 {@code OwnerAuditEvent} 的类注释）。</li>
 * </ul>
 *
 * <p>操作人不用种子账号 {@code superadmin}（它带着「需先改密」标记，改它又会污染
 * {@code BootstrapIntegrationTest} 依赖的初始状态），而是用 SQL 造一个挂着内置超管角色的已改密账号：
 * 超管权限在鉴权时动态展开，因此它天然拥有 {@code V040} 的 {@code system:owner:contact:edit}、
 * {@code V033} 的 {@code system:owner:list} 与 {@code system:audit:list}。
 *
 * <p>货主一律用基类的 {@code seedOwner} 直接写库造：货主建档本身不是本类的被测对象，
 * 用它准备数据会让「建档接口坏了」表现为一堆联系人用例失败。
 *
 * <p>用例之间不隔离数据（同一个测试 JVM 共用一个库），因此每个用例用自己的货主编码与 SKU 前缀，
 * 断言只针对自己造的那几条。
 *
 * <p>SKU 前缀取 {@code K} 开头而不是跟着业务键写作 {@code OC}：{@code ck_owner_sku_prefix} 与
 * {@code SkuPrefix} 都排除易混淆的 {@code 0}/{@code O}/{@code 1}/{@code I}，把业务键直接照抄进前缀
 * 会被数据库当场拒掉，而那种失败看起来像「造数坏了」而不是「前缀不合规」。
 */
@DisplayName("货主联系人")
class OwnerContactIntegrationTest extends AbstractDataScopeIntegrationTest {

    /** 操作人账号：已改密、已启用，挂内置超管角色。 */
    private static final String OPERATOR_LOGIN_NAME = "owner-contact-operator";

    /** 数据范围限定到指定货主的员工，用于验证「范围外的货主取联系人得 40400」。 */
    private static final String SCOPED_LOGIN_NAME = "owner-contact-scoped";

    /** 范围角色编码，用例自造；迁移里除超管外不预置角色。 */
    private static final String SCOPED_ROLE = "TEST_OWNER_CONTACT_SCOPE";

    @BeforeEach
    void prepareAccounts() {
        link(seedStaff(OPERATOR_LOGIN_NAME, "联系人操作员"), superAdminRole());
    }

    @Test
    @DisplayName("全链路：两条只有张三为主，编辑李四设为主后张三变非主，删除李四只剩张三")
    void shouldSwitchPrimaryContactWithoutLeavingTwoPrimaries() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        long ownerId = seedOwner("OWNER-OC-CHAIN", "联系人全链路甲", "KCA2", "ACTIVE");

        JsonNode primaryCreated = createContact(token, ownerId,
                new ContactPayload("张三", "BUSINESS", "13800001111", "zhangsan@example.com", true, "主联系人"));
        assertThat(primaryCreated.path("code").asInt()).as("新增主联系人失败：%s", primaryCreated).isZero();
        String primaryId = primaryCreated.path("data").path("id").asText();
        assertThat(primaryCreated.path("data").path("isPrimary").asBoolean())
                .as("isPrimary 提交 true 就必须落成 true")
                .isTrue();
        assertThat(primaryCreated.path("data").path("roleType").asText())
                .as("角色按字典取值原样回带")
                .isEqualTo("BUSINESS");

        JsonNode secondCreated = createContact(token, ownerId,
                new ContactPayload("李四", "FINANCE", "13800002222", null, false, null));
        assertThat(secondCreated.path("code").asInt()).as("新增第二条失败：%s", secondCreated).isZero();
        String secondId = secondCreated.path("data").path("id").asText();

        JsonNode contacts = contactsOf(token, ownerId);
        // 顺序也是契约的一部分：按标识升序等价于登记先后，翻页不存在，但「谁先登记」要看得出来
        assertThat(idsOf(contacts)).as("两条都在，按登记先后返回：%s", contacts)
                .containsExactly(primaryId, secondId);
        assertThat(primaryIds(contacts)).as("此时只有第一条是主联系人").containsExactly(primaryId);

        // 编辑是全量替换：要保留的字段必须一并提交，省略 isPrimary 等于提交 false
        JsonNode switched = putJson(token, contactsPath(ownerId) + "/" + secondId,
                objectMapper.writeValueAsString(
                        new ContactPayload("李四", "FINANCE", "13800002222", null, true, null)));
        assertThat(switched.path("code").asInt()).as("设为主联系人失败：%s", switched).isZero();

        JsonNode afterSwitch = contactsOf(token, ownerId);
        // 本任务的核心断言：条数不变（切换主联系人不是新增）、主标记换人、旧主被改成非主
        assertThat(afterSwitch.size()).as("切换主联系人不得增删行：%s", afterSwitch).isEqualTo(2);
        assertThat(primaryIds(afterSwitch)).as("至多一条主联系人，且必须是刚设的那条").containsExactly(secondId);
        assertThat(rowOf(afterSwitch, primaryId).path("isPrimary").asBoolean())
                .as("旧主必须被改成非主，而不是两条并存：%s", afterSwitch)
                .isFalse();

        assertThat(deleteContact(token, ownerId, secondId).path("code").asInt()).isZero();

        JsonNode afterDelete = contactsOf(token, ownerId);
        assertThat(idsOf(afterDelete)).as("删掉第二条后只剩第一条：%s", afterDelete).containsExactly(primaryId);
        // 删掉主联系人只释放名额，不会自动把某条旧的推回主位：主联系人是人选的，不是算出来的
        assertThat(primaryIds(afterDelete)).as("删掉主联系人后不自动补一个主联系人").isEmpty();
    }

    @Test
    @DisplayName("软删除：删除后列表不再返回该条，重复删除返回 40400，行仍在库里")
    void shouldSoftDeleteAndRejectSecondDelete() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        long ownerId = seedOwner("OWNER-OC-DELETE", "联系人软删乙", "KCB3", "ACTIVE");

        JsonNode created = createContact(token, ownerId,
                new ContactPayload("王五", "LOGISTICS", "13800003333", null, false, null));
        assertThat(created.path("code").asInt()).as("新增失败：%s", created).isZero();
        String contactId = created.path("data").path("id").asText();

        assertThat(deleteContact(token, ownerId, contactId).path("code").asInt()).isZero();

        assertThat(contactsOf(token, ownerId)).as("已删除的联系人不得再出现在列表里").isEmpty();
        // 软删除：行还在、deleted_at 已置。物理删除会让「谁在什么时候登记过这条」无从追溯
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM owner_contact WHERE id = ?", Boolean.class,
                Long.parseLong(contactId)))
                .as("删除必须是软删除（置 deleted_at），不是物理删除")
                .isTrue();

        JsonNode secondDelete = deleteContact(token, ownerId, contactId);
        assertThat(secondDelete.path("code").asInt())
                .as("重复删除与「不存在」同口径，必须是 40400：%s", secondDelete)
                .isEqualTo(NOT_FOUND_CODE);
    }

    @Test
    @DisplayName("越权：数据范围外的员工取联系人列表返回 40400，而不是空列表")
    void shouldReturnNotFoundForOwnerOutsideDataScope() throws Exception {
        long visibleOwnerId = seedOwner("OWNER-OC-SCOPE-IN", "联系人范围丙", "KCC4", "ACTIVE");
        long hiddenOwnerId = seedOwner("OWNER-OC-SCOPE-OUT", "联系人范围丁", "KCD5", "ACTIVE");
        long staffId = seedStaff(SCOPED_LOGIN_NAME, "联系人范围员工");
        link(staffId, seedScopedRole(SCOPED_ROLE, "CUSTOM", visibleOwnerId));
        String token = loginToken(SCOPED_LOGIN_NAME);

        // 对照组：范围里的货主能正常取到列表（空数组）。没有这一步，下面的 40400 可能只是
        // 缺权限点或路径写错，测到的就不是数据范围
        assertThat(contactsOf(token, visibleOwnerId)).as("范围里的货主应当能取到（空的）联系人列表").isEmpty();

        JsonNode hidden = getJson(token, contactsPath(hiddenOwnerId));
        assertThat(hidden.path("code").asInt())
                .as("范围外的货主与「不存在」同口径：%s", hidden)
                .isEqualTo(NOT_FOUND_CODE);
        // 空列表与「无权」是两件事：前者说这个货主没有联系人，后者说你根本看不到这个货主
        assertThat(hidden.path("data").isNull())
                .as("无权时必须报错，不能回一个空列表：%s", hidden)
                .isTrue();
    }

    @Test
    @DisplayName("字典校验：roleType 不在 CONTACT_ROLE 里返回 40000，提示带原值且不落库")
    void shouldRejectRoleTypeOutsideContactRoleDict() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        long ownerId = seedOwner("OWNER-OC-DICT", "联系人字典戊", "KCE6", "ACTIVE");

        JsonNode rejected = createContact(token, ownerId,
                new ContactPayload("赵六", "UNKNOWN", "13800004444", null, false, null));

        assertThat(rejected.path("code").asInt())
                .as("字典里没有的角色必须被拒：%s", rejected)
                .isEqualTo(BAD_REQUEST_CODE);
        assertThat(rejected.path("message").asText())
                .contains("CONTACT_ROLE")
                .as("提示里要带出提交的原值，调用方才改得对")
                .contains("UNKNOWN");
        assertThat(contactsOf(token, ownerId)).as("被拒绝的新增不得落库").isEmpty();
    }

    @Test
    @DisplayName("货主不存在：新增与列表都返回 40400，新增不落库")
    void shouldRejectUnknownOwner() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        // 取一个必然不存在的标识，而不是写死一个「目前还没用到」的小整数
        long missingOwnerId = jdbcTemplate.queryForObject(
                "SELECT coalesce(max(id), 0) + 1000000 FROM owner", Long.class);

        JsonNode created = createContact(token, missingOwnerId,
                new ContactPayload("孙七", "ONSITE", "13800005555", null, false, null));

        assertThat(created.path("code").asInt()).as("不存在的货主：%s", created).isEqualTo(NOT_FOUND_CODE);
        assertThat(created.path("message").asText()).isEqualTo("货主不存在");
        assertThat(getJson(token, contactsPath(missingOwnerId)).path("code").asInt())
                .as("列表与新增同口径，不能靠列表为空暗示「货主不存在」")
                .isEqualTo(NOT_FOUND_CODE);
    }

    @Test
    @DisplayName("审计：新增、编辑、删除三条都挂在货主编码下，按时间倒序")
    void shouldRecordContactAuditUnderOwnerCode() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        String ownerCode = "OWNER-OC-AUDIT";
        long ownerId = seedOwner(ownerCode, "联系人审计己", "KCF7", "ACTIVE");

        JsonNode created = createContact(token, ownerId,
                new ContactPayload("周八", "RETURN", "13800006666", null, false, null));
        assertThat(created.path("code").asInt()).as("新增失败：%s", created).isZero();
        String contactId = created.path("data").path("id").asText();

        JsonNode updated = putJson(token, contactsPath(ownerId) + "/" + contactId,
                objectMapper.writeValueAsString(
                        new ContactPayload("周八（改）", "RETURN", "13800006667", null, false, null)));
        assertThat(updated.path("code").asInt()).as("编辑失败：%s", updated).isZero();

        assertThat(deleteContact(token, ownerId, contactId).path("code").asInt()).isZero();

        JsonNode page = getJson(token, "/api/console/audit-logs?bizType=OWNER&bizId=" + ownerCode);
        assertThat(page.path("code").asInt()).as("查询审计日志失败：%s", page).isZero();
        JsonNode records = page.path("data").path("records");
        assertThat(records.size()).as("新增、编辑、删除各一条：%s", records).isEqualTo(3);

        // 三条都挂在货主编码下：联系人不另立 CONTACT 对象类型，否则从货主详情追溯时这几条会消失
        for (JsonNode record : records) {
            assertThat(record.path("bizType").asText()).isEqualTo("OWNER");
            assertThat(record.path("bizId").asText()).isEqualTo(ownerCode);
        }
        assertThat(records.get(0).path("summary").asText()).isEqualTo("删除联系人 " + ownerCode);
        assertThat(records.get(1).path("summary").asText()).isEqualTo("修改联系人 " + ownerCode);
        assertThat(records.get(2).path("summary").asText()).isEqualTo("新增联系人 " + ownerCode);
    }

    /**
     * 联系人子资源的路径。
     *
     * <p>抽出来是因为四个端点与越权用例都要拼同一个前缀：路径写错一次会让 40400 出现在
     * 不该出现的用例里，而那种失败看起来像业务 bug。
     *
     * @param ownerId 货主标识
     * @return 该货主的联系人集合路径
     */
    private static String contactsPath(long ownerId) {
        return "/api/console/owners/" + ownerId + "/contacts";
    }

    /**
     * 新增一条联系人，返回原始响应。
     *
     * <p>不在这里断言业务码：被拒的用例要的正是 4xxxx，由调用方按场景断言。
     *
     * @param token   操作人令牌
     * @param ownerId 货主标识，取自路径
     * @param payload 联系人请求体
     * @return 接口响应
     * @throws Exception 请求构造或执行失败
     */
    private JsonNode createContact(String token, long ownerId, ContactPayload payload) throws Exception {
        return postJson(token, contactsPath(ownerId), objectMapper.writeValueAsString(payload));
    }

    /**
     * 删除一条联系人，返回响应。
     *
     * <p>基类只编组了 GET/POST/PUT；删除的响应体要参与断言（第二次删除得 40400），
     * 因此不能只断言 HTTP 状态。
     *
     * @param token     操作人令牌
     * @param ownerId   货主标识，仅用于定位
     * @param contactId 联系人标识
     * @return 接口响应
     * @throws Exception 请求构造或执行失败
     */
    private JsonNode deleteContact(String token, long ownerId, String contactId) throws Exception {
        MvcResult result = mockMvc.perform(delete(contactsPath(ownerId) + "/" + contactId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * 取某货主的联系人列表，并断言请求本身成功。
     *
     * <p>不把「列表为空」当成失败：空列表是合法结果，由调用方按场景断言；这里只把
     * 「请求失败」与「范围过滤掉」区分开，否则 404 到底来自过滤还是参数错误，看断言分不出来。
     *
     * @param token   操作人令牌
     * @param ownerId 货主标识
     * @return 响应里的 {@code data} 节点（联系人数组）
     * @throws Exception 请求构造或响应解析失败
     */
    private JsonNode contactsOf(String token, long ownerId) throws Exception {
        JsonNode response = getJson(token, contactsPath(ownerId));
        assertThat(response.path("code").asInt()).as("查询联系人列表失败：%s", response).isZero();
        return response.path("data");
    }

    /**
     * 列表里的联系人标识，保持接口返回的顺序。
     *
     * @param contacts 联系人数组
     * @return 标识列表
     */
    private static List<String> idsOf(JsonNode contacts) {
        List<String> ids = new ArrayList<>();
        contacts.forEach(row -> ids.add(row.path("id").asText()));
        return ids;
    }

    /**
     * 列表里所有主联系人的标识。
     *
     * @param contacts 联系人数组
     * @return 主联系人标识列表；没有主联系人时为空
     */
    private static List<String> primaryIds(JsonNode contacts) {
        List<String> ids = new ArrayList<>();
        contacts.forEach(row -> {
            if (row.path("isPrimary").asBoolean()) {
                ids.add(row.path("id").asText());
            }
        });
        return ids;
    }

    /**
     * 按标识取列表里的一行。
     *
     * <p>取不到时直接失败：返回一个空节点会让后续的字段断言读到一个不存在的东西而「看起来通过」，
     * 那种通过比失败更难查。
     *
     * @param contacts  联系人数组
     * @param contactId 联系人标识
     * @return 对应的那一行
     */
    private static JsonNode rowOf(JsonNode contacts, String contactId) {
        for (JsonNode row : contacts) {
            if (contactId.equals(row.path("id").asText())) {
                return row;
            }
        }
        return fail("列表里没有标识为 " + contactId + " 的联系人：" + contacts);
    }

    /**
     * 联系人写入请求体的最小映射，新增与编辑共用。
     *
     * <p>不复用生产侧的 {@code OwnerContactSaveRequest}：用同一个类型序列化再反序列化，
     * 会掩盖字段名被改而测试仍然通过的情况（与 {@code OwnerAccountProvisionIntegrationTest}
     * 的取向一致）。
     *
     * @param name      联系人姓名
     * @param roleType  角色，取 {@code CONTACT_ROLE} 字典的 value
     * @param phone     联系电话
     * @param email     邮箱，可为 {@code null}
     * @param isPrimary 是否主联系人
     * @param remark    备注，可为 {@code null}
     */
    private record ContactPayload(String name, String roleType, String phone, String email,
                                  boolean isPrimary, String remark) {
    }
}

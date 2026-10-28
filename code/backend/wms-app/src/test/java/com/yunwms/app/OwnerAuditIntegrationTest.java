package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 货主审计留痕的端到端验证：真实 HTTP 请求 → 真实 PostgreSQL 的 {@code sys_audit_log} → 查询端点。
 *
 * <p>这一批要证的是单测证不了的东西：{@code wms-master} 的五个写操作真的通过
 * {@code wms-app} 的桥接落进了 {@code wms-auth} 的审计表，而且落进去的内容与单测里断言的一致。
 * 单测把审计端口换成了 mock，只能证明「调用发生在正确的时机、参数是这些值」；能不能写进库、
 * 来源端判定对不对、快照经 {@code jsonb} 往返后还在不在，只有这里能答。
 *
 * <p>要钉住四件事：
 * <ul>
 *   <li><b>一条链按编码可追溯</b>——建档、修改、开号三条记录挂在同一个货主编码下，按时间倒序返回。
 *       这正是管理员从货主详情看「这个客户经历过什么」的形态；对象标识若用主键，这三条就查不到；</li>
 *   <li><b>来源端是 {@code CONSOLE}</b>——本批的调用全部来自仓网端的 HTTP 请求，来源端由实现按请求
 *       路径前缀判定；写成 {@code PORTAL} 或为空，说明判定依据变成了别的（例如按主体类型推导）；</li>
 *   <li><b>变更前后快照的形状</b>——建档与开号没有「变更前」，修改前后都有且状态一致，
 *       开号的对象仍是货主（不是账号）；</li>
 *   <li><b>快照里没有凭据</b>——本批不该有，但钉住它，防止将来「顺手把密码也记一份」。</li>
 * </ul>
 *
 * <p>操作人用 SQL 造一个挂着内置超管角色的已改密账号：超管权限在鉴权时动态展开，因此它天然拥有
 * {@code system:owner:create|update|account} 与 {@code system:audit:list} 两条链路的权限点。
 *
 * <p>用例之间不隔离数据（整个测试 JVM 共用一个库）：每个用例用自己的 SKU 前缀与外部码，
 * 断言只针对自己那条货主编码下的记录，不依赖总数与执行顺序。
 */
@DisplayName("货主审计留痕")
class OwnerAuditIntegrationTest extends AbstractDataScopeIntegrationTest {

    /** 操作人账号：已改密、已启用，挂内置超管角色。 */
    private static final String OPERATOR_LOGIN_NAME = "owner-audit-operator";

    /** 主账号初始密码；用于断言它一个字符都没有进审计表。 */
    private static final String INITIAL_PASSWORD = "Owner@1234";

    @BeforeEach
    void prepareAccounts() {
        link(seedStaff(OPERATOR_LOGIN_NAME, "审计留痕操作员"), superAdminRole());
    }

    @Test
    @DisplayName("按货主编码追溯：建档 → 修改 → 开号三条，时间倒序，来源端 CONSOLE，快照不含凭据")
    void shouldTraceOwnerChainInReverseChronologicalOrder() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        JsonNode created = createOwner(token, "审计留痕甲", "AUD2");
        String ownerId = created.path("id").asText();
        String ownerCode = created.path("code").asText();

        JsonNode updated = putJson(token, "/api/console/owners/" + ownerId,
                objectMapper.writeValueAsString(updatePayload("审计留痕甲（改名）", "EXT-AUD2")));
        assertThat(updated.path("code").asInt()).as("编辑失败：%s", updated).isZero();

        JsonNode account = postJson(token, "/api/console/owners/" + ownerId + "/account",
                objectMapper.writeValueAsString(new AccountPayload("审计留痕甲主账号", INITIAL_PASSWORD)));
        assertThat(account.path("code").asInt()).as("开号失败：%s", account).isZero();
        String accountId = account.path("data").path("accountId").asText();

        JsonNode records = auditRecords(token, ownerCode);

        assertThat(records.size())
                .as("建档、修改、开号各一条，且都挂在货主编码 %s 下", ownerCode)
                .isEqualTo(3);
        JsonNode accountOpened = records.get(0);
        JsonNode edited = records.get(1);
        JsonNode creation = records.get(2);

        // 倒序：最新的一条在最前，摘要按「事件模板 + 对象标识」拼装
        assertThat(accountOpened.path("summary").asText()).isEqualTo("开通主账号 " + ownerCode);
        assertThat(edited.path("summary").asText()).isEqualTo("修改货主 " + ownerCode);
        assertThat(creation.path("summary").asText()).isEqualTo("新建货主 " + ownerCode);
        assertThat(createdAtOf(accountOpened)).isAfterOrEqualTo(createdAtOf(edited));
        assertThat(createdAtOf(edited)).isAfterOrEqualTo(createdAtOf(creation));

        for (JsonNode record : records) {
            assertThat(record.path("bizType").asText()).isEqualTo("OWNER");
            assertThat(record.path("bizId").asText()).isEqualTo(ownerCode);
            // 目标状态与来源端都由实现自己取：调用来自仓网端，就只能是 CONSOLE
            assertThat(record.path("sourcePortal").asText()).isEqualTo("CONSOLE");
            // 这条链路有会话，操作人必须记下来；记成「系统」说明会话没读到
            assertThat(record.path("operatorId").asText()).isNotBlank();
            assertThat(record.path("operatorName").asText()).isNotEqualTo("系统");
            assertNoCredentials(record);
        }

        // 建档：没有变更前值（落 SQL NULL，不是空对象），变更后从系统编码开始
        assertThat(creation.path("beforeValue").isNull()).isTrue();
        assertThat(creation.path("afterValue").path("code").asText()).isEqualTo(ownerCode);
        assertThat(creation.path("afterValue").path("status").asText()).isEqualTo("ACTIVE");
        assertThat(creation.path("action").asText()).isEqualTo("CREATE");

        // 修改：前后都有，且状态一致——编辑改不到状态，日志不能暗示发生了一次流转
        assertThat(edited.path("action").asText()).isEqualTo("UPDATE");
        assertThat(edited.path("beforeValue").path("name").asText()).isEqualTo("审计留痕甲");
        assertThat(edited.path("afterValue").path("name").asText()).isEqualTo("审计留痕甲（改名）");
        assertThat(edited.path("afterValue").path("status").asText())
                .isEqualTo(edited.path("beforeValue").path("status").asText());

        // 开号：对象仍是货主，快照里是登录名与账号标识
        assertThat(accountOpened.path("action").asText()).isEqualTo("CREATE");
        assertThat(accountOpened.path("beforeValue").isNull()).isTrue();
        assertThat(accountOpened.path("afterValue").path("loginName").asText()).isEqualTo(ownerCode);
        assertThat(accountOpened.path("afterValue").path("accountId").asText()).isEqualTo(accountId);
    }

    @Test
    @DisplayName("提交了但字段一个都没变：只留建档那一条，不产生第二条修改记录")
    void shouldNotRecordNoOpUpdate() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        JsonNode created = createOwner(token, "审计留痕乙", "AUD3");
        String ownerId = created.path("id").asText();
        String ownerCode = created.path("code").asText();

        // 把档案原样再提交一次：这是一次成功的保存，但没有任何字段发生变化
        JsonNode saved = putJson(token, "/api/console/owners/" + ownerId,
                objectMapper.writeValueAsString(updatePayload("审计留痕乙", "EXT-AUD3")));
        assertThat(saved.path("code").asInt()).as("对照组：同一份内容再保存一次必须成功：%s", saved).isZero();

        JsonNode records = auditRecords(token, ownerCode);

        // 「谁改了什么」与「谁点了一次保存」在日志里不能长得一样，否则真改动会被空提交淹没
        assertThat(records.size()).as("空提交不得留下审计记录").isEqualTo(1);
        assertThat(records.get(0).path("summary").asText()).isEqualTo("新建货主 " + ownerCode);
    }

    /**
     * 走接口建一个合作中的货主。
     *
     * @param token     操作人令牌，需持有 {@code system:owner:create}
     * @param name      客户名称，用例自身保证唯一性标记落在里面
     * @param skuPrefix SKU 前缀，4 位大写字母或数字且不含 0/O/1/I
     * @return 响应里的 {@code data} 节点（含 {@code id} 与 {@code code}）
     */
    private JsonNode createOwner(String token, String name, String skuPrefix) throws Exception {
        String body = objectMapper.writeValueAsString(new CreateOwnerPayload(
                name, "审计简称", "Owner Audit Test", "EXT-" + skuPrefix, skuPrefix, "BRAND",
                "审计测试结算主体", "CN", "CN-GD", "深圳", "南山区", "518000", "科技园路 1 号", null,
                "ACTIVE"));
        JsonNode response = postJson(token, "/api/console/owners", body);
        assertThat(response.path("code").asInt()).as("建档失败：%s", response).isZero();
        return response.path("data");
    }

    /**
     * 编辑货主的请求体：与建档时提交的字段逐项对应，只有名称与外部码由调用方替换。
     *
     * <p>「与建档逐项对应」是空提交用例的前提：少一个字段或多一个值，那次保存就成了真改动，
     * 测到的就不是「没改不记」了。
     *
     * @param name         客户名称
     * @param externalCode 外部码
     * @return 可直接序列化的请求体
     */
    private static UpdateOwnerPayload updatePayload(String name, String externalCode) {
        return new UpdateOwnerPayload(
                name, "Owner Audit Test", "审计简称", externalCode, "BRAND", "审计测试结算主体",
                "CN", "CN-GD", "深圳", "南山区", "518000", "科技园路 1 号", null);
    }

    /**
     * 按货主编码追溯：调查询端点并返回记录数组。
     *
     * <p>只查 {@code bizType=OWNER&bizId=<货主编码>}：对象标识用编码而不是主键，正是本批要证的追溯方式。
     *
     * @param token     操作人令牌，需持有 {@code system:audit:list}
     * @param ownerCode 货主系统编码
     * @return 记录数组，按时间倒序
     */
    private JsonNode auditRecords(String token, String ownerCode) throws Exception {
        JsonNode page = getJson(token, "/api/console/audit-logs?bizType=OWNER&bizId=" + ownerCode);
        assertThat(page.path("code").asInt()).as("查询审计日志失败：%s", page).isZero();
        JsonNode records = page.path("data").path("records");
        assertThat(page.path("data").path("total").asLong())
                .as("total 与 records 必须一致：%s", page)
                .isEqualTo(records.size());
        return records;
    }

    private static Instant createdAtOf(JsonNode record) {
        return Instant.parse(record.path("createdAt").asText());
    }

    /**
     * 断言一条记录的两份快照里没有凭据类字段。
     *
     * <p>两条判据一起用：字段名（将来真加了一个 {@code passwordHash} 就会命中）与明文值
     * （字段名可以改，值不会说谎）。审计只追加，任何凭据一旦写进去就撤不回来，所以在写入侧钉住。
     *
     * @param record 一条审计日志
     */
    private static void assertNoCredentials(JsonNode record) {
        for (String snapshot : List.of("beforeValue", "afterValue")) {
            JsonNode node = record.path(snapshot);
            assertThat(fieldNamesOf(node))
                    .as("%s 不得包含凭据类字段：%s", snapshot, record)
                    .noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("password"));
            assertThat(node.toString())
                    .as("%s 不得出现初始密码明文：%s", snapshot, record)
                    .doesNotContain(INITIAL_PASSWORD);
        }
    }

    /**
     * 递归收集 JSON 里的全部字段名。
     *
     * <p>只看顶层不够：快照里可能嵌对象或数组（例如将来的联系人集合），
     * 凭据字段藏在内层时顶层扫描会放过它。
     *
     * @param node JSON 节点
     * @return 字段名列表；不是对象时返回空
     */
    private static List<String> fieldNamesOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        collectFieldNames(node, names);
        return names;
    }

    private static void collectFieldNames(JsonNode node, List<String> names) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String name = fieldNames.next();
                names.add(name);
                collectFieldNames(node.get(name), names);
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectFieldNames(child, names));
        }
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
     * 编辑请求体的最小映射：没有 {@code skuPrefix} 与 {@code initialStatus}，两者都不在编辑契约里。
     */
    private record UpdateOwnerPayload(
            String name,
            String nameEn,
            String shortName,
            String externalCode,
            String ownerType,
            String settlementEntity,
            String country,
            String province,
            String city,
            String district,
            String postalCode,
            String addressLine,
            String remark) {
    }

    /**
     * 开号请求体的最小映射：登录名由系统按货主编码生成，请求体里没有这个字段。
     *
     * @param displayName     主账号姓名
     * @param initialPassword 初始密码明文
     */
    private record AccountPayload(String displayName, String initialPassword) {
    }
}

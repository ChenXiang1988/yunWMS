package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditEntry;
import com.yunwms.auth.domain.port.AuditRecorder;
import com.yunwms.auth.domain.port.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审计能力的端到端验证：写入端口 → 真实 PostgreSQL 的 {@code jsonb} / {@code inet} → 查询端点。
 *
 * <p>本批**不接入任何业务操作**（那是 2.8b），因此日志由用例直接调 {@link AuditRecorder} 写入。
 * 这不是绕过：要证的正是「审计能力本身能不能用」——端口能写、迁移的列与约束真的生效、
 * 快照经 {@code jsonb} 往返后内容不变、查询按时间倒序、权限点真的被接口消费。
 *
 * <p>直接调用发生在测试线程上，没有会话也没有请求上下文，因此写出来的日志操作人为空、
 * 来源端为空——这同时是「取不到操作人仍然写入」那条规格的端到端证据。
 *
 * <p>用例之间不隔离数据（整个测试 JVM 共用一个库）：每个用例用自己的对象标识，
 * 断言只针对自己写的那几条，不依赖总数与执行顺序。
 */
@DisplayName("审计日志")
class AuditLogIntegrationTest extends AbstractIntegrationTest {

    /** 操作人账号：已改密、已启用，挂内置超管角色（权限点由鉴权时动态展开）。 */
    private static final String OPERATOR_LOGIN_NAME = "audit-operator";
    /** 无任何权限点的账号，用于验证 403。 */
    private static final String NO_PERMISSION_LOGIN_NAME = "audit-nobody";
    private static final String PASSWORD = "Admin@1234";
    /** 无权限点角色编码，用例自造；迁移里除超管外不预置角色。 */
    private static final String NO_PERMISSION_ROLE = "TEST_AUDIT_NO_PERMISSION";

    /** 错误码来自 {@code ErrorCode}：40000 参数不合法，40300 无权访问。 */
    private static final int BAD_REQUEST_CODE = 40000;
    private static final int FORBIDDEN_CODE = 40300;

    /**
     * 两次写入之间的间隔。
     *
     * <p>{@code created_at} 的默认值是 {@code now()}（事务开始时间），精度到微秒；两次独立的写入
     * 实际上不会落在同一微秒，但「实际上不会」不足以支撑一条断言顺序的用例——这里显式拉开间隔，
     * 让倒序断言不依赖时钟精度。
     */
    private static final long WRITE_GAP_MILLIS = 5;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private AuditRecorder auditRecorder;

    @BeforeEach
    void prepareAccounts() {
        Long operatorId = seedAccount(OPERATOR_LOGIN_NAME, "审计操作员", "audit-operator@example.com");
        Long nobodyId = seedAccount(NO_PERMISSION_LOGIN_NAME, "审计无权限用户", "audit-nobody@example.com");
        // 超管权限由鉴权时动态展开，这里不写 sys_role_menu：那正是规格禁止的做法
        link(operatorId, seedRole("SUPER_ADMIN", "超级管理员"));
        link(nobodyId, seedRole(NO_PERMISSION_ROLE, "测试用无权限角色"));
    }

    @Test
    @DisplayName("按对象追溯：两条日志按操作时间倒序返回，最新的一条在首位")
    void shouldReturnEntriesOfOneObjectInReverseChronologicalOrder() throws Exception {
        String bizId = uniqueBizId("order");
        auditRecorder.record(new AuditEntry(AuditBizType.OWNER, bizId, AuditAction.CREATE,
                "新建货主", null, Map.of("name", "测试货主")));
        Thread.sleep(WRITE_GAP_MILLIS);
        auditRecorder.record(new AuditEntry(AuditBizType.OWNER, bizId, AuditAction.UPDATE,
                "修改结算主体", Map.of("settlementSubject", "旧主体"),
                Map.of("settlementSubject", "新主体")));

        JsonNode page = auditPage(consoleToken(), AuditBizType.OWNER, bizId, null, null);

        assertThat(page.path("code").asInt()).as("查询审计日志失败：%s", page).isZero();
        assertThat(page.path("data").path("total").asLong()).isEqualTo(2);
        JsonNode records = page.path("data").path("records");
        assertThat(records.size()).isEqualTo(2);
        // 规格场景「按对象追溯」要求按时间倒序：最新的一条必须是后写进去的那条
        assertThat(records.get(0).path("summary").asText()).isEqualTo("修改结算主体");
        assertThat(records.get(1).path("summary").asText()).isEqualTo("新建货主");
        assertThat(createdAtOf(records.get(0))).isAfterOrEqualTo(createdAtOf(records.get(1)));
        // 每行都要带出规格要求的操作人、操作时间、操作类型、摘要与来源端字段
        JsonNode latest = records.get(0);
        assertThat(latest.path("bizType").asText()).isEqualTo("OWNER");
        assertThat(latest.path("bizId").asText()).isEqualTo(bizId);
        assertThat(latest.path("action").asText()).isEqualTo("UPDATE");
        assertThat(latest.path("operatorName").asText()).isEqualTo("系统");
        assertThat(latest.path("createdAt").asText()).isNotBlank();
        // 直接调用没有会话：操作人标识必须为空，姓名取系统值，而不是被填一个假的操作人。
        // 来源端同样为空——测试线程上那份 Mock 请求的 URI 是空的，认不出端；生产里系统内部触发
        // 根本没有请求上下文，两种形态都应当落到空值而不是自造一个端
        assertThat(latest.path("operatorId").isNull()).isTrue();
        assertThat(latest.path("sourcePortal").isNull()).isTrue();
    }

    @Test
    @DisplayName("快照往返：jsonb 不丢字段，嵌套结构与显式 null 都原样读回")
    void shouldRoundTripSnapshotsThroughJsonb() throws Exception {
        String bizId = uniqueBizId("snapshot");
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("weight", Map.of("value", 12.5, "unit", "KG"));
        before.put("tags", List.of("易碎", "冷藏"));
        before.put("remark", null);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("weight", Map.of("value", 13.0, "unit", "KG"));
        after.put("tags", List.of("易碎"));
        after.put("remark", "改过重量");
        // 子表类数据的快照是完整集合而不是差异（规格「审计日志」第二段）：这里用一个集合字段承载
        Map<String, Object> beforeContacts = Map.of("contacts", List.of(
                Map.of("id", 1, "name", "张三", "primary", true),
                Map.of("id", 2, "name", "李四", "primary", false)));
        Map<String, Object> afterContacts = Map.of("contacts", List.of(
                Map.of("id", 1, "name", "张三", "primary", true)));

        auditRecorder.record(new AuditEntry(AuditBizType.SKU, bizId, AuditAction.UPDATE,
                "修改重量", before, after));
        auditRecorder.record(new AuditEntry(AuditBizType.OWNER_CONTACT, bizId, AuditAction.DELETE,
                "删除联系人", beforeContacts, afterContacts));

        JsonNode skuRow = recordOf(consoleToken(), AuditBizType.SKU, bizId);
        // 逐字段比对而不是只比「非空」：jsonb 会把键重排、把数字归一，丢了哪一个字段只有深比才能发现
        assertThat(toMap(skuRow.path("beforeValue"))).isEqualTo(before);
        assertThat(toMap(skuRow.path("afterValue"))).isEqualTo(after);
        // 删除没有「变更后」值：必须落 SQL NULL 并原样返回 null，而不是空对象
        assertThat(skuRow.path("beforeValue").isNull()).isFalse();
        assertThat(skuRow.path("afterValue").isNull()).isFalse();

        JsonNode contactRow = recordOf(consoleToken(), AuditBizType.OWNER_CONTACT, bizId);
        assertThat(toMap(contactRow.path("beforeValue"))).isEqualTo(beforeContacts);
        assertThat(toMap(contactRow.path("afterValue"))).isEqualTo(afterContacts);

        // 字段顺序不参与比较：before 是 LinkedHashMap、读回来是 jsonb 重排后的对象，深比只看键值
        assertThat(contactRow.path("action").asText()).isEqualTo("DELETE");
    }

    @Test
    @DisplayName("参数校验：缺对象标识、分页越界都返回 40000，且不返回任何记录")
    void shouldRejectInvalidQueryParameters() throws Exception {
        String token = consoleToken();

        JsonNode missingBizId = auditPage(token, AuditBizType.OWNER, null, null, null);
        assertThat(missingBizId.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(missingBizId.path("message").asText()).isEqualTo("审计对象标识不能为空");

        JsonNode missingBizType = auditPage(token, null, "OWNER0001", null, null);
        assertThat(missingBizType.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(missingBizType.path("message").asText()).isEqualTo("审计对象类型不能为空");

        JsonNode badPageNo = auditPage(token, AuditBizType.OWNER, "OWNER0001", 0, 20);
        assertThat(badPageNo.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(badPageNo.path("message").asText()).isEqualTo("页码必须从 1 开始");

        JsonNode badPageSize = auditPage(token, AuditBizType.OWNER, "OWNER0001", 1, 201);
        assertThat(badPageSize.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(badPageSize.path("message").asText()).isEqualTo("每页条数必须在 1 到 200 之间");
    }

    @Test
    @DisplayName("无 system:audit:list 权限的用户：查询返回 HTTP 403 与 40300")
    void shouldRejectQueryWithoutPermission() throws Exception {
        String token = login(NO_PERMISSION_LOGIN_NAME).path("data").path("token").asText();

        mockMvc.perform(get("/api/console/audit-logs?bizType=OWNER&bizId=OWNER0001")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));
    }

    @Test
    @DisplayName("来源端约束：V038 的 CHECK 拒绝两端之外的值")
    void shouldRejectUnknownSourcePortal() {
        // 约束只是最后一道防线，但它必须是真的：写成 CHECK (source_portal IN (...)) 却漏了 NOT VALID
        // 之类的差异，只会在脏数据已经进库之后才暴露
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO sys_audit_log (biz_type, biz_id, action, operator_name, source_portal)
                VALUES ('OWNER', ?, 'UPDATE', '测试', 'PDA')
                """, uniqueBizId("check")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_audit_source_portal");

        // 允许为空：系统内部触发没有「端」的概念，历史数据也没有这个值
        jdbcTemplate.update("""
                INSERT INTO sys_audit_log (biz_type, biz_id, action, operator_name)
                VALUES ('OWNER', ?, 'UPDATE', '测试')
                """, uniqueBizId("null-portal"));
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

    /**
     * 调查询端点并返回完整响应体。
     *
     * <p>参数为 {@code null} 时整个参数不传：「没填」是要覆盖的一种入参形态（必填项校验），
     * 传空串是另一种（空白归一），两者分开由对应用例各自表达。
     */
    private JsonNode auditPage(String token, AuditBizType bizType, String bizId,
                               Integer pageNo, Integer pageSize) throws Exception {
        var request = get("/api/console/audit-logs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (bizType != null) {
            request = request.param("bizType", bizType.name());
        }
        if (bizId != null) {
            request = request.param("bizId", bizId);
        }
        if (pageNo != null) {
            request = request.param("pageNo", Integer.toString(pageNo));
        }
        if (pageSize != null) {
            request = request.param("pageSize", Integer.toString(pageSize));
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** 取某个对象的第一条日志；没有匹配时直接失败，避免后续断言被空对象带偏。 */
    private JsonNode recordOf(String token, AuditBizType bizType, String bizId) throws Exception {
        JsonNode page = auditPage(token, bizType, bizId, null, null);
        assertThat(page.path("code").asInt()).as("查询审计日志失败：%s", page).isZero();
        JsonNode records = page.path("data").path("records");
        assertThat(records.size()).as("对象 %s/%s 没有任何审计日志", bizType, bizId).isPositive();
        return records.get(0);
    }

    private static Instant createdAtOf(JsonNode record) {
        return Instant.parse(record.path("createdAt").asText());
    }

    /** 把快照节点转回 Map，用于与写入前的内容做深比较。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode node) {
        return objectMapper.convertValue(node, Map.class);
    }

    /** 每个用例用自己的对象标识：整个测试 JVM 共用一个库，不隔离会让倒序断言互相干扰。 */
    private static String uniqueBizId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
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

package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录取证的端到端验证：真实 HTTP 登录 → 真实 PostgreSQL 的 {@code sys_audit_log}。
 *
 * <p>这里要证的是单测证不了的三件事：
 * <ul>
 *   <li><b>来源端真的落对了</b>——两端共用 {@code /api/auth/login}，路径前缀判不出端，来源端由会话里的
 *       主体类型回退得出。员工登录必须记 {@code CONSOLE}、货主账号必须记 {@code PORTAL}；回退若没生效，
 *       两行都会是空值，而空值在规格里是「没有端的概念」，不是「没认出来」；</li>
 *   <li><b>失败也留痕，且没有被回滚</b>——登录用例不开事务，审计写入自建事务先行提交，随后抛出的
 *       {@code BizException} 回滚不到它。断言库里确实有一行，比断言「调用了一次 recorder」强得多：
 *       前者能发现被回滚，后者发现不了；</li>
 *   <li><b>审计里没有凭据</b>——审计只追加，任何密码信息一旦写进去就撤不回来。</li>
 * </ul>
 *
 * <p>查询直接走 {@code JdbcTemplate} 而不是审计查询端点：登录审计的 {@code biz_id} 是登录名，用端点查
 * 需要先让另一个账号登录，那本身会再产生一行 LOGIN 记录；而端点的字段映射已由
 * {@code AuditLogIntegrationTest} 覆盖，这里要钉的是写入侧的列值。
 *
 * <p>用例之间不隔离数据（整个测试 JVM 共用一个库）：每个用例用自己的账号，断言只针对自己那个登录名
 * 下的记录，不依赖总数与执行顺序。
 */
@DisplayName("登录审计留痕")
class LoginAuditIntegrationTest extends AbstractDataScopeIntegrationTest {

    /** 员工账号：登录成功要记 CONSOLE。 */
    private static final String STAFF_LOGIN_NAME = "login-audit-staff";

    /** 货主账号：登录成功要记 PORTAL。 */
    private static final String OWNER_LOGIN_NAME = "login-audit-owner";

    /** 密码错误的账号：与上面两个分开，失败计数不干扰它们的成功断言。 */
    private static final String WRONG_PASSWORD_LOGIN_NAME = "login-audit-wrong-password";

    /** 员工账号挂的角色：用来验证 {@code after.roles} 里是登录当时生效的角色编码。 */
    private static final String STAFF_ROLE_CODE = "TEST_LOGIN_AUDIT_ROLE";

    /** 货主编码与 SKU 前缀：全局唯一索引，用例重复执行时复用（见基类的造数约定）。 */
    private static final String OWNER_CODE = "LOGINAUDIT01";
    private static final String OWNER_SKU_PREFIX = "AUD8";

    private static final String WRONG_PASSWORD = "Wrong@1234";

    /** 登录失败的错误码：账号不存在与密码错误共用 40101。 */
    private static final int LOGIN_FAILED_CODE = 40101;

    @BeforeEach
    void prepareAccounts() {
        long staffId = seedStaff(STAFF_LOGIN_NAME, "登录审计员工");
        link(staffId, seedRole(STAFF_ROLE_CODE, "登录审计角色"));
        long ownerId = seedOwner(OWNER_CODE, "登录审计货主", OWNER_SKU_PREFIX, "ACTIVE");
        seedOwnerAccount(OWNER_LOGIN_NAME, "登录审计货主账号", ownerId);
        seedStaff(WRONG_PASSWORD_LOGIN_NAME, "登录审计密码错误账号");
    }

    @Test
    @DisplayName("员工登录成功：一条 USER/LOGIN，来源端 CONSOLE，快照含主体类型、角色与改密标记")
    void shouldRecordConsolePortalForStaffLogin() throws Exception {
        JsonNode response = login(STAFF_LOGIN_NAME);

        assertThat(response.path("data").path("subjectType").asText()).isEqualTo("STAFF");
        List<Map<String, Object>> rows = loginAudits(STAFF_LOGIN_NAME);
        assertThat(rows).as("员工登录成功应恰好产生一条审计").hasSize(1);

        Map<String, Object> row = rows.get(0);
        // 登录的主体是账号：对象类型 USER、动作 LOGIN、标识用登录名（管理员按登录名查得到）
        assertThat(row.get("action")).isEqualTo("LOGIN");
        assertThat(row.get("source_portal"))
                .as("路径判不出端时必须回退到会话主体类型，员工只能来自仓网端")
                .isEqualTo("CONSOLE");
        // 有会话：操作人是刚登录的那个人，不是「系统」
        assertThat(row.get("operator_id")).isNotNull();
        assertThat(row.get("operator_name")).isEqualTo("登录审计员工");
        // 登录没有「变更前」，变更后是登录当时的事实快照
        assertThat(row.get("before_text")).isNull();
        JsonNode after = snapshotOf(row);
        assertThat(after.path("loginName").asText()).isEqualTo(STAFF_LOGIN_NAME);
        assertThat(after.path("subjectType").asText()).isEqualTo("STAFF");
        // 角色取登录当时生效的那一份，与登录响应里的 roles 同源
        assertThat(textsOf(after.path("roles"))).containsExactly(STAFF_ROLE_CODE);
        assertThat(after.path("mustChangePassword").asBoolean()).isFalse();
        assertNoPassword(rows);
    }

    @Test
    @DisplayName("货主账号登录成功：来源端 PORTAL，快照里的主体类型是 OWNER")
    void shouldRecordPortalForOwnerLogin() throws Exception {
        JsonNode response = login(OWNER_LOGIN_NAME);

        assertThat(response.path("data").path("subjectType").asText()).isEqualTo("OWNER");
        List<Map<String, Object>> rows = loginAudits(OWNER_LOGIN_NAME);
        assertThat(rows).hasSize(1);

        Map<String, Object> row = rows.get(0);
        assertThat(row.get("action")).isEqualTo("LOGIN");
        assertThat(row.get("source_portal"))
                .as("货主账号只能从货主端登录，回退结果必须是 PORTAL")
                .isEqualTo("PORTAL");
        assertThat(row.get("operator_name")).isEqualTo("登录审计货主账号");
        assertThat(snapshotOf(row).path("subjectType").asText()).isEqualTo("OWNER");
        assertNoPassword(rows);
    }

    @Test
    @DisplayName("密码错误：仍有一条审计，来源端为空，摘要是「密码错误」，且没有被回滚")
    void shouldRecordAuditForWrongPassword() throws Exception {
        JsonNode response = loginWith(WRONG_PASSWORD_LOGIN_NAME, WRONG_PASSWORD);

        // 对外仍是与「账号不存在」共用的错误码与提示，防账号枚举
        assertThat(response.path("code").asInt()).isEqualTo(LOGIN_FAILED_CODE);

        List<Map<String, Object>> rows = loginAudits(WRONG_PASSWORD_LOGIN_NAME);
        // 这条断言同时证明了「失败留痕不会被回滚」：若审计加入了随后被回滚的事务，这里一行都不会有
        assertThat(rows).as("登录失败必须留痕，且不被业务异常回滚").hasSize(1);

        Map<String, Object> row = rows.get(0);
        assertThat(row.get("action")).isEqualTo("LOGIN");
        assertThat((String) row.get("summary"))
                .startsWith("登录失败：密码错误")
                .contains("来源端未知");
        // 失败时两端共用一个 URL 且没有会话可供回退：来源端如实留空，不猜一个端
        assertThat(row.get("source_portal")).isNull();
        assertThat(row.get("before_text")).isNull();
        assertThat(row.get("after_text")).isNull();
        // 失败时没有登录主体，操作人记为系统值而不是随便归因到某个人
        assertThat(row.get("operator_id")).isNull();
        assertThat(row.get("operator_name")).isEqualTo("系统");
        assertNoPassword(rows);
    }

    @Test
    @DisplayName("账号不存在：对外与密码错误同一错误码，审计里记真实原因")
    void shouldRecordAuditForUnknownLoginName() throws Exception {
        String unknownLoginName = "login-audit-nobody";

        JsonNode response = loginWith(unknownLoginName, PASSWORD);

        assertThat(response.path("code").asInt()).isEqualTo(LOGIN_FAILED_CODE);
        List<Map<String, Object>> rows = loginAudits(unknownLoginName);
        assertThat(rows).hasSize(1);
        // 账号不存在没有账号标识可用，对象标识就是用户尝试的那个名字
        assertThat((String) rows.get(0).get("summary")).startsWith("登录失败：账号不存在");
        assertThat(rows.get(0).get("source_portal")).isNull();
    }

    /**
     * 查某个登录名下的全部登录审计，按写入顺序返回。
     *
     * <p>{@code before_value / after_value} 取 {@code ::text}：要断言的是「密码明文没有出现在任何一列」，
     * 走 {@code jsonb} 的 Java 映射反而看不到原始文本。
     *
     * @param loginName 登录名，对应 {@code biz_id}
     * @return 该登录名下的日志行，最早的一条在前
     */
    private List<Map<String, Object>> loginAudits(String loginName) {
        return jdbcTemplate.queryForList("""
                SELECT action,
                       operator_id,
                       operator_name,
                       source_portal,
                       summary,
                       before_value::text AS before_text,
                       after_value::text AS after_text
                FROM sys_audit_log
                WHERE biz_type = 'USER'
                  AND biz_id = ?
                ORDER BY id
                """, loginName);
    }

    /**
     * 把一条日志的 {@code after_value} 读成 JSON 树。
     *
     * <p>不直接对 {@code jsonb::text} 做字符串比对：{@code jsonb} 会重排键、把冒号渲染成
     * {@code ": "}，按字面拼接断言测到的是 PostgreSQL 的输出格式，而不是快照内容。
     *
     * @param row 一条日志行
     * @return 变更后快照；该列为空时提交失败
     */
    private JsonNode snapshotOf(Map<String, Object> row) throws Exception {
        String text = (String) row.get("after_text");
        assertThat(text).as("变更后快照不应为空：%s", row).isNotNull();
        return objectMapper.readTree(text);
    }

    /**
     * 断言这些日志里没有任何一列出现账号密码明文。
     *
     * <p>按值判定而不是按字段名：{@code after} 里刻意带着 {@code mustChangePassword} 这个状态位，
     * 按名字含 {@code password} 扫描会把它误判成凭据。密码一旦写进只追加的审计就撤不回来。
     *
     * @param rows 同一登录名下的日志行
     */
    private static void assertNoPassword(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            for (Object value : row.values()) {
                if (value == null) {
                    continue;
                }
                assertThat(String.valueOf(value))
                        .as("审计里不得出现密码明文：%s", row)
                        .doesNotContain(PASSWORD, WRONG_PASSWORD);
            }
        }
    }

    /** 用给定密码登录一次并返回响应；不在这里断言成功，失败用例要看的就是失败响应。 */
    private JsonNode loginWith(String loginName, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(loginName, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}

package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户与角色审计留痕的端到端验证：真实 HTTP 请求 → 真实 PostgreSQL 的 {@code sys_audit_log} → 查询端点。
 *
 * <p>这一批要证的是单测证不了的东西：十一个留痕点里的五个用户操作与四个角色操作真的把内容落进了库、
 * 快照经 {@code jsonb} 往返后还在、按登录名与角色编码查得到，而且日志里没有一个字节来自凭据。
 * 单测把 {@code AuditRecorder} 换成了 mock，只能证明「调用发生在正确的时机、参数是这些值」。
 *
 * <p>要钉住四件事：
 * <ul>
 *   <li><b>一条链按业务标识可追溯</b>——建号、编辑、停用、分配角色四条记录挂在同一个登录名下，
 *       建角色、编辑、删除三条挂在同一个角色编码下，按时间倒序返回。这正是管理端看「这个账号经历过什么」
 *       的形态；对象标识若用主键，这些记录一条都查不到；</li>
 *   <li><b>动作分类</b>——分配角色的动作是 {@code PERMISSION_CHANGE}，而不是与「改个展示名」共用的
 *       {@code UPDATE}；</li>
 *   <li><b>快照形状</b>——新增没有「变更前」，删除没有「变更后」，停用的方向写在摘要里；</li>
 *   <li><b>没有凭据</b>——初始密码明文与库里的密码哈希都不出现在任何一条记录里。</li>
 * </ul>
 *
 * <p>操作人用 SQL 造一个挂着内置超管角色的已改密账号：超管权限在鉴权时动态展开，因此它天然拥有
 * {@code system:user:*} / {@code system:role:*} 与 {@code system:audit:list} 的权限点。
 *
 * <p>用例之间不隔离数据（整个测试 JVM 共用一个库）：每个用例用自己的登录名与角色编码，
 * 断言只针对自己那条链下的记录，不依赖总数与执行顺序。
 */
@DisplayName("用户与角色审计留痕")
class UserRoleAuditIntegrationTest extends AbstractDataScopeIntegrationTest {

    /** 操作人账号：已改密、已启用，挂内置超管角色。 */
    private static final String OPERATOR_LOGIN_NAME = "user-role-audit-operator";

    /** 新建账号的初始密码；用于断言它一个字符都没有进审计表。 */
    private static final String INITIAL_PASSWORD = "Audit@1234";

    /** 编辑时提交的新展示名，与建号时不同，否则那次提交会被「空提交不留痕」跳过。 */
    private static final String UPDATED_DISPLAY_NAME = "审计留痕账号（改名）";

    @BeforeEach
    void prepareAccount() {
        link(seedStaff(OPERATOR_LOGIN_NAME, "用户角色审计操作员"), superAdminRole());
    }

    @Test
    @DisplayName("按登录名追溯：建号 → 编辑 → 停用 → 分配角色四条，时间倒序，含 PERMISSION_CHANGE")
    void shouldTraceUserChainInReverseChronologicalOrder() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        String loginName = uniqueSuffix("audit-user-");
        String roleId = createdRoleId(createRole(token, uniqueRoleCode("AUD_R"), "审计留痕角色", List.of()));

        JsonNode created = postJson(token, "/api/console/users",
                objectMapper.writeValueAsString(createUserPayload(loginName)));
        assertThat(created.path("code").asInt()).as("建号失败：%s", created).isZero();
        String userId = created.path("data").path("id").asText();

        // 编辑：只换展示名，邮箱保持原值——内部员工清空邮箱会被 ck_user_email_required 拦下
        JsonNode updated = putJson(token, "/api/console/users/" + userId,
                objectMapper.writeValueAsString(new UpdateUserPayload(
                        UPDATED_DISPLAY_NAME, emailOf(loginName), null)));
        assertThat(updated.path("code").asInt()).as("编辑失败：%s", updated).isZero();

        JsonNode disabled = putJson(token, "/api/console/users/" + userId + "/status",
                objectMapper.writeValueAsString(new StatusPayload("DISABLED")));
        assertThat(disabled.path("code").asInt()).as("停用失败：%s", disabled).isZero();

        JsonNode assigned = putJson(token, "/api/console/users/" + userId + "/roles",
                objectMapper.writeValueAsString(new AssignRolesPayload(List.of(roleId))));
        assertThat(assigned.path("code").asInt()).as("分配角色失败：%s", assigned).isZero();

        JsonNode records = auditRecords(token, "USER", loginName);

        assertThat(records.size())
                .as("建号、编辑、停用、分配角色各一条，且都挂在登录名 %s 下", loginName)
                .isEqualTo(4);
        JsonNode roleAssignment = records.get(0);
        JsonNode statusChange = records.get(1);
        JsonNode edit = records.get(2);
        JsonNode creation = records.get(3);

        // 倒序：最新的一条在最前；摘要要让人一眼看懂「谁对什么做了什么」
        assertThat(roleAssignment.path("summary").asText()).isEqualTo("分配角色 " + loginName);
        assertThat(statusChange.path("summary").asText()).isEqualTo("停用用户 " + loginName);
        assertThat(edit.path("summary").asText()).isEqualTo("修改用户 " + loginName);
        assertThat(creation.path("summary").asText()).isEqualTo("新建用户 " + loginName);
        assertThat(createdAtOf(roleAssignment)).isAfterOrEqualTo(createdAtOf(statusChange));
        assertThat(createdAtOf(statusChange)).isAfterOrEqualTo(createdAtOf(edit));
        assertThat(createdAtOf(edit)).isAfterOrEqualTo(createdAtOf(creation));

        for (JsonNode record : records) {
            assertThat(record.path("bizType").asText()).isEqualTo("USER");
            assertThat(record.path("bizId").asText()).isEqualTo(loginName);
            // 来源端由实现按请求路径前缀判定：调用来自仓网端，就只能是 CONSOLE
            assertThat(record.path("sourcePortal").asText()).isEqualTo("CONSOLE");
            // 这条链路有会话，操作人必须记下来；记成「系统」说明会话没读到
            assertThat(record.path("operatorId").asText()).isNotBlank();
            assertThat(record.path("operatorName").asText()).isNotEqualTo("系统");
            assertNoCredentials(record, loginName);
        }

        // 建档：没有变更前值（落 SQL NULL，不是空对象），变更后从登录名开始
        assertThat(creation.path("action").asText()).isEqualTo("CREATE");
        assertThat(creation.path("beforeValue").isNull()).isTrue();
        assertThat(creation.path("afterValue").path("loginName").asText()).isEqualTo(loginName);
        assertThat(creation.path("afterValue").path("status").asText()).isEqualTo("ACTIVE");
        assertThat(creation.path("afterValue").path("mustChangePassword").asBoolean()).isTrue();

        // 编辑：前后都有，状态一致——编辑改不到状态，日志不能暗示发生了一次流转
        assertThat(edit.path("action").asText()).isEqualTo("UPDATE");
        assertThat(edit.path("beforeValue").path("displayName").asText()).isEqualTo("审计留痕账号");
        assertThat(edit.path("afterValue").path("displayName").asText()).isEqualTo(UPDATED_DISPLAY_NAME);
        assertThat(edit.path("afterValue").path("status").asText())
                .isEqualTo(edit.path("beforeValue").path("status").asText());

        // 停用：方向写在摘要里，快照只差状态
        assertThat(statusChange.path("action").asText()).isEqualTo("UPDATE");
        assertThat(statusChange.path("beforeValue").path("status").asText()).isEqualTo("ACTIVE");
        assertThat(statusChange.path("afterValue").path("status").asText()).isEqualTo("DISABLED");

        // 权限变更：动作与其它 UPDATE 分开，管理员按动作筛选时不会漏掉它
        assertThat(roleAssignment.path("action").asText()).isEqualTo("PERMISSION_CHANGE");
    }

    @Test
    @DisplayName("分配角色的留痕带得出「改成了哪些角色」：before 是改前的编码，after 是改后的")
    void shouldRecordRoleCodesAroundAssignment() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        String loginName = uniqueSuffix("audit-roles-");
        // 编码前缀决定字典序：AUD_A_* 在 AUD_B_* 之前，断言才能写死排序结果
        String codeA = uniqueRoleCode("AUD_A");
        String codeB = uniqueRoleCode("AUD_B");
        String roleA = createdRoleId(createRole(token, codeA, "留痕角色甲", List.of()));
        String roleB = createdRoleId(createRole(token, codeB, "留痕角色乙", List.of()));

        JsonNode created = postJson(token, "/api/console/users",
                objectMapper.writeValueAsString(createUserPayload(loginName)));
        assertThat(created.path("code").asInt()).as("建号失败：%s", created).isZero();
        String userId = created.path("data").path("id").asText();

        // 第一次：从一个角色都没有变成只有甲
        JsonNode first = putJson(token, "/api/console/users/" + userId + "/roles",
                objectMapper.writeValueAsString(new AssignRolesPayload(List.of(roleA))));
        assertThat(first.path("code").asInt()).as("分配角色失败：%s", first).isZero();

        // 第二次：甲 + 乙，改前的值应当正好是上一步的结果
        JsonNode second = putJson(token, "/api/console/users/" + userId + "/roles",
                objectMapper.writeValueAsString(new AssignRolesPayload(List.of(roleA, roleB))));
        assertThat(second.path("code").asInt()).as("分配角色失败：%s", second).isZero();

        List<JsonNode> assignments = new ArrayList<>();
        for (JsonNode record : auditRecords(token, "USER", loginName)) {
            if (record.path("action").asText().equals("PERMISSION_CHANGE")) {
                assignments.add(record);
            }
        }
        assertThat(assignments).as("两次分配各一条留痕").hasSize(2);
        JsonNode latest = assignments.get(0);
        JsonNode earlier = assignments.get(1);

        // 两份快照必须不同：相等时这条日志只说得清「谁动了权限」，说不清「动成了什么」
        assertThat(roleCodesOf(latest, "beforeValue")).containsExactly(codeA);
        assertThat(roleCodesOf(latest, "afterValue")).containsExactly(codeA, codeB);
        assertThat(roleCodesOf(earlier, "beforeValue")).isEmpty();
        assertThat(roleCodesOf(earlier, "afterValue")).containsExactly(codeA);
        // 「一个角色都没有」落成空数组而不是 null：null 与键缺失在读日志时无法区分
        assertThat(earlier.path("beforeValue").path("roleCodes").isArray()).isTrue();
        assertThat(latest.path("beforeValue").path("roleCodes").isArray()).isTrue();
    }

    @Test
    @DisplayName("按角色编码追溯：建角色 → 编辑 → 删除三条，时间倒序，删除的 after 为 null")
    void shouldTraceRoleChainInReverseChronologicalOrder() throws Exception {
        String token = loginToken(OPERATOR_LOGIN_NAME);
        String roleCode = uniqueRoleCode("AUD_ROLE");

        JsonNode created = createRole(token, roleCode, "审计留痕角色", List.of());
        assertThat(created.path("code").asInt()).as("建角色失败：%s", created).isZero();
        String roleId = created.path("data").path("id").asText();

        JsonNode updated = putJson(token, "/api/console/roles/" + roleId,
                objectMapper.writeValueAsString(
                        new UpdateRolePayload("审计留痕角色（改名）", "留痕用例", List.of())));
        assertThat(updated.path("code").asInt()).as("编辑角色失败：%s", updated).isZero();

        // 角色没有被任何账号占用，删除应当成功；这条链路只有它自己，因此不会与其他用例互相影响
        JsonNode deleted = deleteJson(token, "/api/console/roles/" + roleId);
        assertThat(deleted.path("code").asInt()).as("删除角色失败：%s", deleted).isZero();

        JsonNode records = auditRecords(token, "ROLE", roleCode);

        assertThat(records.size())
                .as("建角色、编辑、删除各一条，且都挂在角色编码 %s 下", roleCode)
                .isEqualTo(3);
        JsonNode deletion = records.get(0);
        JsonNode edit = records.get(1);
        JsonNode creation = records.get(2);

        assertThat(deletion.path("summary").asText()).isEqualTo("删除角色 " + roleCode);
        assertThat(edit.path("summary").asText()).isEqualTo("修改角色 " + roleCode);
        assertThat(creation.path("summary").asText()).isEqualTo("新建角色 " + roleCode);
        assertThat(createdAtOf(deletion)).isAfterOrEqualTo(createdAtOf(edit));
        assertThat(createdAtOf(edit)).isAfterOrEqualTo(createdAtOf(creation));

        for (JsonNode record : records) {
            assertThat(record.path("bizType").asText()).isEqualTo("ROLE");
            assertThat(record.path("bizId").asText()).isEqualTo(roleCode);
            assertThat(record.path("sourcePortal").asText()).isEqualTo("CONSOLE");
        }

        assertThat(creation.path("action").asText()).isEqualTo("CREATE");
        assertThat(creation.path("beforeValue").isNull()).isTrue();
        assertThat(creation.path("afterValue").path("builtin").asBoolean()).isFalse();
        assertThat(creation.path("afterValue").path("status").asText()).isEqualTo("ACTIVE");

        assertThat(edit.path("action").asText()).isEqualTo("UPDATE");
        assertThat(edit.path("beforeValue").path("name").asText()).isEqualTo("审计留痕角色");
        assertThat(edit.path("afterValue").path("name").asText()).isEqualTo("审计留痕角色（改名）");

        // 删除是审计唯一能证明「这条记录曾经存在过」的场景，after 必须如实为空
        assertThat(deletion.path("action").asText()).isEqualTo("DELETE");
        assertThat(deletion.path("beforeValue").path("code").asText()).isEqualTo(roleCode);
        assertThat(deletion.path("afterValue").isNull()).isTrue();
    }

    /**
     * 按业务标识追溯：调查询端点并返回记录数组。
     *
     * <p>只查 {@code bizType} 与 {@code bizId}：对象标识用登录名与角色编码而不是主键，
     * 正是这一批要证的追溯方式。
     *
     * @param token   操作人令牌，需持有 {@code system:audit:list}
     * @param bizType 审计对象类型编码
     * @param bizId   对象标识：登录名或角色编码
     * @return 记录数组，按时间倒序
     */
    private JsonNode auditRecords(String token, String bizType, String bizId) throws Exception {
        JsonNode page = getJson(token, "/api/console/audit-logs?bizType=" + bizType + "&bizId=" + bizId);
        assertThat(page.path("code").asInt()).as("查询审计日志失败：%s", page).isZero();
        JsonNode records = page.path("data").path("records");
        assertThat(page.path("data").path("total").asLong())
                .as("total 与 records 必须一致：%s", page)
                .isEqualTo(records.size());
        return records;
    }

    /**
     * 取一条审计记录某一侧快照里的角色编码。
     *
     * @param record 一条审计日志
     * @param side   {@code beforeValue} 或 {@code afterValue}
     * @return 角色编码，按落库顺序；快照里没有这个数组时为空列表
     */
    private static List<String> roleCodesOf(JsonNode record, String side) {
        return textsOf(record.path(side).path("roleCodes"));
    }

    /**
     * 断言一条记录里没有凭据。
     *
     * <p>三条判据一起用：字段名（将来真加了一个 {@code passwordHash} 就会命中）、明文
     * （字段名可以改，值不会说谎）、以及库里那份哈希（它才是「等于把凭证复制到另一张表」的那份数据）。
     *
     * @param record     一条审计日志
     * @param loginName  该账号的登录名，用于回查库里的密码哈希
     */
    private void assertNoCredentials(JsonNode record, String loginName) {
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE lower(login_name) = lower(?)", String.class, loginName);
        for (String snapshot : List.of("beforeValue", "afterValue")) {
            JsonNode node = record.path(snapshot);
            assertThat(fieldNamesOf(node))
                    .as("%s 不得包含凭据类字段：%s", snapshot, record)
                    // mustChangePassword 是状态位而不是凭据：它说明「下次登录必须先改密」
                    .noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("password")
                            && !name.equals("mustChangePassword"));
            assertThat(node.toString())
                    .as("%s 不得出现初始密码明文：%s", snapshot, record)
                    .doesNotContain(INITIAL_PASSWORD);
            assertThat(node.toString())
                    .as("%s 不得出现密码哈希：%s", snapshot, record)
                    .doesNotContain(storedHash);
        }
        // 摘要同样不带凭据：它是人读的一行字，没有理由需要知道密码
        assertThat(record.path("summary").asText()).doesNotContain(INITIAL_PASSWORD, storedHash);
    }

    /**
     * 递归收集 JSON 里的全部字段名。
     *
     * <p>只看顶层不够：快照里可能嵌对象或数组（例如 {@code menuIds}），凭据字段藏在内层时
     * 顶层扫描会放过它。
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
     * 以令牌发 DELETE 并返回响应；非 200 由 MockMvc 断言失败。
     *
     * @param token 操作人令牌
     * @param path  请求路径
     * @return 响应体
     */
    private JsonNode deleteJson(String token, String path) throws Exception {
        MvcResult result = mockMvc.perform(delete(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static Instant createdAtOf(JsonNode record) {
        return Instant.parse(record.path("createdAt").asText());
    }

    /**
     * 建号请求体：内部员工，邮箱由登录名派生，不预分配角色。
     *
     * @param loginName 登录名，全表唯一且不区分大小写
     * @return 可直接序列化的请求体
     */
    private static CreateUserPayload createUserPayload(String loginName) {
        return new CreateUserPayload(loginName, "审计留痕账号", "STAFF", null, emailOf(loginName),
                null, INITIAL_PASSWORD, List.of());
    }

    private static String emailOf(String loginName) {
        return loginName + "@example.com";
    }

    /** 每个用例用自己的登录名与编码：整个测试 JVM 共用一个库，不隔离会让倒序断言互相干扰。 */
    private static String uniqueSuffix(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 角色编码须满足 {@code NewRole.CODE_PATTERN}：大写字母开头，只含大写字母、数字与下划线。 */
    private static String uniqueRoleCode(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    /**
     * 建号请求体的最小映射：不覆盖 {@code roleIds}，建号后再单独走分配角色接口，
     * 那样「分配角色」才会留下自己的那条审计。
     *
     * @param loginName       登录名
     * @param displayName     展示名
     * @param subjectType     主体类型编码
     * @param ownerId         所属货主标识，内部员工传 {@code null}
     * @param email           邮箱
     * @param wecomUserId     企业微信 userid，可为 {@code null}
     * @param initialPassword 初始密码明文
     * @param roleIds         建号时同时分配的角色标识
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
     * 编辑用户请求体的最小映射。
     *
     * @param displayName 展示名，必填
     * @param email       邮箱，内部员工必须保留非空
     * @param wecomUserId 企业微信 userid，可为 {@code null}
     */
    private record UpdateUserPayload(String displayName, String email, String wecomUserId) {
    }

    /**
     * 编辑角色请求体的最小映射。
     *
     * @param name    角色名称，必填
     * @param remark  备注，可为 {@code null}
     * @param menuIds 替换后的授权，必传（清空请传空数组）
     */
    private record UpdateRolePayload(String name, String remark, List<String> menuIds) {
    }

    /**
     * 启停请求体的最小映射：用户与角色两个端点共用。
     *
     * @param status 目标状态编码
     */
    private record StatusPayload(String status) {
    }

    /**
     * 分配角色请求体的最小映射。
     *
     * @param roleIds 替换后的角色标识，按契约以字符串传
     */
    private record AssignRolesPayload(List<String> roleIds) {
    }
}

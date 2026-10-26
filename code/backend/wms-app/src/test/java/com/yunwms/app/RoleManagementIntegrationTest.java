package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.auth.application.dto.RolePage;
import com.yunwms.auth.application.query.RoleQueryService;
import com.yunwms.auth.domain.port.PasswordHasher;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 角色管理接口（新建 / 列表 / 详情 / 编辑 / 启停 / 删除）的端到端验证。
 *
 * <p>要证的是单测证不了的几件事：{@code V026}、{@code V029} 的四个权限点真的被接口消费、
 * 落库值真的是「非内置 + {@code CUSTOM} + 启用」、唯一索引与外键真的把冲突变成了可展示的错误码
 * 且整单回滚、列表的占用人数与授权数量真的来自同一条聚合 SQL 而不是逐行查询，
 * 以及编辑的替换语义、停用后权限当次失效、删除的占用校验与软删除副作用。
 *
 * <p>操作人不复用种子账号 {@code superadmin}：它带着「需先改密」标记，拦截器在改密前只放行
 * 改密接口；替它改密又会污染 {@code BootstrapIntegrationTest} 依赖的初始状态（两个测试类
 * 共用一个库）。这里用 SQL 造一个挂着内置超管角色的已改密账号
 * （超管权限在鉴权时动态展开，因此它天然拥有 {@code V026}、{@code V029} 新加的权限点）。
 *
 * <p>用例之间不隔离数据，因此每个用例用自己的角色编码，且断言只针对自己那条编码。
 */
@DisplayName("角色管理")
class RoleManagementIntegrationTest extends AbstractIntegrationTest {

    /** 操作人账号：已改密、已启用，挂内置超管角色。 */
    private static final String OPERATOR_LOGIN_NAME = "role-operator";
    /** 无任何权限点的账号，用于验证 403。 */
    private static final String NO_PERMISSION_LOGIN_NAME = "role-nobody";
    private static final String PASSWORD = "Admin@1234";
    /** 无权限点角色编码，用例自造；迁移里除超管外不预置角色。 */
    private static final String NO_PERMISSION_ROLE = "TEST_ROLE_NO_PERMISSION";

    /**
     * 错误码来自 {@code ErrorCode}：40000 参数问题，40300 无权访问，40400 资源不存在，
     * 40900 状态不允许，40902 角色编码已被占用。
     */
    private static final int BAD_REQUEST_CODE = 40000;
    private static final int FORBIDDEN_CODE = 40300;
    private static final int NOT_FOUND_CODE = 40400;
    private static final int CONFLICT_CODE = 40900;
    private static final int ROLE_CODE_TAKEN_CODE = 40902;

    /** 语句计数器，全类共用一个实例：拦截器挂在一个共享的 SqlSessionFactory 上。 */
    private static final StatementCounter STATEMENT_COUNTER = new StatementCounter();

    /**
     * 统计 MyBatis 执行了几条映射语句。
     *
     * <p>为什么要在测试里挂一个拦截器：任务要求「列表接口整体不超过 3 次 SQL」，
     * 这条约束用眼睛数是靠不住的——加一个逐行查询不会有任何编译错误，
     * 只会在角色变多之后变慢到没人能解释。把它变成一条构建期断言，退化就变成红。
     *
     * <p>只拦 {@code Executor#query(MappedStatement, Object, RowBounds, ResultHandler)} 与
     * {@code Executor#update(MappedStatement, Object)}：这两处就是「一条映射语句一次」的位置。
     * 本项目没有懒加载与嵌套结果映射，所以一条映射语句对应一条 SQL。
     *
     * <p>必须声明 {@link Intercepts}：MyBatis 靠它判断插件该包住哪些方法，缺了会在创建会话时
     * 直接抛 {@code PluginException}，把整个上下文带坏。
     */
    @Intercepts({
            @Signature(type = Executor.class, method = "query",
                    args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
            @Signature(type = Executor.class, method = "update",
                    args = {MappedStatement.class, Object.class})
    })
    private static final class StatementCounter implements Interceptor {

        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            count.incrementAndGet();
            return invocation.proceed();
        }

        @Override
        public Object plugin(Object target) {
            return Plugin.wrap(target, this);
        }

        @Override
        public void setProperties(Properties properties) {
            // 无配置项
        }

        private int count() {
            return count.get();
        }

        private void reset() {
            count.set(0);
        }
    }

    /** 拦截器只能挂一次：挂两次会把每条语句数成两条。上下文在测试类之间复用，因此用静态标记。 */
    private static boolean counterInstalled;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Autowired
    private RoleQueryService roleQueryService;

    @BeforeEach
    void prepareAccounts() {
        if (!counterInstalled) {
            // 运行期挂载即可：MyBatis 在每次创建 Executor 时读取插件链，挂载后的下一次请求就会生效
            sqlSessionFactory.getConfiguration().addInterceptor(STATEMENT_COUNTER);
            counterInstalled = true;
        }
        STATEMENT_COUNTER.reset();

        Long operatorId = seedAccount(OPERATOR_LOGIN_NAME, "角色操作员", "role-operator@example.com");
        Long nobodyId = seedAccount(NO_PERMISSION_LOGIN_NAME, "无权限用户", "role-nobody@example.com");
        // 超管权限由鉴权时动态展开，这里不写 sys_role_menu：那正是规格禁止的做法
        link(operatorId, seedRole("SUPER_ADMIN", "超级管理员"));
        link(nobodyId, seedRole(NO_PERMISSION_ROLE, "测试用无权限角色"));
    }

    @Test
    @DisplayName("超管建角色：落库为非内置、指定货主、启用，详情返回的 menuIds 与提交一致")
    void shouldCreateRoleAndReturnMenuIdsInDetail() throws Exception {
        List<Long> submitted = List.of(roleMenuId(), listPermissionId(), createPermissionId());
        String code = "ROLE_DETAIL";

        JsonNode created = createRole(consoleToken(), code, "客服", "对接货主", submitted);

        assertThat(created.path("code").asInt()).as("建角色失败：%s", created).isZero();
        // 标识以字符串返回：超过 2^53 的 Long 在 JS 里会丢精度
        String roleId = created.path("data").path("id").asText();
        assertThat(roleId).isNotBlank();
        assertThat(created.path("data").path("code").asText()).isEqualTo(code);

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT builtin, data_scope_type, status, remark, created_by, updated_by
                FROM sys_role WHERE code = ?
                """, code);
        // 三个固定值都不接受请求参数，这里确认它们真的按固定值落了库
        assertThat(row.get("builtin")).as("接口不能创建内置角色").isEqualTo(false);
        assertThat(row.get("data_scope_type")).as("数据范围属于 2.4，本批固定 CUSTOM").isEqualTo("CUSTOM");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("remark")).isEqualTo("对接货主");
        Long operatorId = userId(OPERATOR_LOGIN_NAME);
        assertThat(row.get("created_by")).isEqualTo(operatorId);
        assertThat(row.get("updated_by")).isEqualTo(operatorId);

        JsonNode detail = getJson(consoleToken(), "/api/console/roles/" + roleId);

        assertThat(detail.path("code").asInt()).isZero();
        JsonNode detailData = detail.path("data");
        assertThat(detailData.path("id").asText()).isEqualTo(roleId);
        assertThat(detailData.path("code").asText()).isEqualTo(code);
        assertThat(detailData.path("name").asText()).isEqualTo("客服");
        assertThat(detailData.path("builtin").asBoolean()).isFalse();
        assertThat(detailData.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(detailData.path("userCount").asLong()).isZero();
        assertThat(detailData.path("permissionCount").asLong()).isEqualTo(submitted.size());

        List<String> returned = new ArrayList<>();
        detailData.path("menuIds").forEach(node -> returned.add(node.asText()));
        // 详情按标识升序返回，因此期望值也排一次序；集合内容必须与提交的完全一致
        assertThat(returned).containsExactlyElementsOf(
                submitted.stream().sorted(Comparator.naturalOrder()).map(String::valueOf).toList());
    }

    @Test
    @DisplayName("列表：新建角色出现一次，占用人数为 0、授权数量等于勾选数")
    void shouldListCreatedRoleWithZeroUserCount() throws Exception {
        List<Long> submitted = List.of(listPermissionId());
        String code = "ROLE_LIST";
        createRole(consoleToken(), code, "列表用例角色", null, submitted);

        JsonNode response = getJson(consoleToken(), "/api/console/roles?keyword=" + code + "&pageNo=1&pageSize=20");

        assertThat(response.path("code").asInt()).isZero();
        JsonNode page = response.path("data");
        assertThat(page.path("pageNo").asInt()).isEqualTo(1);
        assertThat(page.path("pageSize").asInt()).isEqualTo(20);
        assertThat(page.path("total").asInt()).isEqualTo(1);
        JsonNode row = page.path("records").get(0);
        assertThat(row.path("code").asText()).isEqualTo(code);
        assertThat(row.path("userCount").asLong()).as("新角色还没分配给任何人").isZero();
        assertThat(row.path("permissionCount").asLong()).isEqualTo(submitted.size());
        assertThat(row.path("builtin").asBoolean()).isFalse();
        assertThat(row.path("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("列表用例：整个查询固定 2 条 SQL，与角色数量无关")
    void shouldQueryRolePageWithFixedNumberOfStatements() {
        // 直接调用用例而不是走 HTTP：HTTP 请求还会经过鉴权链路，而鉴权侧的查询是
        // SaTokenPermissionResolver 的既有行为（每次鉴权 3 条单表查询，已在该类里说明），
        // 混进来会让这条断言在测「鉴权有多快」而不是「列表有没有 N+1」。
        STATEMENT_COUNTER.reset();

        RolePage page = roleQueryService.page(1, 20, null, null);

        int statements = STATEMENT_COUNTER.count();
        // 打印实测条数，供交付时核对「整体不超过 3 次 SQL」
        System.out.println("角色列表用例 SQL 条数：" + statements);
        assertThat(statements)
                .as("列表用例执行了 %d 条 SQL（上限 3）：计数一次 + 分页一次，逐行查计数会让它随角色数量增长",
                        statements)
                .isLessThanOrEqualTo(3);
        assertThat(page.records()).as("用例返回空页时也应是 0 条而不是 null").isNotNull();
    }

    @Test
    @DisplayName("角色编码重复：返回 40902，只留一条角色")
    void shouldRejectDuplicateCode() throws Exception {
        String token = consoleToken();
        String code = "ROLE_DUP";
        assertThat(createRole(token, code, "重复用例角色", null, List.of()).path("code").asInt()).isZero();

        JsonNode duplicated = createRole(token, code, "重复用例角色", null, List.of());

        // 40902 而不是泛化的 40900：前端要能把这个提示挂在编码字段上
        assertThat(duplicated.path("code").asInt()).isEqualTo(ROLE_CODE_TAKEN_CODE);
        assertThat(duplicated.path("message").asText()).isEqualTo("角色编码已被占用（含已删除的角色）");
        assertThat(countRole(code)).isEqualTo(1);
    }

    @Test
    @DisplayName("勾选的菜单不存在：拒绝且角色未落库")
    void shouldRejectUnknownMenuId() throws Exception {
        String code = "ROLE_BAD_MENU";

        JsonNode response = createRole(consoleToken(), code, "不存在的菜单", null, List.of(999_999L));

        assertThat(response.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        // 翻译的是 sys_role_menu_menu_id_fkey：外键在插入时拦下，整单在同一个事务里回滚
        assertThat(response.path("message").asText()).isEqualTo("指定的菜单或权限点不存在");
        assertThat(countRole(code)).as("被拒绝的建角色请求不得留下角色").isZero();
    }

    @Test
    @DisplayName("请求体里带 builtin：按未知字段拒绝，角色不落库")
    void shouldRejectBuiltinInPayload() throws Exception {
        String code = "ROLE_BUILTIN";
        String body = objectMapper.writeValueAsString(Map.of(
                "code", code,
                "name", "试图造超级管理员",
                "builtin", true));

        JsonNode response = postJson(consoleToken(), "/api/console/roles", body);

        // FAIL_ON_UNKNOWN_PROPERTIES：多传的字段被拒绝，而不是「收下但忽略」
        assertThat(response.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(countRole(code)).isZero();
    }

    @Test
    @DisplayName("无 system:role:create 权限的用户建角色：HTTP 403 且角色不落库")
    void shouldRejectCreationWithoutPermission() throws Exception {
        String token = login(NO_PERMISSION_LOGIN_NAME).path("data").path("token").asText();
        String code = "ROLE_DENIED";
        String body = objectMapper.writeValueAsString(new CreateRolePayload(
                code, "无权限建的角色", null, List.of()));

        mockMvc.perform(post("/api/console/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        // 403 之外还要证明业务逻辑没有执行：只有「先拦后执行」才叫未授权接口不执行业务逻辑
        assertThat(countRole(code)).as("被拒绝的建角色请求不得留下角色").isZero();
    }

    @Test
    @DisplayName("列表每页条数超过 200：拒绝而不是静默截断")
    void shouldRejectPageSizeAboveLimit() throws Exception {
        JsonNode response = getJson(consoleToken(), "/api/console/roles?pageSize=201");

        assertThat(response.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(response.path("message").asText()).isEqualTo("每页条数必须在 1 到 200 之间");
    }

    @Test
    @DisplayName("角色不存在：详情返回 404 段错误码，HTTP 仍为 200")
    void shouldRejectMissingRoleDetail() throws Exception {
        JsonNode response = getJson(consoleToken(), "/api/console/roles/999999");

        // 业务结果一律 HTTP 200，由 code 表达；只有 401 与 403 用状态码
        assertThat(response.path("code").asInt()).isEqualTo(NOT_FOUND_CODE);
        assertThat(response.path("message").asText()).isEqualTo("角色不存在");
    }

    @Test
    @DisplayName("编辑：名称、备注与授权全量替换，详情回读一致，编码不可改")
    void shouldUpdateRoleAndReadBackDetail() throws Exception {
        String token = consoleToken();
        String code = "ROLE_EDIT";
        String roleId = createdRoleId(createRole(token, code, "客服", "初始备注", List.of(listPermissionId())));

        List<Long> submitted = List.of(roleMenuId(), listPermissionId(), createPermissionId());
        JsonNode updated = updateRole(token, roleId, "客服二线", "改了备注", submitted);

        assertThat(updated.path("code").asInt()).as("编辑失败：%s", updated).isZero();
        JsonNode detail = getJson(token, "/api/console/roles/" + roleId).path("data");
        // 编码不在请求体里，也不能被改：它是程序引用角色的标识
        assertThat(detail.path("code").asText()).isEqualTo(code);
        assertThat(detail.path("name").asText()).isEqualTo("客服二线");
        assertThat(detail.path("remark").asText()).isEqualTo("改了备注");
        assertThat(detail.path("permissionCount").asLong()).isEqualTo(submitted.size());
        assertThat(textsOf(detail.path("menuIds"))).containsExactlyElementsOf(sortedTexts(submitted));
        // 替换而不是追加：库里只剩这三条，建角色时勾的那一条必须已经消失
        assertThat(permissionIdsOfRole(roleId)).containsExactlyInAnyOrderElementsOf(submitted);
    }

    @Test
    @DisplayName("编辑：空数组清空全部授权")
    void shouldClearAllMenusWithEmptyArray() throws Exception {
        String token = consoleToken();
        String roleId = createdRoleId(createRole(token, "ROLE_EDIT_CLEAR", "待清空角色", null,
                List.of(listPermissionId(), createPermissionId())));

        assertThat(updateRole(token, roleId, "待清空角色", null, List.of()).path("code").asInt()).isZero();

        JsonNode detail = getJson(token, "/api/console/roles/" + roleId).path("data");
        assertThat(detail.path("permissionCount").asLong()).isZero();
        assertThat(textsOf(detail.path("menuIds"))).isEmpty();
        assertThat(permissionIdsOfRole(roleId)).isEmpty();
    }

    @Test
    @DisplayName("编辑：菜单不存在时整单拒绝，名称与授权都不变")
    void shouldRejectUnknownMenuOnUpdate() throws Exception {
        String token = consoleToken();
        String roleId = createdRoleId(createRole(token, "ROLE_EDIT_BAD_MENU", "原名称", null,
                List.of(listPermissionId())));

        JsonNode response = updateRole(token, roleId, "改名尝试", "改了备注", List.of(999_999L));

        assertThat(response.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(response.path("message").asText()).isEqualTo("指定的菜单或权限点不存在");
        // 整单回滚：改名与「先删后插」的删都不能留在库里
        JsonNode detail = getJson(token, "/api/console/roles/" + roleId).path("data");
        assertThat(detail.path("name").asText()).isEqualTo("原名称");
        assertThat(permissionIdsOfRole(roleId)).containsExactly(listPermissionId());
    }

    @Test
    @DisplayName("编辑：请求体里带 code 按未知字段拒绝，编码不变")
    void shouldRejectCodeInUpdatePayload() throws Exception {
        String token = consoleToken();
        String code = "ROLE_EDIT_CODE";
        String roleId = createdRoleId(createRole(token, code, "编码不可改", null, List.of()));

        JsonNode response = putJson(token, "/api/console/roles/" + roleId,
                objectMapper.writeValueAsString(Map.of(
                        "code", "ROLE_RENAMED",
                        "name", "试图改编码",
                        "menuIds", List.of())));

        // FAIL_ON_UNKNOWN_PROPERTIES：多传的字段被拒绝，而不是「收下但忽略」
        assertThat(response.path("code").asInt()).isEqualTo(BAD_REQUEST_CODE);
        assertThat(getJson(token, "/api/console/roles/" + roleId).path("data").path("code").asText())
                .as("编码是程序引用角色的标识，不能被改")
                .isEqualTo(code);
    }

    @Test
    @DisplayName("启停：停用后持有者下一次请求即 403，重复停用仍成功，再启用后恢复")
    void shouldRevokePermissionImmediatelyAfterDisable() throws Exception {
        String token = consoleToken();
        String roleId = createdRoleId(createRole(token, "ROLE_TOGGLE", "待停用角色", null,
                List.of(listPermissionId())));
        String holderLoginName = "role-toggle-holder";
        Long holderId = seedAccount(holderLoginName, "角色持有者", "role-toggle-holder@example.com");
        link(holderId, Long.parseLong(roleId));
        String holderToken = login(holderLoginName).path("data").path("token").asText();
        // 停用前：角色带来的权限点让这个账号可以访问角色列表
        assertThat(getJson(holderToken, "/api/console/roles?pageNo=1&pageSize=1").path("code").asInt())
                .as("停用前的权限应当已生效")
                .isZero();

        assertThat(changeRoleStatus(token, roleId, "DISABLED").path("code").asInt()).isZero();

        // 权限当次请求就失效：鉴权每次请求都按角色状态过滤，没有缓存要等过期，也不需要重新登录
        mockMvc.perform(get("/api/console/roles?pageNo=1&pageSize=1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + holderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        // 幂等：同一目标状态重复提交按成功处理
        assertThat(changeRoleStatus(token, roleId, "DISABLED").path("code").asInt()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sys_role WHERE id = ?",
                String.class, Long.parseLong(roleId))).isEqualTo("DISABLED");

        // 重新启用后同一个令牌恢复权限：停用改的是角色，不是会话
        assertThat(changeRoleStatus(token, roleId, "ACTIVE").path("code").asInt()).isZero();
        assertThat(getJson(holderToken, "/api/console/roles?pageNo=1&pageSize=1").path("code").asInt()).isZero();
    }

    @Test
    @DisplayName("删除：仍被用户占用时拒绝并提示人数，角色保留")
    void shouldRejectDeletingOccupiedRole() throws Exception {
        String token = consoleToken();
        String roleId = createdRoleId(createRole(token, "ROLE_OCCUPIED", "被占用角色", null, List.of()));
        Long holderId = seedAccount("role-occupied-holder", "占用用户", "role-occupied-holder@example.com");
        link(holderId, Long.parseLong(roleId));

        JsonNode response = deleteRole(token, roleId);

        // 409 段位而不是 40000：入参合法，被拒绝的是「这个角色还有人用」这个状态
        assertThat(response.path("code").asInt()).isEqualTo(CONFLICT_CODE);
        assertThat(response.path("message").asText()).isEqualTo("该角色仍被 1 个用户使用，请先解除分配");
        // 必须证明库里的角色还在：CASCADE 会静默带走关联记录，这条断言防的正是「删了也不报错」
        assertThat(countRole("ROLE_OCCUPIED")).isEqualTo(1);
        assertThat(deletedAtOf(roleId)).isNull();
    }

    @Test
    @DisplayName("删除：未占用角色软删除，列表与详情都查不到，编码仍被占用")
    void shouldSoftDeleteUnoccupiedRole() throws Exception {
        String token = consoleToken();
        String code = "ROLE_DELETED";
        String roleId = createdRoleId(createRole(token, code, "待删角色", null, List.of(listPermissionId())));

        assertThat(deleteRole(token, roleId).path("code").asInt()).isZero();

        // 列表与详情按 deleted_at 过滤：删完即查不到
        JsonNode page = getJson(token, "/api/console/roles?keyword=" + code).path("data");
        assertThat(page.path("total").asInt()).isZero();
        assertThat(getJson(token, "/api/console/roles/" + roleId).path("code").asInt())
                .isEqualTo(NOT_FOUND_CODE);

        // 软删除：行还在、deleted_at 有值；授权关联保留，供将来审计回溯
        assertThat(countRole(code)).isEqualTo(1);
        assertThat(deletedAtOf(roleId)).isNotNull();
        assertThat(permissionIdsOfRole(roleId)).as("软删除不动授权关联").containsExactly(listPermissionId());

        // 副作用：ux_sys_role_code 是全表唯一索引、不带 deleted_at 条件，编码仍被占着，
        // 重建必须给出能解释这一点的提示，否则管理员会陷入「明明没有这个角色，却说我重复」
        JsonNode recreated = createRole(token, code, "重建同名角色", null, List.of());
        assertThat(recreated.path("code").asInt()).isEqualTo(ROLE_CODE_TAKEN_CODE);
        assertThat(recreated.path("message").asText()).isEqualTo("角色编码已被占用（含已删除的角色）");
    }

    @Test
    @DisplayName("内置角色：编辑、停用、删除三者都被拒绝，库里状态不变")
    void shouldRejectAllModificationsOnBuiltinRole() throws Exception {
        String token = consoleToken();
        String superAdminId = Long.toString(jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE code = 'SUPER_ADMIN'", Long.class));

        JsonNode edited = updateRole(token, superAdminId, "改了超管名", null, List.of());
        assertThat(edited.path("code").asInt()).isEqualTo(CONFLICT_CODE);
        assertThat(edited.path("message").asText()).isEqualTo("内置角色不可编辑");

        JsonNode disabled = changeRoleStatus(token, superAdminId, "DISABLED");
        assertThat(disabled.path("code").asInt()).isEqualTo(CONFLICT_CODE);
        assertThat(disabled.path("message").asText()).isEqualTo("内置角色不可启停");

        JsonNode deleted = deleteRole(token, superAdminId);
        assertThat(deleted.path("code").asInt()).isEqualTo(CONFLICT_CODE);
        assertThat(deleted.path("message").asText()).isEqualTo("内置角色不可删除");

        // 三件事必须都没发生：超管角色一旦被停用或删除，系统里再没有账号能配置权限
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name, status, deleted_at FROM sys_role WHERE code = 'SUPER_ADMIN'");
        assertThat(row.get("name")).isEqualTo("超级管理员");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("deleted_at")).isNull();
    }

    @Test
    @DisplayName("无写权限的用户：编辑、启停、删除都返回 HTTP 403，角色不变")
    void shouldRejectModificationsWithoutPermission() throws Exception {
        String operatorToken = consoleToken();
        String roleId = createdRoleId(createRole(operatorToken, "ROLE_DENIED_WRITE", "无权限用例角色",
                null, List.of(listPermissionId())));
        String token = login(NO_PERMISSION_LOGIN_NAME).path("data").path("token").asText();

        mockMvc.perform(put("/api/console/roles/" + roleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateRolePayload("改名尝试", null, List.of()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));
        mockMvc.perform(put("/api/console/roles/" + roleId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChangeStatusPayload("DISABLED"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));
        mockMvc.perform(delete("/api/console/roles/" + roleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(FORBIDDEN_CODE));

        // 403 之外还要证明业务逻辑没有执行：只有「先拦后执行」才叫未授权接口不执行业务逻辑
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name, status, deleted_at FROM sys_role WHERE id = ?", Long.parseLong(roleId));
        assertThat(row.get("name")).isEqualTo("无权限用例角色");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("deleted_at")).isNull();
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

    private JsonNode createRole(String token, String code, String name, String remark, List<Long> menuIds)
            throws Exception {
        // 请求体里的标识按契约以字符串传：JS 拿 Long 会丢精度，后端两种写法都得收
        List<String> menuIdTexts = menuIds.stream().map(String::valueOf).toList();
        String body = objectMapper.writeValueAsString(new CreateRolePayload(code, name, remark, menuIdTexts));
        return postJson(token, "/api/console/roles", body);
    }

    private JsonNode updateRole(String token, String roleId, String name, String remark, List<Long> menuIds)
            throws Exception {
        // 与建角色同一口径：标识按契约以字符串传
        List<String> menuIdTexts = menuIds.stream().map(String::valueOf).toList();
        return putJson(token, "/api/console/roles/" + roleId,
                objectMapper.writeValueAsString(new UpdateRolePayload(name, remark, menuIdTexts)));
    }

    private JsonNode changeRoleStatus(String token, String roleId, String status) throws Exception {
        return putJson(token, "/api/console/roles/" + roleId + "/status",
                objectMapper.writeValueAsString(new ChangeStatusPayload(status)));
    }

    private JsonNode deleteRole(String token, String roleId) throws Exception {
        MvcResult result = mockMvc.perform(delete("/api/console/roles/" + roleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
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

    /** 取 {@code V026} 建立的角色管理菜单标识；取不到说明迁移没跑或没生效。 */
    private Long roleMenuId() {
        return jdbcTemplate.queryForObject("""
                SELECT id FROM sys_menu WHERE menu_type = 'C' AND path = '/system/roles'
                """, Long.class);
    }

    private Long listPermissionId() {
        return menuId("system:role:list");
    }

    private Long createPermissionId() {
        return menuId("system:role:create");
    }

    private Long menuId(String perms) {
        return jdbcTemplate.queryForObject("SELECT id FROM sys_menu WHERE perms = ?", Long.class, perms);
    }

    private int countRole(String code) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sys_role WHERE code = ?", Integer.class, code);
        return count == null ? 0 : count;
    }

    /** 取建角色响应里的标识；失败时直接失败，避免后续断言被空值带偏。 */
    private static String createdRoleId(JsonNode createdRole) {
        assertThat(createdRole.path("code").asInt()).as("建角色失败：%s", createdRole).isZero();
        return createdRole.path("data").path("id").asText();
    }

    /** 直接查库核对授权关系：接口层只能证明「改完之后详情回读一致」，证明不了库里真的换了那批。 */
    private List<Long> permissionIdsOfRole(String roleId) {
        return jdbcTemplate.queryForList(
                "SELECT menu_id FROM sys_role_menu WHERE role_id = ? ORDER BY menu_id",
                Long.class, Long.parseLong(roleId));
    }

    /** 取软删除标记：为 {@code null} 表示角色仍然有效。 */
    private Object deletedAtOf(String roleId) {
        return jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM sys_role WHERE id = ?", Object.class, Long.parseLong(roleId));
    }

    /** 详情里的标识按契约以字符串返回，断言前统一成字符串列表。 */
    private static List<String> textsOf(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    /** 详情按标识升序返回，因此期望值也排一次序。 */
    private static List<String> sortedTexts(List<Long> values) {
        return values.stream().sorted().map(String::valueOf).toList();
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
     * 建角色请求体的最小映射。
     *
     * <p>不复用生产侧的 {@code CreateRoleRequest}：用同一个类型序列化再反序列化，会掩盖
     * 字段名被改而测试仍然通过的情况——接口契约要按「HTTP 上的 JSON」来验证。
     * 这里也没有 {@code builtin} 字段：「带 builtin 的请求」用另一条用例手工拼 JSON 验证。
     *
     * @param code    角色编码
     * @param name    角色名称
     * @param remark  备注，可为 {@code null}
     * @param menuIds 勾选的菜单与权限点标识，按契约以字符串传
     */
    private record CreateRolePayload(String code, String name, String remark, List<String> menuIds) {
    }

    /**
     * 编辑角色请求体的最小映射，理由同上。
     *
     * <p>没有 {@code code} 字段：编码不可改，多传会被后端按未知字段拒绝。
     *
     * @param name    角色名称
     * @param remark  备注，可为 {@code null}
     * @param menuIds 替换后的菜单与权限点标识，按契约以字符串传
     */
    private record UpdateRolePayload(String name, String remark, List<String> menuIds) {
    }

    /**
     * 启停请求体的最小映射，理由同上。
     *
     * @param status 目标状态编码
     */
    private record ChangeStatusPayload(String status) {
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

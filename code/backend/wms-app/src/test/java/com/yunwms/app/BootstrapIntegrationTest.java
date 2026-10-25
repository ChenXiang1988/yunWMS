package com.yunwms.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 空库启动的端到端验证：迁移能落地、初始账号能登录、未改密时被拦住。
 *
 * <p>这三条是「系统能不能被第一个人用起来」的最小闭环，此前只能靠手工起服务加 curl 点一遍，
 * 既没进 CI，也无法在改动登录链路后立刻回查。这里把它固化成自动用例。
 *
 * <p>用例之间不共享状态：每个用例各自登录取令牌，不依赖执行顺序。
 */
@DisplayName("空库启动与初始账号")
class BootstrapIntegrationTest extends AbstractIntegrationTest {

    /** 迁移文件数，与 {@code src/main/resources/db/migration} 下的文件一一对应。 */
    private static final int MIGRATION_COUNT = 23;

    /** 业务表数：24 份建表语句产出的表，不含 Flyway 自己的历史表。 */
    private static final int BUSINESS_TABLE_COUNT = 24;

    /** 初始账号凭据，取自 {@code V023__seed_superadmin.sql}；写死是为了让迁移被改动时这里立刻失败。 */
    private static final String SEEDED_LOGIN_NAME = "superadmin";
    private static final String SEEDED_PASSWORD = "Admin@1234";

    /** 错误码来自 {@code ErrorCode#MUST_CHANGE_PASSWORD}。 */
    private static final int MUST_CHANGE_PASSWORD_CODE = 40104;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("迁移可跑通：23 份迁移全部成功，24 张业务表就绪")
    void shouldApplyAllMigrationsOnEmptyDatabase() {
        Integer migrations = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        // 只数成功记录：失败的迁移同样会在历史表里留一行，只要它不是 success，本用例必须报错而不是通过
        assertThat(migrations).isEqualTo(MIGRATION_COUNT);

        Integer tables = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """, Integer.class);
        assertThat(tables).isEqualTo(BUSINESS_TABLE_COUNT);
    }

    @Test
    @DisplayName("初始账号可登录：code=0 且 mustChangePassword=true")
    void shouldLoginWithSeededSuperadmin() throws Exception {
        JsonNode login = loginAsSuperadmin();

        assertThat(login.path("code").asInt()).isZero();
        assertThat(login.path("data").path("mustChangePassword").asBoolean())
                .as("初始密码只够换一次登录，登录响应必须要求先改密")
                .isTrue();
        assertThat(login.path("data").path("subjectType").asText()).isEqualTo("STAFF");
        // 未分配角色不影响登录：角色体系（2.2）落地前，初始化账号的角色是空数组
        assertThat(login.path("data").path("roles")).isEmpty();
        assertThat(login.path("data").path("token").asText()).isNotBlank();
    }

    @Test
    @DisplayName("待改密拦截：访问业务接口返回 HTTP 401 与业务码 40104")
    void shouldRejectProtectedEndpointWhilePasswordChangePending() throws Exception {
        String token = loginAsSuperadmin().path("data").path("token").asText();

        // /api/owners 是否已有实现不影响结论：拦截发生在 Servlet 过滤器里，早于路由匹配，
        // 未映射的路径同样会得到 40104 而不是 404
        mockMvc.perform(get("/api/owners").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(MUST_CHANGE_PASSWORD_CODE));
    }

    /**
     * 用初始账号登录，并把响应体解析成 JSON。
     *
     * @return 登录响应的完整 JSON，供各用例按需取字段
     * @throws Exception 请求构造或执行失败
     */
    private JsonNode loginAsSuperadmin() throws Exception {
        String body = objectMapper.writeValueAsString(
                new LoginPayload(SEEDED_LOGIN_NAME, SEEDED_PASSWORD));
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * 登录请求体的最小映射。
     *
     * <p>不复用 {@code LoginRequest}：那是入参校验用的类型，测试要验证的是「HTTP 上的 JSON 能否被解析」，
     * 用同一个 record 序列化后再反序列化，会掩盖字段名被改而测试仍然通过的情况。
     *
     * @param loginName 登录名
     * @param password  明文密码
     */
    private record LoginPayload(String loginName, String password) {
    }
}

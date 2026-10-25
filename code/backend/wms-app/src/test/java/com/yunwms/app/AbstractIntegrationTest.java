package com.yunwms.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成测试基类：完整应用上下文 + 一次性 PostgreSQL 容器。
 *
 * <p>为什么不用 H2：迁移里有表达式唯一索引（{@code lower(login_name)}）、部分唯一索引、
 * {@code timestamptz} 与 CHECK 约束，H2 对这些的语义与 PostgreSQL 不同。用内存库跑通只能证明
 * 「SQL 语法大致像」，不能证明空库在真实目标库上能起来——而「空库能不能起来」正是要验证的东西。
 *
 * <p>为什么数据库地址用 {@link DynamicPropertySource} 注入而不是写在测试的 yml 里：
 * 容器的端口每次启动都不同，端口只有在容器起来之后才知道；写死端口等于把测试绑到某次运行的偶然值上。
 * 同时这样也不影响 {@code src/main/resources/application.yml}，生产配置不需要为测试留口子。
 *
 * <p>为什么用 {@link MockMvc} 而不是启动真实端口：同一台机器上并行跑测试或本地起了服务时，
 * 固定端口必然抢不到；{@code MockMvc} 走完整的 Servlet 过滤链与 MVC 派发，但不绑定任何真实端口。
 * 鉴权过滤器注册的是 {@code /*} 模式，{@code MockMvc} 会按 Servlet 语义把它套到全部请求上，
 * 因此 {@code Sa-Token} 的上下文过滤器、登录校验与强制改密拦截在这里与运行时一致。
 *
 * <p>容器由 Testcontainers 的 Ryuk 在 JVM 退出时回收，测试自身不做任何数据清理：
 * 每个测试类拿到的是一个刚落地的空库，迁移在执行测试前跑完。
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractIntegrationTest {

    /**
     * 与生产同版本的 PostgreSQL。
     *
     * <p>库名对齐 {@code application.yml} 的默认库名，便于把测试里的 SQL 直接拿到本地库复现；
     * 用户名与密码用容器默认值，不复制生产凭据——测试里出现生产凭据是最容易被顺手抄走的一类泄露。
     */
    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("yunwms");

    /**
     * 把数据源指向本次测试的容器。
     *
     * <p>覆盖的是 {@code spring.datasource.*} 而不是 {@code DB_URL} 环境变量：前者优先级最高且只影响测试上下文，
     * 改动环境变量会连 {@code mvnw} 里并行执行的其他任务一起改掉。
     *
     * @param registry Spring 提供的动态属性注册表
     */
    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    /** 不绑定端口地发请求，用于验证过滤器与接口契约。 */
    @Autowired
    protected MockMvc mockMvc;

    /** 直接查库核对迁移结果；仓储层只暴露业务需要的查询，核对结构需要绕过它。 */
    @Autowired
    protected JdbcTemplate jdbcTemplate;
}

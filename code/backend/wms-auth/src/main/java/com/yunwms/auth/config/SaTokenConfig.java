package com.yunwms.auth.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.stp.StpUtil;
import com.yunwms.auth.domain.port.CurrentSession;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 认证入口：除放行清单外，所有请求都必须已登录；已登录但需先改密的，只放行改密接口。
 *
 * <p>权限校验（接口级权限点）属于后续任务，当前只做到「是否登录」与「是否必须先改密」这两层。
 *
 * <p>用过滤器而不是 MVC 拦截器：拦截器要等到请求命中处理器才执行，未映射的路径会先变成 404，
 * 拿不到统一的 401；过滤器在 Servlet 容器里先于 DispatcherServlet 执行，结果与路径是否存在无关。
 * 强制改密同理不放在 Controller 注解上：注解只能保护写了注解的接口，新加的业务接口漏写一个就绕过去了；
 * 放在这里默认拒绝，新增路径必须显式加入放行清单才能通过。
 */
@Configuration
public class SaTokenConfig {

    /**
     * 无需登录即可访问的路径。
     *
     * <p>登录接口本身必须放行，否则没人能拿到第一张令牌；它只做「凭据 → 令牌」的兑换，
     * 不返回任何业务数据，放行不会泄露信息。
     *
     * <p>{@code /v3/api-docs} 与 {@code /v3/api-docs/**} 都列出：前者是文档入口本身，后者是其下的分组，
     * 不依赖路径匹配器把末尾的 {@code /**} 当作零段处理。
     */
    private static final String[] PUBLIC_PATHS = {
            "/api/auth/login",
            "/actuator/health",
            "/actuator/info",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    /**
     * 鉴权过滤器在过滤器链中的序号。
     *
     * <p>必须晚于 Sa-Token 自己的上下文过滤器，否则 {@code StpUtil} 读不到当前请求。该过滤器在 1.46.0
     * 里的序号是 -104，这里取 0：只要大于它就成立，也不至于被排到请求上下文等后续过滤器之后。
     * 升级 Sa-Token 后若本过滤器报 {@code InvalidContextException} 而非 401，说明该序号变了，需一并调整。
     */
    private static final int AUTH_FILTER_ORDER = 0;

    @Bean
    public FilterRegistrationBean<SaServletFilter> saTokenAuthFilter(RestAuthResponses authResponses,
                                                                    CurrentSession currentSession) {
        SaServletFilter filter = new SaServletFilter()
                .addInclude("/**")
                .addExclude(PUBLIC_PATHS)
                .setAuth(obj -> {
                    StpUtil.checkLogin();
                    // 先确认登录再判改密：未登录的请求必须得到 40100「未登录或登录已失效」，
                    // 换成 40104 会让匿名请求以为自己在待改密状态里
                    MustChangePasswordGate.assertAllowed(
                            SaHolder.getRequest().getRequestPath(), currentSession.mustChangePassword());
                })
                .setError(authResponses::failureBody);
        FilterRegistrationBean<SaServletFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(AUTH_FILTER_ORDER);
        return registration;
    }
}

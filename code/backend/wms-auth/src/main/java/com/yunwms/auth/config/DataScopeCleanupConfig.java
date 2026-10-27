package com.yunwms.auth.config;

import com.yunwms.auth.application.service.DataScopeContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求结束时清理数据范围缓存。
 *
 * <p><b>为什么必须有一个显式的清理点：</b>缓存落在 {@link ThreadLocal} 上，而 Web 容器的线程是复用的。
 * 漏掉清理时，下一个复用该线程的请求会直接读到上一个用户的范围——请求之间不再有任何边界，
 * 而这种缺陷只在并发或连接复用的时序下出现，本地单线程点几下完全复现不出来。
 *
 * <p><b>为什么用过滤器而不是拦截器或 {@code @Scope("request")} 的销毁回调：</b>过滤器包住整个
 * Servlet 执行过程，{@code finally} 一定执行；拦截器要等到命中处理器才进入，未映射路径、
 * 鉴权失败与异常路径都可能不经过它，而恰恰是这些路径最容易漏掉清理。
 *
 * <p><b>为什么排在最先（{@link Ordered#HIGHEST_PRECEDENCE}）：</b>清理点必须是包在最外层的那一个，
 * 这样内层的任何过滤器、异常处理与参数解析都发生在它保护的范围之内；排在鉴权过滤器之后的话，
 * 鉴权失败提前返回时清理点根本没进入过。
 */
@Configuration
public class DataScopeCleanupConfig {

    /**
     * 注册清理过滤器。
     *
     * @param dataScopeContext 请求级数据范围缓存
     * @return 已注册的过滤器，路径模式与鉴权过滤器一致（{@code /*}）
     */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> dataScopeCleanupFilter(
            DataScopeContext dataScopeContext) {
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                try {
                    filterChain.doFilter(request, response);
                } finally {
                    // 无条件清理：异常路径同样会把线程交还给容器，残留值一样会被下一个请求读到
                    dataScopeContext.clear();
                }
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}

package com.yunwms.auth.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

/**
 * 接口级鉴权的入口：把 Sa-Token 的方法注解接到 MVC 上。
 *
 * <p>为什么过滤器之外还要一个拦截器：过滤器（见 {@link SaTokenConfig}）只能判「是否登录」，
 * 它执行时还没有路由结果，拿不到本次请求命中的方法，因此读不到方法上的权限点。
 * MVC 拦截器在处理器确定之后、调用处理器之前执行，这时 {@code HandlerMethod} 已经可读。
 *
 * <p>两层缺一不可：过滤器负责「默认拒绝」（新路径不加入放行清单就进不来），
 * 拦截器负责「按接口鉴权」。只留过滤器会让权限点无人消费，只留拦截器则漏写注解即放行。
 *
 * <p>这里不用 {@code @RestControllerAdvice} 接住鉴权异常：{@code RestAuthResponses} 已经是
 * 「鉴权异常 → 错误码 → HTTP 状态」的唯一映射，再写一份 advice 就有了第二份。
 * 直接用同一份映射把响应写出去，403 的形态与过滤器给出的完全一致。
 *
 * <p>失败模式值得记住：注解漏写等于放行，且不会有任何报错。因此由
 * {@code ControllerAuthorizationAnnotationTest} 强制每个接口方法都带注解，把漏写变成构建失败。
 */
@Configuration
public class SaTokenAnnotationConfig implements WebMvcConfigurer {

    private final RestAuthResponses authResponses;

    public SaTokenAnnotationConfig(RestAuthResponses authResponses) {
        this.authResponses = authResponses;
    }

    /**
     * 注册注解鉴权拦截器。
     *
     * <p>作用于全部路径：没有注解的接口不会被这个拦截器拦下（它没有可读的注解），
     * 真正的「默认拒绝」由过滤器的放行清单保证。两者作用域一致，才不会出现
     * 「过滤器放行了但没人鉴权」的路径。
     *
     * @param registry MVC 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new JsonFailureInterceptor(authResponses)).addPathPatterns("/**");
    }

    /**
     * 保留 Sa-Token 的注解处理，只把异常出口换成统一响应体。
     *
     * <p>默认的 {@link SaInterceptor} 会把鉴权异常原样抛给 MVC，落到 {@code GlobalExceptionHandler}
     * 的兜底分支变成 50000 与 HTTP 200——前端会以为请求成功，只是数据为空。
     * 这三种异常都在本类里就地翻译成 401 / 403，与过滤器走同一条路径。
     */
    private static final class JsonFailureInterceptor extends SaInterceptor {

        private final RestAuthResponses authResponses;

        private JsonFailureInterceptor(RestAuthResponses authResponses) {
            this.authResponses = authResponses;
        }

        /**
         * 执行注解鉴权；鉴权失败时直接写出响应并中断请求，不进入 Controller。
         *
         * @param request  当前请求
         * @param response 当前响应
         * @param handler  本次请求命中的处理器
         * @return 鉴权通过返回 {@code true}，被拒绝返回 {@code false}
         * @throws Exception 注解处理过程中抛出的非鉴权异常，原样上交
         */
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
            try {
                return super.preHandle(request, response, handler);
            } catch (NotLoginException | NotPermissionException | NotRoleException e) {
                writeFailure(response, e);
                return false;
            }
        }

        /**
         * 写出与过滤器一致的失败响应。
         *
         * <p>状态码与 Content-Type 由 {@link RestAuthResponses} 设置在同一个响应对象上，
         * 这里只补正文——重复设置会掩盖两处配置不一致的问题。
         */
        private void writeFailure(HttpServletResponse response, Exception failure) throws IOException {
            String body = authResponses.failureBody(failure);
            response.getWriter().write(body);
        }
    }
}

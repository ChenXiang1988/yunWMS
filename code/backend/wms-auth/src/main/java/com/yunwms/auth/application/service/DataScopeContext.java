package com.yunwms.auth.application.service;

import com.yunwms.auth.domain.model.DataScope;
import org.springframework.stereotype.Component;

/**
 * 请求级的数据范围缓存：同一请求内只解析一次。
 *
 * <p><b>为什么需要缓存：</b>一次请求里范围会被问多次（列表、详情、计数、将来的 SQL 改写），
 * 而每次解析最多要读四张表。不缓存时「看一个页面多十几次查询」会成为常态，且会随功能增加而恶化。
 *
 * <p><b>为什么用 {@link ThreadLocal} 而不是 Spring 的 request scope：</b>解析结果要能被非 Spring 的
 * 组件读到（例如 MyBatis 的语句改写器，它在 mapper 调用途中执行，拿不到注入的 request bean 之外
 * 更自然的方式）。{@link ThreadLocal} 的生命周期由 Web 过滤器兜住，不依赖代理对象与作用域解析。
 * 代价是必须清理——见 {@link #clear()}。
 *
 * <p><b>不跨请求缓存：</b>角色停用、角色移除、用户覆盖清除必须在下一个请求生效（规格要求「立即」）。
 * 跨请求缓存（Redis、Caffeine）会让「改了配置但界面没变」成为需要等待或重启才能解释的现象，
 * 因此这里的缓存严格限定在一次请求内。
 *
 * <p>清理由 {@code DataScopeCleanupConfig} 注册的过滤器在 {@code finally} 里调用：线程会被容器复用，
 * 漏掉清理就等于把一个用户的范围带给下一个复用到该线程的请求——那是越权方向的缺陷，且无法复现。
 */
@Component
public class DataScopeContext {

    private final DataScopeResolver dataScopeResolver;
    private final ThreadLocal<DataScope> cache = new ThreadLocal<>();

    public DataScopeContext(DataScopeResolver dataScopeResolver) {
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 取当前请求的生效数据范围，首次调用时才解析。
     *
     * @return 生效范围，恒不为 {@code null}；没有登录上下文时为 {@link DataScope.None}
     */
    public DataScope current() {
        DataScope cached = cache.get();
        if (cached != null) {
            return cached;
        }
        DataScope resolved = dataScopeResolver.resolve();
        cache.set(resolved);
        return resolved;
    }

    /**
     * 清理当前线程上的缓存。
     *
     * <p>必须在请求结束时无条件调用（含异常路径）：线程由容器复用，残留的值会被下一个请求读到，
     * 并且不会有任何报错。
     */
    public void clear() {
        cache.remove();
    }
}

package com.yunwms.app;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统计 MyBatis 执行了几条映射语句，用于把「这个查询不许有 N+1」变成一条构建期断言。
 *
 * <p>为什么需要它：多一条逐行查询不会有任何编译错误，只会在数据变多之后变慢到没人能解释。
 * 用眼睛数 SQL 是靠不住的，靠日志也只能在跑过之后回头看。
 *
 * <p>只拦 {@code Executor#query(MappedStatement, Object, RowBounds, ResultHandler)} 与
 * {@code Executor#update(MappedStatement, Object)}：这两处就是「一条映射语句一次」的位置。
 * 本项目没有懒加载与嵌套结果映射，所以一条映射语句对应一条 SQL。
 *
 * <p>必须声明 {@link Intercepts}：MyBatis 靠它判断插件该包住哪些方法，缺了会在创建会话时
 * 直接抛 {@code PluginException}，把整个上下文带坏。
 *
 * <p>挂载方式见使用方：运行期 {@code sqlSessionFactory.getConfiguration().addInterceptor(...)}，
 * 因为 Spring 上下文在测试类之间复用，用配置类注入反而要处理「同一个实例被挂两次」。
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
final class StatementCounter implements Interceptor {

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

    int count() {
        return count.get();
    }

    void reset() {
        count.set(0);
    }
}

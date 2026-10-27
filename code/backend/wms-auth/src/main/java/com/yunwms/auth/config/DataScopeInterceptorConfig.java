package com.yunwms.auth.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.yunwms.auth.application.service.DataScopeContext;
import com.yunwms.auth.infrastructure.datascope.DataScopePermissionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 装配数据权限插件：把当前请求的货主范围追加到已登记的查询语句上。
 *
 * <p><b>为什么插件在本模块装配，而它改写的是 {@code wms-master} 的语句：</b>范围解析
 * （{@link DataScopeContext}）属于认证与权限域，拦截器必须拿到它；MyBatis 的插件挂在全局
 * {@code SqlSessionFactory} 上，由启动模块收集容器里的 {@code Interceptor} 统一注册，
 * 因此装配点放在本模块即可对全仓生效，{@code wms-master} 不需要反向依赖本模块。
 *
 * <p><b>为什么只有这一个内层拦截器：</b>数据权限是唯一需要改写语句的能力；分页仍由 XML 里的
 * {@code LIMIT/OFFSET} 表达（{@code OwnerMapper.xml}），不引入分页插件是为了不改变既有语句形态——
 * 分页插件会重写 {@code count} 语句，而列表的计数与分页各自带条件（见 {@code OwnerScopedStatements}），
 * 交给它反而多一层要验证的改写。
 *
 * <p><b>注册的是「清单驱动」的 handler 而不是全表启用：</b>凡是未在清单里登记的语句一律原样放行，
 * 理由与清单本身见 {@code DataScopePermissionHandler} / {@code OwnerScopedStatements}。
 *
 * <p><b>启动期失败优先：</b>本 Bean 只是把 handler 接进插件，解析范围的失败发生在请求期
 * 并直接以 500 结束——不在这里做「解析失败就放行」的兜底，那个兜底等于关掉数据权限。
 */
@Configuration
public class DataScopeInterceptorConfig {

    /**
     * 注册数据权限插件。
     *
     * <p>返回类型必须是 {@link MybatisPlusInterceptor}：MyBatis-Plus 只把容器里的
     * {@code org.apache.ibatis.plugin.Interceptor} 装到 {@code SqlSessionFactory} 上，而
     * {@link DataPermissionInterceptor} 是内层拦截器，本身不是 MyBatis 插件，单独声明成 Bean 不会生效。
     *
     * <p><b>范围缓存取的是 {@link ObjectProvider} 而不是 Bean 本身：</b>本 Bean 是
     * {@code SqlSessionFactory} 的构造输入之一，而范围解析器要经过 Mapper 反过来依赖
     * {@code SqlSessionFactory}。直接注入会在启动期形成闭环并让容器起不来；传一个「用到时才取」的
     * 句柄，既保住「范围在请求期实时解析」这条语义，也不需要在任何一处写 {@code @Lazy} 代理。
     *
     * <p><b>取值函数的闭包放在这里，不放处理器里：</b>处理器只认领域类型 {@code DataScope}，
     * 因此持久化层不必反向依赖应用层的请求缓存实现；「范围从哪来」这条装配知识留在唯一的装配点。
     *
     * @param dataScopeContextProvider 范围缓存 Bean 的延迟句柄；只在改写已登记语句时取值
     * @return 已装配数据权限内层拦截器的 MyBatis-Plus 插件
     */
    @Bean
    public MybatisPlusInterceptor dataScopeMybatisInterceptor(
            ObjectProvider<DataScopeContext> dataScopeContextProvider) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(
                new DataScopePermissionHandler(() -> dataScopeContextProvider.getObject().current())));
        return interceptor;
    }
}

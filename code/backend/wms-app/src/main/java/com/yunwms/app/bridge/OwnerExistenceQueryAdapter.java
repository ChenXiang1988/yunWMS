package com.yunwms.app.bridge;

import com.yunwms.auth.domain.port.OwnerExistenceQuery;
import com.yunwms.master.application.query.OwnerExistenceLookup;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

/**
 * 把 {@code wms-master} 的货主可用性查询接到 {@code wms-auth} 的 {@link OwnerExistenceQuery} 上。
 *
 * <p><b>为什么这个类在 {@code wms-app}：</b>{@code owner} 表归 {@code wms-master}，而数据范围配置
 * 需要判断「这些货主还能不能被引用」，两个业务模块互不引用（{@code docs/coding-project.md} 第二节，
 * 由 {@code ModuleBoundaryTest} 与 {@code MapperTableAccessTest} 拦截）。只有 {@code wms-app} 的
 * 运行期 classpath 同时包含两边，与 {@link OwnerNameQueryAdapter} 同一路子。
 *
 * <p><b>为什么不复用 {@link OwnerNameQueryAdapter} 那条链路：</b>那边的口径刻意包含已软删除的货主
 * （账号列表仍要显示名称），而配置校验必须只认启用中的货主。两个口径走两个端口，用回一个必然错一边。
 *
 * <p>不做缓存、不做兜底：口径由对端 {@link OwnerExistenceLookup} 定义，本类只做翻译。
 */
@Component
public class OwnerExistenceQueryAdapter implements OwnerExistenceQuery {

    private final OwnerExistenceLookup ownerExistenceLookup;

    public OwnerExistenceQueryAdapter(OwnerExistenceLookup ownerExistenceLookup) {
        this.ownerExistenceLookup = ownerExistenceLookup;
    }

    /**
     * {@inheritDoc}
     *
     * <p>空集合的短路在两处都成立：端口契约要求调用方不传空集合，对端服务自身也会短路，
     * 因此任何一个调用方漏判都不会变成一次全表查询。
     */
    @Override
    public Set<Long> findExistingActiveIds(Collection<Long> ownerIds) {
        return ownerExistenceLookup.findExistingActiveIds(ownerIds);
    }
}

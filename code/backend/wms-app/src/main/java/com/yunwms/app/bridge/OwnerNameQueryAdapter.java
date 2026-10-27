package com.yunwms.app.bridge;

import com.yunwms.auth.domain.port.OwnerNameQuery;
import com.yunwms.master.application.query.OwnerNameLookup;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * 把 {@code wms-master} 的货主名称查询能力接到 {@code wms-auth} 的 {@link OwnerNameQuery} 上。
 *
 * <p><b>为什么这个类在 {@code wms-app}：</b>{@code owner} 表归 {@code wms-master}，而用户列表要显示
 * 「这个账号绑的是哪个货主」，两个业务模块互不引用（{@code docs/coding-project.md} 第二节，由
 * {@code ModuleBoundaryTest} 拦截）。只有 {@code wms-app} 的运行期 classpath 同时包含两边，
 * 与 {@link DictLookupAdapter} / {@link OwnerAccountCounterAdapter} 同一路子。
 *
 * <p><b>本类替换掉了 {@code wms-auth} 里直接读 {@code owner} 表的临时实现</b>：越界只读那处既有代码
 * 已经删除，表所有权清单里的例外也一并删除，因此 {@code MapperTableAccessTest} 在 auth 再次出现
 * {@code owner} 的 SQL 时会当场失败。
 *
 * <p>不做缓存、不做兜底：口径（包含已软删除的货主）由对端 {@link OwnerNameLookup} 定义，
 * 本类只做翻译，不重述也不改写结果。
 */
@Component
public class OwnerNameQueryAdapter implements OwnerNameQuery {

    private final OwnerNameLookup ownerNameLookup;

    public OwnerNameQueryAdapter(OwnerNameLookup ownerNameLookup) {
        this.ownerNameLookup = ownerNameLookup;
    }

    /**
     * {@inheritDoc}
     *
     * <p>空集合的短路在两处都成立：端口契约要求调用方不传空集合，对端服务自身也会短路，
     * 因此任何一个调用方漏判都不会变成一次全表查询。
     */
    @Override
    public Map<Long, String> findNamesByIds(Collection<Long> ownerIds) {
        return ownerNameLookup.findNamesByIds(ownerIds);
    }
}

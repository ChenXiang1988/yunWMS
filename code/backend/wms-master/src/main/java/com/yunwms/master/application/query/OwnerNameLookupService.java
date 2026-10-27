package com.yunwms.master.application.query;

import com.yunwms.master.domain.port.OwnerNameReader;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * {@link OwnerNameLookup} 的实现：按标识批量取货主名称。
 *
 * <p><b>为什么不加 {@code @Transactional(readOnly = true)}：</b>只有一次只读查询，没有需要原子提交的
 * 第二处写入，也没有「两次查询必须看到同一快照」的要求；加注解不会让这条查询更一致，只会多一层事务包装。
 *
 * <p><b>空集合在这里短路：</b>这是本用例唯一的判断。空 {@code IN} 在 SQL 里要么拼成非法的 {@code IN ()}，
 * 要么在拼装时被省略、退化成不带条件的全表查询——后者的错误对上层表现为「查到了全部货主」，
 * 比报错更难发现。调用方（列表、详情）确实可能拿到空集合（一页全是内部员工账号），
 * 因此这一步不能省，也不能推给持久化实现：那样每加一个实现都要重新记得一次。
 */
@Service
public class OwnerNameLookupService implements OwnerNameLookup {

    private final OwnerNameReader ownerNameReader;

    public OwnerNameLookupService(OwnerNameReader ownerNameReader) {
        this.ownerNameReader = ownerNameReader;
    }

    /**
     * {@inheritDoc}
     *
     * <p>重复标识不去重：SQL 的 {@code IN} 本身就按集合求值，去重只会多一次集合拷贝；
     * 结果映射按标识收口，重复键在持久化实现里已由主键唯一性保证不会出现。
     */
    @Override
    public Map<Long, String> findNamesByIds(Collection<Long> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return Map.of();
        }
        return ownerNameReader.findNamesByIds(ownerIds);
    }
}

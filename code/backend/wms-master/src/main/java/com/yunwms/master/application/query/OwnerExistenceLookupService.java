package com.yunwms.master.application.query;

import com.yunwms.master.domain.port.OwnerExistenceReader;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;

/**
 * {@link OwnerExistenceLookup} 的实现：把「哪些货主可用」的判定交给仓储，自己只做空集合短路。
 *
 * <p><b>为什么不加 {@code @Transactional(readOnly = true)}：</b>只有一次只读查询，没有需要原子提交的
 * 第二处写入，也没有「两次查询必须看到同一快照」的要求；加注解不会让这条查询更一致，只会多一层事务包装。
 *
 * <p><b>空集合短路放在这里而不是持久化实现：</b>那样每加一个实现都要重新记得一次，而忘掉的表现是
 * 「查到了全部货主」——一个会让校验通过的静默失败。
 */
@Service
public class OwnerExistenceLookupService implements OwnerExistenceLookup {

    private final OwnerExistenceReader ownerExistenceReader;

    public OwnerExistenceLookupService(OwnerExistenceReader ownerExistenceReader) {
        this.ownerExistenceReader = ownerExistenceReader;
    }

    /**
     * {@inheritDoc}
     *
     * <p>重复标识不去重：{@code IN} 本身按集合求值，去重只会多一次集合拷贝。
     */
    @Override
    public Set<Long> findExistingActiveIds(Collection<Long> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return Set.of();
        }
        return ownerExistenceReader.findExistingActiveIds(ownerIds);
    }
}

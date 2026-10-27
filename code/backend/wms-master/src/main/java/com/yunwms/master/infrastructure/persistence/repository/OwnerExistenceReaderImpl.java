package com.yunwms.master.infrastructure.persistence.repository;

import com.yunwms.master.domain.port.OwnerExistenceReader;
import com.yunwms.master.infrastructure.persistence.mapper.OwnerMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@link OwnerExistenceReader} 的 MyBatis 实现。
 *
 * <p>一次批量查询就是一次库往返，不做缓存：这条判定只在配置写入时执行一次，
 * 缓存只会引入「停用一个货主后多久才不能再被写进配置」这个说不清的问题。
 *
 * <p>结果收成 {@link LinkedHashSet}：调用方要拿它做差集找出「哪些标识不可用」，
 * 集合语义比列表更贴合用途，判重也由容器完成。
 */
@Repository
public class OwnerExistenceReaderImpl implements OwnerExistenceReader {

    private final OwnerMapper ownerMapper;

    public OwnerExistenceReaderImpl(OwnerMapper ownerMapper) {
        this.ownerMapper = ownerMapper;
    }

    @Override
    public Set<Long> findExistingActiveIds(Collection<Long> ownerIds) {
        return new LinkedHashSet<>(ownerMapper.selectExistingActiveIds(ownerIds));
    }
}

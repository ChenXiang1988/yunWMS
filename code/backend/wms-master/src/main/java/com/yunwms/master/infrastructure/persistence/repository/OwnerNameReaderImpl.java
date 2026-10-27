package com.yunwms.master.infrastructure.persistence.repository;

import com.yunwms.master.domain.port.OwnerNameReader;
import com.yunwms.master.infrastructure.persistence.entity.OwnerNameRow;
import com.yunwms.master.infrastructure.persistence.mapper.OwnerMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link OwnerNameReader} 的 MyBatis 实现。
 *
 * <p>放在 {@code infrastructure/persistence/repository} 而不是 {@code .service}：它读写的是库，
 * 名字里的 Repository 表示「持久化入口」，与用例无关（与 {@link OwnerSkuCounterImpl} 同一处理）。
 *
 * <p>不做缓存、不做兜底：一次批量查询就是一次库往返；缓存会引入「改了货主名称，多久之后
 * 别的页面才看得见」这个说不清的问题，而这条查询命中的是 {@code owner} 的主键。
 */
@Repository
public class OwnerNameReaderImpl implements OwnerNameReader {

    private final OwnerMapper ownerMapper;

    public OwnerNameReaderImpl(OwnerMapper ownerMapper) {
        this.ownerMapper = ownerMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>用 {@code toMap} 而不是 {@code groupingBy}：标识是主键，一个标识只对应一个名称，
     * 收集器把这条事实写进代码——真的出现重复键时会当场失败，而不是悄悄丢掉一条。
     */
    @Override
    public Map<Long, String> findNamesByIds(Collection<Long> ownerIds) {
        return ownerMapper.selectNamesByIds(ownerIds).stream()
                .collect(Collectors.toMap(OwnerNameRow::getId, OwnerNameRow::getName));
    }
}

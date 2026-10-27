package com.yunwms.master.infrastructure.persistence.repository;

import com.yunwms.master.domain.port.OwnerAssigneeReader;
import com.yunwms.master.infrastructure.persistence.mapper.OwnerAssigneeMapper;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@link OwnerAssigneeReader} 的 MyBatis 实现。
 *
 * <p>一次查询取回整批并收集成集合：调用方要做的是与角色范围求并集，
 * 集合语义比列表更贴合用途，重复标识也在这一步收敛。
 *
 * <p>不做缓存：请求级的缓存由调用方（{@code DataScopeContext}）负责，
 * 这里缓存会让「新接一个货主后多久生效」变成需要等过期才解释得清的问题。
 */
@Repository
public class OwnerAssigneeReaderImpl implements OwnerAssigneeReader {

    private final OwnerAssigneeMapper ownerAssigneeMapper;

    public OwnerAssigneeReaderImpl(OwnerAssigneeMapper ownerAssigneeMapper) {
        this.ownerAssigneeMapper = ownerAssigneeMapper;
    }

    @Override
    public Set<Long> findOwnerIdsByUserId(long userId) {
        return new LinkedHashSet<>(ownerAssigneeMapper.selectOwnerIdsByUserId(userId));
    }
}

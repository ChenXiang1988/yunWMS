package com.yunwms.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunwms.auth.domain.port.DataScopeOwnerRegistrar;
import com.yunwms.auth.infrastructure.persistence.entity.SysDataScopeOwnerEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysDataScopeOwnerMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;

/**
 * {@link DataScopeOwnerRegistrar} 的 MyBatis-Plus 实现。
 *
 * <p><b>删除是物理删除：</b>该表没有 {@code deleted_at} 列，也没有逻辑删除配置作用于它，
 * 因此这里用 {@code DELETE} 而不是更新标记列。留着旧行会让范围解析读到已被替换掉的货主，
 * 而那是越权方向的错误。
 *
 * <p>删除命中零行不当作失败：替换语义下「主体原本没有明细」是正常状态
 * （{@code docs/coding.md}「持久化与副作用」）。
 *
 * <p>插入逐行进行、`created_at` 交给库默认值：明细条数是管理员勾选出来的个位数到几十条，
 * 批量插入的收益抵不过「同一批行的时间来自两个来源」的代价。
 */
@Repository
public class DataScopeOwnerRegistrarImpl implements DataScopeOwnerRegistrar {

    private final SysDataScopeOwnerMapper sysDataScopeOwnerMapper;

    public DataScopeOwnerRegistrarImpl(SysDataScopeOwnerMapper sysDataScopeOwnerMapper) {
        this.sysDataScopeOwnerMapper = sysDataScopeOwnerMapper;
    }

    @Override
    public void replaceRoleOwners(long roleId, Collection<Long> ownerIds) {
        if (ownerIds == null) {
            // 需要清空时传空集合：把 null 当空集合会让「调用方漏传参数」悄悄变成「清空范围」
            throw new IllegalArgumentException("角色数据范围明细集合不能为 null，清空时请传空集合");
        }
        sysDataScopeOwnerMapper.delete(Wrappers.<SysDataScopeOwnerEntity>lambdaQuery()
                .eq(SysDataScopeOwnerEntity::getRoleId, roleId));
        for (Long ownerId : ownerIds) {
            SysDataScopeOwnerEntity row = new SysDataScopeOwnerEntity();
            row.setRoleId(roleId);
            row.setOwnerId(ownerId);
            sysDataScopeOwnerMapper.insert(row);
        }
    }

    @Override
    public void replaceUserOwners(long userId, Collection<Long> ownerIds) {
        if (ownerIds == null) {
            throw new IllegalArgumentException("用户数据范围明细集合不能为 null，清空时请传空集合");
        }
        sysDataScopeOwnerMapper.delete(Wrappers.<SysDataScopeOwnerEntity>lambdaQuery()
                .eq(SysDataScopeOwnerEntity::getUserId, userId));
        for (Long ownerId : ownerIds) {
            SysDataScopeOwnerEntity row = new SysDataScopeOwnerEntity();
            row.setUserId(userId);
            row.setOwnerId(ownerId);
            sysDataScopeOwnerMapper.insert(row);
        }
    }
}

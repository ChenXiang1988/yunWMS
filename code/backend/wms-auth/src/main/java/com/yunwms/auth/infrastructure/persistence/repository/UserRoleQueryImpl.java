package com.yunwms.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunwms.auth.domain.model.RoleStatus;
import com.yunwms.auth.domain.port.UserRoleQuery;
import com.yunwms.auth.infrastructure.persistence.entity.SysRoleEntity;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserRoleEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysRoleMapper;
import com.yunwms.auth.infrastructure.persistence.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link UserRoleQuery} 的 MyBatis-Plus 实现。
 *
 * <p>拆成两次单表查询而不是一次联表：两张表的条件都很简单，用 BaseMapper 就能表达，
 * 不必为此引入 XML。角色数量是个位数，多一次往返可以忽略。
 */
@Repository
public class UserRoleQueryImpl implements UserRoleQuery {

    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;

    public UserRoleQueryImpl(SysUserRoleMapper sysUserRoleMapper, SysRoleMapper sysRoleMapper) {
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMapper = sysRoleMapper;
    }

    @Override
    public List<String> findActiveRoleCodesByUserId(long userId) {
        List<Long> roleIds = sysUserRoleMapper.selectList(Wrappers.<SysUserRoleEntity>lambdaQuery()
                        .eq(SysUserRoleEntity::getUserId, userId))
                .stream()
                .map(SysUserRoleEntity::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            // 空的 IN 条件在拼装时会被省略，查询就退化成「查全部角色」，必须提前短路
            return List.of();
        }
        return sysRoleMapper.selectList(Wrappers.<SysRoleEntity>lambdaQuery()
                        .in(SysRoleEntity::getId, roleIds)
                        .eq(SysRoleEntity::getStatus, RoleStatus.ACTIVE.name())
                        .orderByAsc(SysRoleEntity::getSortNo))
                .stream()
                .map(SysRoleEntity::getCode)
                .toList();
    }
}

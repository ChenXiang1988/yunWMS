package com.yunwms.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunwms.auth.domain.model.RoleStatus;
import com.yunwms.auth.domain.port.UserPermissionQuery;
import com.yunwms.auth.infrastructure.persistence.entity.SysRoleEntity;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserRoleEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysMenuMapper;
import com.yunwms.auth.infrastructure.persistence.mapper.SysRoleMapper;
import com.yunwms.auth.infrastructure.persistence.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link UserPermissionQuery} 的 MyBatis-Plus 实现。
 *
 * <p>三次单表查询（用户角色 → 角色 → 权限点）而不是一条三表联查：角色数量是个位数，
 * 每次鉴权多一次往返换来的是「超管分支根本不查关联表」，而超管分支正是最热的那条。
 * 写成一条联查时，超管要与普通角色共用同一条 SQL，动态展开就得在 SQL 里做条件判断。
 *
 * <p>停用角色在第二条查询里被过滤掉，所以第三条查询拿到的一定是启用中的角色 id；
 * 这一点不能省：把过滤留到「查出来之后再挑」会让关联数据短暂地参与过授权判定。
 */
@Repository
public class UserPermissionQueryImpl implements UserPermissionQuery {

    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysMenuMapper sysMenuMapper;

    public UserPermissionQueryImpl(SysUserRoleMapper sysUserRoleMapper,
                                   SysRoleMapper sysRoleMapper,
                                   SysMenuMapper sysMenuMapper) {
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysMenuMapper = sysMenuMapper;
    }

    @Override
    public List<String> findActivePermissionCodesByUserId(long userId) {
        List<Long> grantedRoleIds = sysUserRoleMapper.selectList(Wrappers.<SysUserRoleEntity>lambdaQuery()
                        .eq(SysUserRoleEntity::getUserId, userId))
                .stream()
                .map(SysUserRoleEntity::getRoleId)
                .toList();
        if (grantedRoleIds.isEmpty()) {
            // 空的 IN 条件在拼装时会被省略，查询就退化成「查全部角色」，必须提前短路
            return List.of();
        }
        List<SysRoleEntity> activeRoles = sysRoleMapper.selectList(Wrappers.<SysRoleEntity>lambdaQuery()
                .in(SysRoleEntity::getId, grantedRoleIds)
                .eq(SysRoleEntity::getStatus, RoleStatus.ACTIVE.name()));
        if (activeRoles.isEmpty()) {
            return List.of();
        }
        if (activeRoles.stream().anyMatch(role -> Boolean.TRUE.equals(role.getBuiltin()))) {
            // 内置角色即超管：直接给全部权限点，且不读 sys_role_menu。
            // 判定只看 builtin 标记，不看 code：code 是给人读的标识，改名不该改变授权结果
            return sysMenuMapper.selectAllPermissionCodes();
        }
        return sysMenuMapper.selectPermissionCodesByRoleIds(
                activeRoles.stream().map(SysRoleEntity::getId).toList());
    }
}

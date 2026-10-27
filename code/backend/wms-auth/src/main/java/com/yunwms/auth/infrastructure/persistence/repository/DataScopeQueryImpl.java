package com.yunwms.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunwms.auth.domain.model.DataScopeSetting;
import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.model.RoleDataScope;
import com.yunwms.auth.domain.model.RoleStatus;
import com.yunwms.auth.domain.port.DataScopeQuery;
import com.yunwms.auth.infrastructure.persistence.entity.SysDataScopeOwnerEntity;
import com.yunwms.auth.infrastructure.persistence.entity.SysRoleEntity;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserEntity;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserRoleEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysDataScopeOwnerMapper;
import com.yunwms.auth.infrastructure.persistence.mapper.SysRoleMapper;
import com.yunwms.auth.infrastructure.persistence.mapper.SysUserMapper;
import com.yunwms.auth.infrastructure.persistence.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@link DataScopeQuery} 的 MyBatis-Plus 实现。
 *
 * <p>全部是单表等值或 {@code IN} 查询，用 {@code BaseMapper} 加 Lambda Wrapper 表达即可：
 * 没有多表聚合，也没有需要 {@code FOR UPDATE} 的语义。
 *
 * <p><b>查询条数与主体的角色数量无关</b>：取「用户有哪些角色」「这些角色什么范围」是两次查询，
 * {@code CUSTOM} 明细再合并成一次 {@code IN} 查询；逐角色查明细会让持有多角色的人每次请求
 * 多出 N 次往返，而那条退化不会有编译错误。
 *
 * <p>{@code selectById} 自带逻辑删除条件（{@code application.yml} 的
 * {@code logic-delete-field: deletedAt}），因此已删除的角色/账号按「没有配置」处理，
 * 与 {@code RoleQuery} / {@code UserQuery} 的口径一致。
 *
 * <p>类型编码用 {@link DataScopeType#fromCode} 还原，读到未知编码直接抛异常：静默回落会让
 * 一个本该「全部货主」的角色变成无权或相反，两个方向的代价不对称。
 */
@Repository
public class DataScopeQueryImpl implements DataScopeQuery {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysDataScopeOwnerMapper sysDataScopeOwnerMapper;

    public DataScopeQueryImpl(SysUserMapper sysUserMapper,
                              SysRoleMapper sysRoleMapper,
                              SysUserRoleMapper sysUserRoleMapper,
                              SysDataScopeOwnerMapper sysDataScopeOwnerMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysDataScopeOwnerMapper = sysDataScopeOwnerMapper;
    }

    @Override
    public Optional<DataScopeSetting> findUserSetting(long userId) {
        SysUserEntity user = sysUserMapper.selectById(userId);
        if (user == null || user.getDataScopeType() == null) {
            // 空类型就是「没有覆盖」：这里不能替它补一个默认类型，解析侧靠「有没有」分支
            return Optional.empty();
        }
        DataScopeType type = DataScopeType.fromCode(user.getDataScopeType());
        return Optional.of(new DataScopeSetting(type, ownerIdsOfUser(type, userId)));
    }

    @Override
    public Optional<DataScopeSetting> findRoleSetting(long roleId) {
        SysRoleEntity role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            return Optional.empty();
        }
        DataScopeType type = DataScopeType.fromCode(role.getDataScopeType());
        return Optional.of(new DataScopeSetting(type, ownerIdsOfRole(type, roleId)));
    }

    @Override
    public List<RoleDataScope> findActiveRoleScopes(long userId) {
        List<Long> roleIds = sysUserRoleMapper.selectList(Wrappers.<SysUserRoleEntity>lambdaQuery()
                        .eq(SysUserRoleEntity::getUserId, userId))
                .stream()
                .map(SysUserRoleEntity::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            // 空的 IN 条件会被省略，查询退化成「查全部角色」——一次无角色的用户会被算成拥有全部范围
            return List.of();
        }
        return sysRoleMapper.selectList(Wrappers.<SysRoleEntity>lambdaQuery()
                        .in(SysRoleEntity::getId, roleIds)
                        // 停用角色不计入并集（规格「角色停用后不再计入并集」），这条过滤只能落在查询上
                        .eq(SysRoleEntity::getStatus, RoleStatus.ACTIVE.name())
                        .orderByAsc(SysRoleEntity::getSortNo))
                .stream()
                .map(role -> new RoleDataScope(role.getId(), DataScopeType.fromCode(role.getDataScopeType())))
                .toList();
    }

    @Override
    public Map<Long, List<Long>> findCustomOwnerIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            // 空 IN 会被省略，退化成「查全部明细」，那等于把所有人的范围并到一起
            return Map.of();
        }
        Map<Long, List<Long>> ownerIdsByRole = new LinkedHashMap<>();
        for (SysDataScopeOwnerEntity row : sysDataScopeOwnerMapper.selectList(
                Wrappers.<SysDataScopeOwnerEntity>lambdaQuery()
                        .in(SysDataScopeOwnerEntity::getRoleId, roleIds)
                        .orderByAsc(SysDataScopeOwnerEntity::getOwnerId))) {
            ownerIdsByRole.computeIfAbsent(row.getRoleId(), key -> new ArrayList<>())
                    .add(row.getOwnerId());
        }
        return ownerIdsByRole;
    }

    @Override
    public List<Long> findUserOwnerIds(long userId) {
        return sysDataScopeOwnerMapper.selectList(Wrappers.<SysDataScopeOwnerEntity>lambdaQuery()
                        .eq(SysDataScopeOwnerEntity::getUserId, userId)
                        .orderByAsc(SysDataScopeOwnerEntity::getOwnerId))
                .stream()
                .map(SysDataScopeOwnerEntity::getOwnerId)
                .toList();
    }

    /**
     * 取角色配置的明细，仅 {@code CUSTOM} 才查库。
     *
     * @param type   数据范围类型
     * @param roleId 角色标识
     * @return 货主标识集合；非 {@code CUSTOM} 时为空集合且不发查询
     */
    private Set<Long> ownerIdsOfRole(DataScopeType type, long roleId) {
        if (!type.usesOwnerDetails()) {
            // ALL / ASSIGNED 是规则而不是名单，查一次只能是空结果，白白多一次往返
            return Set.of();
        }
        return new LinkedHashSet<>(sysDataScopeOwnerMapper.selectList(
                        Wrappers.<SysDataScopeOwnerEntity>lambdaQuery()
                                .eq(SysDataScopeOwnerEntity::getRoleId, roleId)
                                .orderByAsc(SysDataScopeOwnerEntity::getOwnerId))
                .stream()
                .map(SysDataScopeOwnerEntity::getOwnerId)
                .toList());
    }

    /**
     * 取用户覆盖配置的明细，仅 {@code CUSTOM} 才查库。
     *
     * @param type   数据范围类型
     * @param userId 用户标识
     * @return 货主标识集合；非 {@code CUSTOM} 时为空集合且不发查询
     */
    private Set<Long> ownerIdsOfUser(DataScopeType type, long userId) {
        if (!type.usesOwnerDetails()) {
            return Set.of();
        }
        return new LinkedHashSet<>(findUserOwnerIds(userId));
    }
}

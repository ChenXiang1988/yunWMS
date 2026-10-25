package com.yunwms.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunwms.auth.domain.model.RoleIdentity;
import com.yunwms.auth.domain.model.RoleStatus;
import com.yunwms.auth.domain.model.RoleSummary;
import com.yunwms.auth.domain.port.RoleQuery;
import com.yunwms.auth.infrastructure.persistence.entity.RoleSummaryRow;
import com.yunwms.auth.infrastructure.persistence.entity.SysRoleEntity;
import com.yunwms.auth.infrastructure.persistence.entity.SysRoleMenuEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysRoleMapper;
import com.yunwms.auth.infrastructure.persistence.mapper.SysRoleMenuMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link RoleQuery} 的 MyBatis-Plus 实现。
 *
 * <p>列表与详情各两次 SQL：一次取角色行（两个计数已在同一条语句里聚合，见
 * {@code SysRoleMapper.xml}），详情再取一次授权关联。没有逐行查询，所以列表页的 SQL 条数
 * 与角色数量无关。
 *
 * <p>状态列的中文说明不做在这里：{@code RoleStatus#fromCode} 遇到未知编码直接抛异常。
 * 这是刻意的——库里出现 {@code 'FOO'} 说明数据被绕过 CHECK 写坏，静默回落到「启用」
 * 会让一个本该停用的角色继续授权。
 */
@Repository
public class RoleQueryImpl implements RoleQuery {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

    public RoleQueryImpl(SysRoleMapper sysRoleMapper, SysRoleMenuMapper sysRoleMenuMapper) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
    }

    @Override
    public long count(String keyword, RoleStatus status) {
        return sysRoleMapper.countByFilter(keyword, status == null ? null : status.name());
    }

    @Override
    public List<RoleSummary> findPage(String keyword, RoleStatus status, int limit, long offset) {
        return sysRoleMapper.selectSummaryPage(keyword, status == null ? null : status.name(), limit, offset)
                .stream()
                .map(RoleQueryImpl::toSummary)
                .toList();
    }

    @Override
    public Optional<RoleSummary> findById(long roleId) {
        return Optional.ofNullable(sysRoleMapper.selectSummaryById(roleId))
                .map(RoleQueryImpl::toSummary);
    }

    @Override
    public List<RoleIdentity> findIdentities(Collection<Long> roleIds) {
        if (roleIds.isEmpty()) {
            // 空的 IN 条件在拼装时会被省略，查询会退化成「查全部角色」；
            // 这里必须提前短路，否则一次空请求会被当成「所有角色都合法」
            return List.of();
        }
        return sysRoleMapper.selectList(Wrappers.<SysRoleEntity>lambdaQuery()
                        .select(SysRoleEntity::getId, SysRoleEntity::getBuiltin)
                        .in(SysRoleEntity::getId, roleIds))
                .stream()
                .map(role -> new RoleIdentity(role.getId(), Boolean.TRUE.equals(role.getBuiltin())))
                .toList();
    }

    @Override
    public List<Long> findMenuIds(long roleId) {
        return sysRoleMenuMapper.selectList(Wrappers.<SysRoleMenuEntity>lambdaQuery()
                        .eq(SysRoleMenuEntity::getRoleId, roleId)
                        .orderByAsc(SysRoleMenuEntity::getMenuId))
                .stream()
                .map(SysRoleMenuEntity::getMenuId)
                .toList();
    }

    /**
     * 把行投影转成读模型。
     *
     * <p>计数用 {@code null} 兜 0：SQL 里已用 {@code coalesce} 兜过，但投影类型是包装类型，
     * 若将来有人改了 SQL 漏掉 coalesce，这里不会因为拆箱失败变成 500。
     *
     * @param row 数据库返回的行
     * @return 领域读模型
     * @throws IllegalArgumentException 库里的状态编码未知，说明数据已损坏
     */
    private static RoleSummary toSummary(RoleSummaryRow row) {
        return new RoleSummary(
                row.getId(),
                row.getCode(),
                row.getName(),
                Boolean.TRUE.equals(row.getBuiltin()),
                RoleStatus.fromCode(row.getStatus()),
                row.getUserCount() == null ? 0L : row.getUserCount(),
                row.getPermissionCount() == null ? 0L : row.getPermissionCount(),
                row.getRemark());
    }
}

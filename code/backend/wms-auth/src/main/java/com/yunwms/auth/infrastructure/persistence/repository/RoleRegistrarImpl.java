package com.yunwms.auth.infrastructure.persistence.repository;

import com.yunwms.auth.domain.model.NewRole;
import com.yunwms.auth.domain.model.RoleStatus;
import com.yunwms.auth.domain.port.RoleRegistrar;
import com.yunwms.auth.infrastructure.persistence.entity.SysDataScopeOwnerEntity;
import com.yunwms.auth.infrastructure.persistence.entity.SysRoleEntity;
import com.yunwms.auth.infrastructure.persistence.entity.SysRoleMenuEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysDataScopeOwnerMapper;
import com.yunwms.auth.infrastructure.persistence.mapper.SysRoleMapper;
import com.yunwms.auth.infrastructure.persistence.mapper.SysRoleMenuMapper;
import org.springframework.stereotype.Repository;

/**
 * {@link RoleRegistrar} 的 MyBatis-Plus 实现。
 *
 * <p>三张表都是单行插入，用 {@code BaseMapper#insert} 即可，不需要 XML。
 *
 * <p>{@code builtin} 与 {@code status} 在这里显式写死，不依赖列默认值：这两列的业务含义是
 * 「接口不能创建内置角色」「新建角色一律启用」，列默认值将来被改动时，建角色的语义不该跟着变。
 * {@code data_scope_type} 则取自入参——数据范围已是管理员的可选项，见 {@link NewRole}。
 *
 * <p>明细只写 {@code CUSTOM} 的：{@code ALL} 与 {@code ASSIGNED} 是规则而不是名单
 * （{@code sys_data_scope_owner} 的注释写明「ALL 与 ASSIGNED 不落本表」）。入参在
 * {@link NewRole} 的构造期已保证非 {@code CUSTOM} 时明细为空，这里不再判一次。
 *
 * <p>审计时间不在这里取：{@code created_at} / {@code updated_at} 在库内都有 {@code DEFAULT now()}，
 * 交给数据库可以保证几条 INSERT 的时间来源一致；应用侧再取一次时钟只会引入几个可能相差几毫秒的值。
 */
@Repository
public class RoleRegistrarImpl implements RoleRegistrar {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysDataScopeOwnerMapper sysDataScopeOwnerMapper;

    public RoleRegistrarImpl(SysRoleMapper sysRoleMapper,
                             SysRoleMenuMapper sysRoleMenuMapper,
                             SysDataScopeOwnerMapper sysDataScopeOwnerMapper) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysDataScopeOwnerMapper = sysDataScopeOwnerMapper;
    }

    @Override
    public long register(NewRole role, long operatorId) {
        SysRoleEntity entity = toEntity(role, operatorId);
        sysRoleMapper.insert(entity);
        Long roleId = entity.getId();
        if (roleId == null) {
            // 主键回填依赖 JDBC 的 generated keys；拿不到说明连接配置或驱动行为变了，
            // 此时继续写 sys_role_menu 会得到 roleId 为空的授权行，宁可当场失败
            throw new IllegalStateException("建角色后未取回角色标识，角色编码为：" + role.code());
        }
        for (Long menuId : role.menuIds()) {
            SysRoleMenuEntity row = new SysRoleMenuEntity();
            row.setRoleId(roleId);
            row.setMenuId(menuId);
            // created_at 交给库默认值，理由同上
            sysRoleMenuMapper.insert(row);
        }
        for (Long ownerId : role.ownerIds()) {
            SysDataScopeOwnerEntity row = new SysDataScopeOwnerEntity();
            row.setRoleId(roleId);
            row.setOwnerId(ownerId);
            sysDataScopeOwnerMapper.insert(row);
        }
        return roleId;
    }

    private static SysRoleEntity toEntity(NewRole role, long operatorId) {
        SysRoleEntity entity = new SysRoleEntity();
        entity.setCode(role.code());
        entity.setName(role.name());
        // 接口不能创建内置角色：builtin 恒为 false，见 NewRole 的说明
        entity.setBuiltin(Boolean.FALSE);
        // 枚举入库显式存 name()：库里该列是 varchar 且有 CHECK，存 ordinal 会在枚举顺序调整后静默错位
        entity.setDataScopeType(role.dataScopeType().name());
        entity.setStatus(RoleStatus.ACTIVE.name());
        entity.setRemark(role.remark());
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        return entity;
    }
}

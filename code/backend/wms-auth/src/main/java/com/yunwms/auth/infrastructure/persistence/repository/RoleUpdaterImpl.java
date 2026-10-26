package com.yunwms.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunwms.auth.domain.model.RoleStatus;
import com.yunwms.auth.domain.port.RoleUpdater;
import com.yunwms.auth.infrastructure.persistence.entity.SysRoleEntity;
import com.yunwms.auth.infrastructure.persistence.entity.SysRoleMenuEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysRoleMapper;
import com.yunwms.auth.infrastructure.persistence.mapper.SysRoleMenuMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;

/**
 * {@link RoleUpdater} 的 MyBatis-Plus 实现。
 *
 * <p>全部是单表写入，用 {@code BaseMapper} 与 Lambda Wrapper 表达即可，不需要 XML。
 *
 * <p>已软删除的角色不会被这里改到：{@code application.yml} 配置了全局逻辑删除
 * （{@code logic-delete-field: deletedAt}），MyBatis-Plus 会给自动注入的 UPDATE 补上
 * {@code deleted_at IS NULL} 条件。因此「角色在判定之后被并发删除」会表现为影响零行，
 * 由各方法转成 {@link IllegalStateException}，而不是静默写回一条已删除的角色。
 *
 * <p>审计时间取应用时钟而不交给库默认值：{@code updated_at} 的列默认值只在 INSERT 时生效，
 * 没有 ON UPDATE 触发器，不显式写的话它还是上一次修改的时间。
 */
@Repository
public class RoleUpdaterImpl implements RoleUpdater {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;

    public RoleUpdaterImpl(SysRoleMapper sysRoleMapper, SysRoleMenuMapper sysRoleMenuMapper) {
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
    }

    @Override
    public void updateWithMenus(long roleId, String name, String remark, Collection<Long> menuIds,
                                long operatorId, Instant updatedAt) {
        int updated = sysRoleMapper.update(null, Wrappers.<SysRoleEntity>lambdaUpdate()
                .eq(SysRoleEntity::getId, roleId)
                .set(SysRoleEntity::getName, name)
                // 显式传 null 表示清空备注：set 无条件拼进 SET 子句，因此「不填备注」与「保留旧备注」
                // 在 SQL 上不会退化成同一件事
                .set(SysRoleEntity::getRemark, remark)
                .set(SysRoleEntity::getUpdatedBy, operatorId)
                .set(SysRoleEntity::getUpdatedAt, updatedAt));
        if (updated == 0) {
            // 刚确认角色存在却写不回去：角色在本次操作过程中被删除，此时不能告诉调用方「改成功了」
            throw new IllegalStateException("编辑角色时没有任何一行被改动，角色已不存在：" + roleId);
        }

        // 替换语义：先删后插。删除命中零行是正常结果（角色原本没有任何授权），因此不看影响行数
        sysRoleMenuMapper.delete(Wrappers.<SysRoleMenuEntity>lambdaQuery()
                .eq(SysRoleMenuEntity::getRoleId, roleId));
        for (Long menuId : menuIds) {
            SysRoleMenuEntity row = new SysRoleMenuEntity();
            row.setRoleId(roleId);
            row.setMenuId(menuId);
            // created_at 交给库默认值：应用侧再取一次时钟只会在同一次操作里引入两个时间来源
            sysRoleMenuMapper.insert(row);
        }
    }

    @Override
    public void updateStatus(long roleId, RoleStatus status, long operatorId, Instant updatedAt) {
        // 枚举入库显式存 name()：库里该列是 varchar 且有 CHECK，存 ordinal 会在枚举顺序调整后静默错位
        int updated = sysRoleMapper.update(null, Wrappers.<SysRoleEntity>lambdaUpdate()
                .eq(SysRoleEntity::getId, roleId)
                .set(SysRoleEntity::getStatus, status.name())
                .set(SysRoleEntity::getUpdatedBy, operatorId)
                .set(SysRoleEntity::getUpdatedAt, updatedAt));
        if (updated == 0) {
            throw new IllegalStateException("启停角色时没有任何一行被改动，角色已不存在：" + roleId);
        }
    }

    @Override
    public void softDelete(long roleId, long operatorId, Instant deletedAt) {
        // 不用 deleteById：它会按全局逻辑删除写 deleted_at = now()，但不会记下 updated_by，
        // 而「谁删的」正是审计日志（2.8）要追溯的信息
        int updated = sysRoleMapper.update(null, Wrappers.<SysRoleEntity>lambdaUpdate()
                .eq(SysRoleEntity::getId, roleId)
                .set(SysRoleEntity::getDeletedAt, deletedAt)
                .set(SysRoleEntity::getUpdatedBy, operatorId)
                .set(SysRoleEntity::getUpdatedAt, deletedAt));
        if (updated == 0) {
            throw new IllegalStateException("删除角色时没有任何一行被改动，角色已不存在：" + roleId);
        }
    }
}

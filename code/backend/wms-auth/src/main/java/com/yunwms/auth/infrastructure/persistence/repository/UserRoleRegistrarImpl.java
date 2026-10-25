package com.yunwms.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunwms.auth.domain.port.UserRoleRegistrar;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserEntity;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserRoleEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysUserMapper;
import com.yunwms.auth.infrastructure.persistence.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;

/**
 * {@link UserRoleRegistrar} 的 MyBatis-Plus 实现。
 *
 * <p>先删后插两条路径都用 {@code BaseMapper} 表达，不需要 XML：删除是单表等值条件，
 * 插入是单行写入。{@code SysUserRoleEntity} 刻意没有 {@code @TableId}（复合主键），
 * 因此这里只用 Wrapper 删除、不用任何 {@code deleteById} 系列方法——那会删掉整片关联。
 *
 * <p>逐条插入而不是拼一条多值 {@code INSERT}：一个用户的角色是个位数，语句条数不是瓶颈，
 * 而 {@code BaseMapper} 没有多值插入，自己拼 SQL 反而要处理参数拼装与空集合两个分支。
 *
 * <p>同时更新 {@code sys_user} 的审计列：角色是这个账号的属性，改了它就该留下「谁改的」。
 * {@code updated_at} 显式取应用时钟——库内没有 ON UPDATE 触发器，不写的话它还是建号时间。
 */
@Repository
public class UserRoleRegistrarImpl implements UserRoleRegistrar {

    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysUserMapper sysUserMapper;

    public UserRoleRegistrarImpl(SysUserRoleMapper sysUserRoleMapper, SysUserMapper sysUserMapper) {
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public void replaceRoles(long userId, Collection<Long> roleIds, long operatorId) {
        // 删除命中零行是正常结果（账号原本没有任何角色），因此不看影响行数
        sysUserRoleMapper.delete(Wrappers.<SysUserRoleEntity>lambdaQuery()
                .eq(SysUserRoleEntity::getUserId, userId));

        for (Long roleId : roleIds) {
            SysUserRoleEntity row = new SysUserRoleEntity();
            row.setUserId(userId);
            row.setRoleId(roleId);
            // created_at 交给库默认值：应用侧再取一次时钟只会在同一次操作里引入两个时间来源
            sysUserRoleMapper.insert(row);
        }

        int updated = sysUserMapper.update(null, Wrappers.<SysUserEntity>lambdaUpdate()
                .eq(SysUserEntity::getId, userId)
                .set(SysUserEntity::getUpdatedBy, operatorId)
                .set(SysUserEntity::getUpdatedAt, Instant.now()));
        if (updated == 0) {
            // 刚确认账号存在却写不回去：账号在本次操作过程中被删除，此时不能告诉调用方「改成功了」
            throw new IllegalStateException("角色写入后审计列更新失败，用户已不存在：" + userId);
        }
    }
}

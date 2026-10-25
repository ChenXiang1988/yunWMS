package com.yunwms.auth.infrastructure.persistence.repository;

import com.yunwms.auth.domain.model.NewUserAccount;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.domain.port.UserAccountRegistrar;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserEntity;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserRoleEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysUserMapper;
import com.yunwms.auth.infrastructure.persistence.mapper.SysUserRoleMapper;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * {@link UserAccountRegistrar} 的 MyBatis-Plus 实现。
 *
 * <p>两张表都是单行插入，用 {@code BaseMapper#insert} 即可，不需要 XML。
 *
 * <p>审计时间与 {@code password_updated_at} 不在应用侧取值：这三列在库内都有 {@code DEFAULT now()}，
 * 交给数据库可以保证「同一条 INSERT 的创建时间与密码生效时间一致」；应用侧再取一次时钟只会引入
 * 两个可能相差几毫秒的来源，而这两个值将来会被用来判断「初始密码是不是刚建的」。
 *
 * <p>{@code must_change_password} 与 {@code status} 显式写入，不依赖列默认值：
 * 这两列的业务含义是「初始密码只够换一次登录」，默认值将来若被改动，建号行为不该跟着变。
 */
@Repository
public class UserAccountRegistrarImpl implements UserAccountRegistrar {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public UserAccountRegistrarImpl(SysUserMapper sysUserMapper, SysUserRoleMapper sysUserRoleMapper) {
        this.sysUserMapper = sysUserMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    @Override
    public long register(NewUserAccount account, List<Long> roleIds, long operatorId) {
        SysUserEntity entity = toEntity(account, operatorId);
        sysUserMapper.insert(entity);
        Long userId = entity.getId();
        if (userId == null) {
            // 主键回填依赖 JDBC 的 generated keys；拿不到说明连接配置或驱动行为变了，
            // 此时继续写 sys_user_role 会得到一个 userId 为空的关联，宁可当场失败
            throw new IllegalStateException("建号后未取回用户标识，登录名为：" + account.loginName());
        }
        // 角色分配是集合语义：同一 id 传两次不该变成主键冲突，去重后只写一行
        for (Long roleId : new LinkedHashSet<>(roleIds)) {
            SysUserRoleEntity row = new SysUserRoleEntity();
            row.setUserId(userId);
            row.setRoleId(roleId);
            // created_at 交给库默认值，理由同上
            sysUserRoleMapper.insert(row);
        }
        return userId;
    }

    private static SysUserEntity toEntity(NewUserAccount account, long operatorId) {
        SysUserEntity entity = new SysUserEntity();
        entity.setLoginName(account.loginName());
        entity.setDisplayName(account.displayName());
        // 枚举入库显式存 name()：库里该列是 varchar 且有 CHECK，存 ordinal 会在枚举顺序调整后静默错位
        entity.setSubjectType(account.subjectType().name());
        entity.setOwnerId(account.ownerId());
        entity.setEmail(account.email());
        entity.setWecomUserId(account.wecomUserId());
        entity.setPasswordHash(account.passwordHash());
        entity.setMustChangePassword(Boolean.TRUE);
        entity.setStatus(UserStatus.ACTIVE.name());
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        return entity;
    }
}

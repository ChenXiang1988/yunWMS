package com.yunwms.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.domain.port.UserAccountUpdater;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysUserMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * {@link UserAccountUpdater} 的 MyBatis-Plus 实现。
 *
 * <p>三个方法都是单表按主键更新，用 {@code BaseMapper#update} 加 Wrapper 即可，不需要 XML。
 *
 * <p>{@code updated_at} 由调用方传入而不是库内 {@code now()}：同一行上的
 * {@code password_updated_at} 与 {@code updated_at} 必须来自同一次操作，两个来源会相差几毫秒，
 * 而这两个值将来会被用来判断「这条记录是哪一次操作写的」。
 *
 * <p>影响行数为 0 一律抛 {@link IllegalStateException}：调用方此时刚确认过账号存在，
 * 命中零行只能是并发删除，此时告诉调用方「改成功了」会让管理员以为改动已生效。
 * 这不是靠行数判断并发冲突——状态更新本身是幂等的，命中一行与命中一行但值没变同样算成功。
 */
@Repository
public class UserAccountUpdaterImpl implements UserAccountUpdater {

    private final SysUserMapper sysUserMapper;

    public UserAccountUpdaterImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public void updateProfile(long userId, String displayName, String email, String wecomUserId,
                              long operatorId, Instant updatedAt) {
        // set 显式列出三列，不传实体：传实体会把「没填的字段」当成「不改」，于是清空邮箱
        // （货主账号的合法操作）永远写不进去——MyBatis-Plus 默认跳过 null 字段
        int updated = sysUserMapper.update(null, Wrappers.<SysUserEntity>lambdaUpdate()
                .eq(SysUserEntity::getId, userId)
                .set(SysUserEntity::getDisplayName, displayName)
                .set(SysUserEntity::getEmail, email)
                .set(SysUserEntity::getWecomUserId, wecomUserId)
                .set(SysUserEntity::getUpdatedBy, operatorId)
                .set(SysUserEntity::getUpdatedAt, updatedAt));
        if (updated == 0) {
            throw new IllegalStateException("基本信息写入失败，用户已不存在：" + userId);
        }
    }

    @Override
    public void updateStatus(long userId, UserStatus status, long operatorId, Instant updatedAt) {
        int updated = sysUserMapper.update(null, Wrappers.<SysUserEntity>lambdaUpdate()
                .eq(SysUserEntity::getId, userId)
                // 枚举入库显式存 name()：库里该列是 varchar 且有 CHECK，存 ordinal 会在枚举顺序调整后静默错位
                .set(SysUserEntity::getStatus, status.name())
                .set(SysUserEntity::getUpdatedBy, operatorId)
                .set(SysUserEntity::getUpdatedAt, updatedAt));
        if (updated == 0) {
            throw new IllegalStateException("账号状态写入失败，用户已不存在：" + userId);
        }
    }

    @Override
    public void resetPassword(long userId, String newPasswordHash, Instant changedAt, long operatorId) {
        int updated = sysUserMapper.update(null, Wrappers.<SysUserEntity>lambdaUpdate()
                .eq(SysUserEntity::getId, userId)
                .set(SysUserEntity::getPasswordHash, newPasswordHash)
                .set(SysUserEntity::getPasswordUpdatedAt, changedAt)
                .set(SysUserEntity::getMustChangePassword, Boolean.TRUE)
                .set(SysUserEntity::getUpdatedBy, operatorId)
                .set(SysUserEntity::getUpdatedAt, changedAt));
        if (updated == 0) {
            throw new IllegalStateException("密码重置失败，用户已不存在：" + userId);
        }
    }
}

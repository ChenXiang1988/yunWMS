package com.yunwms.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunwms.auth.domain.model.FailedAttemptState;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserAccount;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.domain.port.UserAccountRepository;
import com.yunwms.auth.infrastructure.persistence.entity.FailedAttemptRow;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysUserMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * {@link UserAccountRepository} 的 MyBatis-Plus 实现。
 */
@Repository
public class UserAccountRepositoryImpl implements UserAccountRepository {

    private final SysUserMapper sysUserMapper;

    public UserAccountRepositoryImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public Optional<UserAccount> findByLoginName(String loginName) {
        // 登录名唯一索引建在 lower(login_name) 上：按原值比较既用不到索引，也查不到大小写不同的同一账号
        return Optional.ofNullable(sysUserMapper.selectOne(Wrappers.<SysUserEntity>lambdaQuery()
                        .apply("lower(login_name) = {0}", loginName.toLowerCase(Locale.ROOT))))
                .map(UserAccountRepositoryImpl::toDomain);
    }

    @Override
    public Optional<UserAccount> findById(long userId) {
        // selectById 会带上逻辑删除条件：被软删除的账号必须和「不存在」同口径，否则它能凭旧令牌继续改密
        return Optional.ofNullable(sysUserMapper.selectById(userId))
                .map(UserAccountRepositoryImpl::toDomain);
    }

    @Override
    public void updatePassword(long userId, String newPasswordHash, Instant changedAt) {
        // 哈希与「需先改密」标记在同一条 UPDATE 里落库：拆成两条语句时，中间失败会留下
        // 「标记已清但密码没变」的状态，初始密码从此不再被拦。updated_at 一并写，改密属于账号变更。
        int updated = sysUserMapper.update(null, Wrappers.<SysUserEntity>lambdaUpdate()
                .eq(SysUserEntity::getId, userId)
                .set(SysUserEntity::getPasswordHash, newPasswordHash)
                .set(SysUserEntity::getPasswordUpdatedAt, changedAt)
                .set(SysUserEntity::getMustChangePassword, Boolean.FALSE)
                .set(SysUserEntity::getUpdatedAt, changedAt));
        if (updated == 0) {
            // 刚读到账号却写不回去：账号在改密过程中被删除，此时不能告诉调用方改密成功
            throw new IllegalStateException("密码写入失败，用户已不存在：" + userId);
        }
    }

    @Override
    public void clearExpiredLock(long userId, Instant now) {
        // 条件里重复判一次「已过期」：并发清理时后到的那次命中零行，是正常结果而不是失败，
        // 因此不看影响行数。判定放回数据库，避免应用拿着旧快照把别人的新锁定清掉。
        // last_failed_at 必须一起清：留着上一轮的残留时间，下一轮第一次失败会被判成窗口内。
        sysUserMapper.update(null, Wrappers.<SysUserEntity>lambdaUpdate()
                .eq(SysUserEntity::getId, userId)
                .isNotNull(SysUserEntity::getLockedUntil)
                .le(SysUserEntity::getLockedUntil, now)
                .set(SysUserEntity::getFailedAttempts, 0)
                .set(SysUserEntity::getLockedUntil, null)
                .set(SysUserEntity::getLastFailedAt, null));
    }

    @Override
    public void clearFailureState(long userId) {
        // 刻意不写 last_login_at：改密成功也是这一轮失败的终点，但它不是登录。
        // 这里不看影响行数：账号已在 updatePassword 里确认存在，命中零行只能是并发删除，属正常结果。
        sysUserMapper.update(null, Wrappers.<SysUserEntity>lambdaUpdate()
                .eq(SysUserEntity::getId, userId)
                .set(SysUserEntity::getFailedAttempts, 0)
                .set(SysUserEntity::getLockedUntil, null)
                .set(SysUserEntity::getLastFailedAt, null));
    }

    @Override
    public FailedAttemptState recordFailedAttempt(long userId, int lockAfterFailures,
                                                  Instant windowStart, Instant failedAt, Instant lockUntil) {
        FailedAttemptRow row = sysUserMapper.recordFailedAttempt(
                userId, lockAfterFailures, windowStart, failedAt, lockUntil);
        if (row == null) {
            // 刚读到账号却累加不出任何行：账号在登录过程中被物理删除，此时没有状态可更新
            throw new IllegalStateException("连续失败次数写入失败，用户已不存在：" + userId);
        }
        return new FailedAttemptState(row.getFailedAttempts(), row.getLockedUntil());
    }

    @Override
    public void recordSuccessfulLogin(long userId, Instant loginAt) {
        // last_failed_at 与计数一起清空：成功登录是这一轮的终点，窗口必须随之下一次失败重新开始。
        int updated = sysUserMapper.update(null, Wrappers.<SysUserEntity>lambdaUpdate()
                .eq(SysUserEntity::getId, userId)
                .set(SysUserEntity::getLastLoginAt, loginAt)
                .set(SysUserEntity::getFailedAttempts, 0)
                .set(SysUserEntity::getLockedUntil, null)
                .set(SysUserEntity::getLastFailedAt, null));
        if (updated == 0) {
            // 刚读到账号却写不回去：账号在登录过程中被删除，此时不能把令牌发出去
            throw new IllegalStateException("登录时间写入失败，用户已不存在：" + userId);
        }
    }

    private static UserAccount toDomain(SysUserEntity entity) {
        Boolean mustChangePassword = entity.getMustChangePassword();
        if (mustChangePassword == null) {
            // 列是 NOT NULL，为空只可能是映射漏了列。此时按 false 兜底会直接放过首次登录，
            // 而强制改密正是靠这一位生效的，宁可让登录链路当场失败。
            throw new IllegalStateException("sys_user.must_change_password 缺失，用户 id=" + entity.getId());
        }
        return new UserAccount(
                entity.getId(),
                entity.getLoginName(),
                entity.getDisplayName(),
                SubjectType.fromCode(entity.getSubjectType()),
                entity.getOwnerId(),
                entity.getPasswordHash(),
                mustChangePassword,
                UserStatus.fromCode(entity.getStatus()),
                entity.getLockedUntil());
    }
}

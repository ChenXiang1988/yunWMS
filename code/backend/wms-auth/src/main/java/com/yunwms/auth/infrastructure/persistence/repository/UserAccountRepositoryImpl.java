package com.yunwms.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserAccount;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.domain.port.UserAccountRepository;
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
    public void recordLoginTime(long userId, Instant loginAt) {
        int updated = sysUserMapper.update(null, Wrappers.<SysUserEntity>lambdaUpdate()
                .eq(SysUserEntity::getId, userId)
                .set(SysUserEntity::getLastLoginAt, loginAt));
        if (updated == 0) {
            // 刚读到账号却写不回去：账号在登录过程中被删除或停用，此时不能把令牌发出去
            throw new IllegalStateException("登录时间写入失败，用户已不存在：" + userId);
        }
    }

    private static UserAccount toDomain(SysUserEntity entity) {
        return new UserAccount(
                entity.getId(),
                entity.getLoginName(),
                entity.getDisplayName(),
                SubjectType.fromCode(entity.getSubjectType()),
                entity.getOwnerId(),
                entity.getPasswordHash(),
                UserStatus.fromCode(entity.getStatus()),
                entity.getLockedUntil());
    }
}

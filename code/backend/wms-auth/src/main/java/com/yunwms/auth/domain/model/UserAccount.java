package com.yunwms.auth.domain.model;

import java.time.Instant;

/**
 * 登录主体（聚合根）。
 *
 * <p>只承载判定「能不能登录」所需的事实，不含持久化注解、不依赖 Spring：登录规则可以被单测直接驱动，
 * 不需要数据库与容器。密码在这里只是哈希串，明文永远不进本对象。
 *
 * @param id           用户标识
 * @param loginName    登录名，员工由管理员指定，货主账号由系统生成
 * @param displayName  展示名，登录成功后回给前端
 * @param subjectType  主体类型，决定可访问端
 * @param ownerId      所属货主；内部员工必须为空，货主账号必须非空
 * @param passwordHash        密码哈希，算法由 {@code PasswordHasher} 的实现决定
 * @param mustChangePassword  是否需要先改密：初始密码建号或管理员重置后为 {@code true}，本人改密成功后转 {@code false}
 * @param status              账号状态
 * @param lockedUntil         锁定截止时间，为空表示未锁定
 */
public record UserAccount(
        long id,
        String loginName,
        String displayName,
        SubjectType subjectType,
        Long ownerId,
        String passwordHash,
        boolean mustChangePassword,
        UserStatus status,
        Instant lockedUntil) {

    /**
     * 与建表脚本的 {@code ck_user_subject} 保持同一口径。
     *
     * <p>库里已有的约束是最后一道防线，领域对象是第一道：脏数据若在登录链路上被放过，
     * 结果是「签了令牌但货主为空」，后续按货主过滤时会把整库数据暴露给货主账号。
     */
    public UserAccount {
        if (subjectType == SubjectType.OWNER && ownerId == null) {
            throw new IllegalArgumentException("货主账号必须绑定货主，用户 id=" + id);
        }
        if (subjectType == SubjectType.STAFF && ownerId != null) {
            throw new IllegalArgumentException("内部员工不得绑定货主，用户 id=" + id);
        }
    }

    /**
     * @return 账号是否已停用
     */
    public boolean isDisabled() {
        return status == UserStatus.DISABLED;
    }

    /**
     * 判断在给定时刻是否处于锁定期。
     *
     * <p>锁定是派生状态：只比较 {@code locked_until} 与当前时间，不另存标记位，
     * 否则「锁定已过期」与「锁定标记未清」会长期不一致。
     *
     * @param now 判定时刻
     * @return 截止时间晚于 {@code now} 时为 {@code true}
     */
    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}

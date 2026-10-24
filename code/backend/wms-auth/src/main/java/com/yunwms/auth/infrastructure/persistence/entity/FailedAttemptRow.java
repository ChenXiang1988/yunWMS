package com.yunwms.auth.infrastructure.persistence.entity;

import java.time.Instant;

/**
 * {@code UPDATE ... RETURNING} 的行投影。
 *
 * <p>与 {@link SysUserEntity} 分开，且不声明 {@code @TableName}：本类只承接一次语句返回的两列，
 * 不参与写入。用整行实体冒充投影会让人分不清「哪些字段是这次写操作的真值」。
 */
public class FailedAttemptRow {

    private Integer failedAttempts;
    private Instant lockedUntil;

    public Integer getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(Integer failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
}

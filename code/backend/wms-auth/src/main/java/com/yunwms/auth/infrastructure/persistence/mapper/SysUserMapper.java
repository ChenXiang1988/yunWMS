package com.yunwms.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunwms.auth.infrastructure.persistence.entity.FailedAttemptRow;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserEntity;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

/**
 * {@code sys_user} 的映射接口。
 *
 * <p>除失败累加外，登录需要的都是单表等值条件，用 {@code BaseMapper} 或 Wrapper 表达即可。
 * 失败累加走 XML：MyBatis-Plus 表达不了 {@code RETURNING}，也表达不了「先锁行再按窗口判定」；
 * 而在应用层先读再加一写回去，在并发下会丢计数，实测后果是实际允许的尝试次数多于阈值。
 */
public interface SysUserMapper extends BaseMapper<SysUserEntity> {

    /**
     * 原子累加一次连续失败次数；达到阈值时在同一条语句内写入锁定截止时间。
     *
     * <p>窗口判定与累加在同一条语句里完成：分两步做时，并发请求会各自按旧窗口判定、各自从 1 重新计数。
     *
     * @param userId            用户标识
     * @param lockAfterFailures 触发锁定的连续失败次数
     * @param windowStart       计数窗口起点，上次失败早于该时刻则本次从 1 重新计数
     * @param failedAt          本次失败时刻，写入 {@code last_failed_at} 作为下一次的窗口判定依据
     * @param lockUntil         达到阈值时写入的锁定截止时间
     * @return 累加后的失败次数与锁定截止时间；用户已被删除时为空
     */
    FailedAttemptRow recordFailedAttempt(@Param("userId") long userId,
                                         @Param("lockAfterFailures") int lockAfterFailures,
                                         @Param("windowStart") Instant windowStart,
                                         @Param("failedAt") Instant failedAt,
                                         @Param("lockUntil") Instant lockUntil);
}

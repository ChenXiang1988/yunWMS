package com.yunwms.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunwms.auth.infrastructure.persistence.entity.FailedAttemptRow;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserEntity;
import com.yunwms.auth.infrastructure.persistence.entity.UserSummaryRow;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * {@code sys_user} 的映射接口。
 *
 * <p>登录链路的条件都是单表等值，用 {@code BaseMapper} 或 Wrapper 表达即可。
 * 两处走 XML：失败累加要用 {@code RETURNING} 与「先锁行再按窗口判定」；
 * 管理端列表的分页查询要按三个可选条件拼 {@code WHERE}，Wrapper 拼出来的 SQL
 * 在「筛选项全空」与「部分为空」之间容易悄悄变成全表扫描，写在 XML 里能一眼看完。
 */
public interface SysUserMapper extends BaseMapper<SysUserEntity> {

    /**
     * 统计满足筛选条件的用户数。
     *
     * @param subjectType 主体类型编码；{@code null} 表示不筛选
     * @param status      账号状态编码；{@code null} 表示不筛选
     * @param keyword     按登录名或姓名模糊匹配的关键字；{@code null} 表示不筛选
     * @return 用户总数
     */
    long countByFilter(@Param("subjectType") String subjectType,
                       @Param("status") String status,
                       @Param("keyword") String keyword);

    /**
     * 按标识升序取一页用户。
     *
     * <p>只取展示需要的列：列表页要显示登录名、姓名、主体类型、货主、状态与最后登录，
     * 而 {@code password_hash}、{@code failed_attempts} 之类不该出现在一次「看列表」的查询里。
     *
     * @param subjectType 主体类型编码；{@code null} 表示不筛选
     * @param status      账号状态编码；{@code null} 表示不筛选
     * @param keyword     按登录名或姓名模糊匹配的关键字；{@code null} 表示不筛选
     * @param limit       本页最多返回多少行
     * @param offset      跳过多少行
     * @return 本页用户行；没有数据时为空列表
     */
    List<UserSummaryRow> selectSummaryPage(@Param("subjectType") String subjectType,
                                           @Param("status") String status,
                                           @Param("keyword") String keyword,
                                           @Param("limit") int limit,
                                           @Param("offset") long offset);

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

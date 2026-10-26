package com.yunwms.auth.infrastructure.persistence.mapper;

import com.yunwms.auth.infrastructure.persistence.entity.CodeRuleSequenceRow;
import org.apache.ibatis.annotations.Param;

/**
 * {@code sys_code_rule} 的取号映射。
 *
 * <p>只有一个方法，且刻意<b>不</b>提供「读当前值」「改当前值」：一旦存在一条能单独读到
 * {@code current_value} 的语句，就一定会有人写「读出来算一算再写回去」，而那正是
 * 并发重号的来源。取号的全部读写在 {@link #advance(String)} 一条语句里完成。
 *
 * <p>等到管理端要做「编码规则列表与编辑」（任务 4.3）时，再按那时的真实需要另开查询方法，
 * 不在这里预先铺开。
 */
public interface SysCodeRuleMapper {

    /**
     * 原子地把规则推进一个 {@code step}，返回本次分配到的流水号。
     *
     * <p>必须由数据库在同一条语句里完成「读旧值、加 step、返回新值」：拆成两次往返时，
     * 两个并发请求会读到同一个 {@code current_value}，各自算出同一个新值，
     * 于是两个对象拿到同一个编码。这与「领号」这类操作的老问题同源，
     * 而且它的后果不会当场报错——重复编码要到写唯一索引或贴出标签之后才被发现。
     *
     * @param ruleCode 规则编码
     * @return 本次分配结果（prefix、seq_length、递增后的 current_value）；规则不存在时返回 {@code null}
     */
    CodeRuleSequenceRow advance(@Param("ruleCode") String ruleCode);
}

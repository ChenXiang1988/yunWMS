package com.yunwms.auth.infrastructure.persistence.mapper;

import com.yunwms.auth.infrastructure.persistence.entity.SysAuditLogEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code sys_audit_log} 的映射接口。
 *
 * <p><b>刻意不继承 {@code BaseMapper}：</b>继承会白送 {@code updateById}、{@code deleteById} 之类的方法，
 * 而规格要求审计日志只追加不可修改。能力上不提供，比在每个写方法上补一道校验可靠——后者漏一处就
 * 是一条静默可删的审计日志。本接口因此只有一条 INSERT 与两条按对象检索的 SELECT。
 *
 * <p>三条语句都走 XML：{@code jsonb} 与 {@code inet} 列写入时必须显式 {@code CAST}（连接没有配
 * {@code stringtype=unspecified}，字符串参数会以 {@code varchar} 发送，直接赋给这两类列会报
 * 「column is of type jsonb but expression is of type character varying」），这是数据库特有语法，
 * Wrapper 表达不出来。
 */
public interface SysAuditLogMapper {

    /**
     * 追加一条日志。
     *
     * <p>返回 {@code void} 而不是影响行数：单条 INSERT 的成功判据是「执行无异常 + 事务提交」，
     * 行数拿来做判断只会引出「0 行是不是失败」这种没有答案的问题（见 {@code docs/coding.md}）。
     * 标识与创建时间交给数据库：{@code id} 是 {@code GENERATED ALWAYS AS IDENTITY}，
     * {@code created_at} 有 {@code DEFAULT now()}，两者都由库生成，写入侧的时钟不参与。
     *
     * @param row 日志行，字段与列一一对应
     */
    void append(SysAuditLogEntity row);

    /**
     * 统计某个对象的日志总数。
     *
     * <p>与 {@link #selectPageByObject} 分开成两次查询而不是用窗口函数一次取回：结果为空页时
     * 窗口函数不会返回任何行，总数就没地方取，仍然要补一次计数。
     *
     * @param bizType 审计对象类型编码
     * @param bizId   对象标识
     * @return 该对象的日志条数
     */
    long countByObject(@Param("bizType") String bizType, @Param("bizId") String bizId);

    /**
     * 按操作时间倒序取某个对象的一页日志。
     *
     * @param bizType 审计对象类型编码
     * @param bizId   对象标识
     * @param limit   本页最多返回多少行
     * @param offset  跳过多少行
     * @return 本页日志行；没有数据时为空列表
     */
    List<SysAuditLogEntity> selectPageByObject(@Param("bizType") String bizType,
                                               @Param("bizId") String bizId,
                                               @Param("limit") int limit,
                                               @Param("offset") long offset);
}

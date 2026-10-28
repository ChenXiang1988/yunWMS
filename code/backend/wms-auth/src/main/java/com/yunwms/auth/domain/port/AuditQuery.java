package com.yunwms.auth.domain.port;

import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditPage;

/**
 * 审计日志的读取入口：按对象追溯。
 *
 * <p>只有「按对象检索」这一种读法：规格的场景是「按 SKUID 检索 → 返回该对象全部变更记录，按时间倒序」。
 * 按操作人、按时间范围这类检索是界面上的扩展（4.3），有真实消费者时再加——现在加上去只会得到
 * 几条没人调用、也没人验证过索引的语句。
 *
 * <p>分页由调用方给出 {@code pageNo} / {@code pageSize}，而不是让本端口返回 MyBatis-Plus 的
 * {@code IPage}：领域端口不认识持久化框架的分页对象。
 */
public interface AuditQuery {

    /**
     * 按对象检索，按操作时间倒序。
     *
     * <p>{@code bizType} 与 {@code bizId} 都必须给出：缺任一个就退化成「查全表」，
     * 而审计表只会越来越大，一次全表分页会把库拖垮。
     *
     * @param bizType  审计对象类型
     * @param bizId    对象标识
     * @param pageNo   页码，从 1 开始，调用方保证不小于 1
     * @param pageSize 每页条数，调用方保证大于 0
     * @return 该对象的一页日志及总数，按操作时间倒序
     * @throws IllegalArgumentException 库里的对象类型、动作或来源端编码未知，说明数据已损坏
     */
    AuditPage findByObject(AuditBizType bizType, String bizId, int pageNo, int pageSize);
}

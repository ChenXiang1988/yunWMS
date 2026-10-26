package com.yunwms.master.application.dto;

import com.yunwms.master.domain.model.OwnerStatus;

import java.time.Instant;
import java.util.List;

/**
 * 货主列表的一页。
 *
 * <p>把 {@code pageNo} / {@code pageSize} 一并回带，是为了让前端能直接用响应里的分页信息渲染
 * 页码控件：只回 {@code records} 与 {@code total} 时，前端得自己记住请求时填的是第几页，
 * 而分页请求与响应之间是会隔着一次用户操作的。
 *
 * <p>本类型停留在应用层，不直接序列化：标识转字符串、状态转编码属于接口层约定，由响应体的装配器负责。
 *
 * <p><b>行类型为什么嵌在这里而不是单独一个顶层类：</b>它与本页是同一次查询的产物，只有本类型和
 * {@link OwnerDetail} 两个消费者。单独摊成顶层类会让「列表行有哪些字段」与「列表分页外壳」分开两处读，
 * 而它们的口径必须一致——详情页头部展示的正是这一行的内容。
 *
 * @param pageNo   页码，从 1 开始
 * @param pageSize 每页条数
 * @param total    满足筛选条件的总数，不是本页条数
 * @param records  本页货主；没有数据时为空列表
 */
public record OwnerPage(int pageNo, int pageSize, long total, List<Summary> records) {

    /**
     * 列表的一行，也是详情页头部展示的那份摘要。
     *
     * <p><b>刻意没有「主负责人」与「SKU 数」两个字段：</b>界面上的那两列分别属于任务 3.5（联系人）
     * 与商品模块，本批没有任何数据来源。造一个恒为 {@code null} 的字段或恒为 0 的计数，前端会以为
     * 「这列存在但暂时没数据」；字段不出现，才准确表达「这个能力还没有」。
     *
     * @param id        货主标识
     * @param code      系统编码，形如 {@code OWNER0001}
     * @param name      客户名称
     * @param ownerType 客户类型，取 {@code OWNER_TYPE} 字典的 value
     * @param status    货主状态；保留枚举而不是字符串，转编码是接口层的事
     * @param updatedAt 最后修改时刻，取自库内 UTC 时间
     */
    public record Summary(
            long id,
            String code,
            String name,
            String ownerType,
            OwnerStatus status,
            Instant updatedAt) {
    }
}

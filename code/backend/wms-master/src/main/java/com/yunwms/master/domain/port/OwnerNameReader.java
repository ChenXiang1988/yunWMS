package com.yunwms.master.domain.port;

import java.util.Collection;
import java.util.Map;

/**
 * 按标识批量读取货主名称，供展示类查询使用。
 *
 * <p><b>口径：不按 {@code deleted_at} 过滤。</b>已软删除的货主可能仍被别的模块引用（例如
 * {@code wms-auth} 的账号绑定着它），那些页面必须显示得出名称。若这里按「未删除」过滤，
 * 「绑了一个已删除的货主」与「没绑货主」在界面上会长得一样，而这两种情况该做的处理完全不同。
 *
 * <p><b>为什么单独成一个端口而不塞进 {@link OwnerRepository}：</b>那个端口读写的是 {@code Owner}
 * 聚合，方法签名围绕聚合与它的写规则展开；本端口只回答「这几个标识分别叫什么」，既不重建聚合，
 * 也不受聚合不变量约束。混进去之后，「一次查询到底碰了哪些列、带不带软删除条件」这件事就得靠
 * 读完整个接口才能回答，而这两点正是本端口存在的理由。与 {@link OwnerSkuCounter} 同一路子：
 * 窄口径的只读能力单独成口，实现留在 {@code infrastructure/persistence}。
 */
public interface OwnerNameReader {

    /**
     * 按标识批量取货主名称。
     *
     * <p>一次查询取回整批，不逐条往返：调用方（用户列表、详情）拿到的是一整页标识，
     * 逐个查会让 SQL 条数随页内行数增长。
     *
     * <p>不按 {@code deleted_at} 过滤，理由见类注释。
     *
     * @param ownerIds 货主标识集合；调用方保证非空——空集合在 SQL 里会拼出 {@code IN ()}，
     *                 而省略条件的 {@code IN} 会退化成全表扫描，因此由用例层短路，本方法不再兜底
     * @return 货主标识到名称的映射；查不到的标识不出现在结果里
     */
    Map<Long, String> findNamesByIds(Collection<Long> ownerIds);
}

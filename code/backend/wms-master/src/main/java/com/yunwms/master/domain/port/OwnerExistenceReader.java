package com.yunwms.master.domain.port;

import java.util.Collection;
import java.util.Set;

/**
 * 按标识批量判断货主是否<b>可用</b>（存在、未删除且状态为 {@code ACTIVE}）。
 *
 * <p><b>口径与 {@link OwnerNameReader} 相反，这是刻意的：</b>那个端口要包含已软删除的货主
 * （账号列表仍要显示「绑了哪个已删除的货主」），而本端口回答「这些货主还能不能被写进配置」。
 * 两者合并成一个端口，必然有一处用错口径，而用错的后果分别是「显示不出名称」与「配置指向一个
 * 看不见的货主」，后者更难发现。
 *
 * <p><b>为什么单独成端口而不塞进 {@link OwnerRepository}：</b>那个端口围绕 {@code Owner} 聚合的
 * 读写与写规则展开；本端口只回答一批标识里哪些可用，既不重建聚合，也不受聚合不变量约束。
 * 与 {@link OwnerSkuCounter} 同一路子：窄口径的只读能力单独成口。
 */
public interface OwnerExistenceReader {

    /**
     * 从给定标识中筛出可用的货主。
     *
     * <p>一次 {@code IN} 查询取回整批，不逐条往返：调用方拿到的是管理员勾选的一整组货主。
     *
     * @param ownerIds 货主标识集合；调用方保证非空——空集合在 SQL 里会拼出非法的 {@code IN ()}
     *                 或退化成全表扫描，因此由用例层短路，本方法不再兜底
     * @return 可用的货主标识；一个都不可用时为空集合
     */
    Set<Long> findExistingActiveIds(Collection<Long> ownerIds);
}

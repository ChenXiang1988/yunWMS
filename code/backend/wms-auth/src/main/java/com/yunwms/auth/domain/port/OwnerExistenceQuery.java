package com.yunwms.auth.domain.port;

import java.util.Collection;
import java.util.Set;

/**
 * 货主存在性查询（只回答「存在、未删除且启用」的那一批）。
 *
 * <p><b>口径只此一份：</b>数据范围配置里写进去的货主必须是启用中的货主。停用或已删除的货主
 * 让配置指向一个看不见的对象：管理员在界面上选不到它，事后也无法判断这条配置为什么不起作用。
 *
 * <p><b>为什么不复用 {@link OwnerNameQuery}：</b>那个端口刻意包含已软删除的货主——账号列表要显示
 * 「绑了一个已删除的货主」。把它用在这里，等于把「已删除」当成可用，而两处的口径本就应该相反。
 *
 * <p><b>为什么是一个端口而不是在 auth 里直接查 {@code owner}：</b>{@code owner} 表归
 * {@code wms-master}，跨模块直读会绕过对方的业务规则，且对方改列名、拆表时没有任何编译期信号。
 * 本端口由 {@code wms-app} 的适配器接到 {@code wms-master} 的 Application 能力上。
 */
public interface OwnerExistenceQuery {

    /**
     * 从给定标识中筛出存在、未删除且启用中的货主。
     *
     * <p>返回集合而不是映射或列表：调用方只关心「哪些能用」，结果是入参的子集，重复标识自然收敛。
     *
     * @param ownerIds 待判定的货主标识集合，可为 {@code null}（按空集合处理）
     * @return 可用货主标识；入参为空或一个都不可用时返回空集合
     */
    Set<Long> findExistingActiveIds(Collection<Long> ownerIds);
}

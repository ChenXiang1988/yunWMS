package com.yunwms.auth.domain.port;

import java.util.Collection;
import java.util.Map;

/**
 * 按标识批量取货主名称。
 *
 * <p><b>这是一处跨模块读取，但不再是越界读取。</b>{@code owner} 表属于 {@code wms-master}，
 * 用户列表却需要显示「这个账号绑的是哪个货主」。本模块只声明需要什么：实现由 {@code wms-app} 的
 * {@code OwnerNameQueryAdapter} 装配，委托 {@code wms-master} 的货主名称查询能力
 * （{@code docs/coding-project.md} 第二节：跨模块取数由 {@code wms-app} 装配，或依赖能力提供方的
 * Application 接口）。本模块因此不再有任何一条读 {@code owner} 表的 SQL。
 *
 * <p>为什么把跨模块读取收敛到一个端口，而不是在列表 SQL 里 join {@code owner}：
 * 写进 SQL 的越界没有边界，往后每一处需要货主名称的查询都会照着抄一遍，实现方改名、拆表、
 * 调整软删除口径时也不会有任何编译期信号。收在一个端口后面，替换时只改装配。
 *
 * <p>本端口的签名是按「能力提供方能满足的形状」设计的：一次给一批标识、拿回映射，
 * 调用方不必知道货主有几条。
 */
public interface OwnerNameQuery {

    /**
     * 按标识批量取货主名称。
     *
     * <p>不按 {@code deleted_at} 过滤：账号绑的货主被逻辑删除时，列表仍要显示出名称，
     * 否则「绑了一个已删除的货主」与「没绑货主」在界面上长得一样，而这两种情况该做的处理完全不同。
     *
     * @param ownerIds 货主标识集合；调用方保证非空（空集合在调用前短路，避免退化成查全表）
     * @return 货主标识到名称的映射；查不到的标识不出现在结果里
     */
    Map<Long, String> findNamesByIds(Collection<Long> ownerIds);
}

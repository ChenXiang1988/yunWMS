package com.yunwms.auth.domain.port;

import java.util.Collection;
import java.util.Map;

/**
 * 按标识批量取货主名称。
 *
 * <p><b>这是一处有意的跨模块读取。</b>{@code owner} 表属于 {@code wms-master}，用户列表却需要显示
 * 「这个账号绑的是哪个货主」。{@code docs/coding-project.md} 要求跨模块取数由 {@code wms-app} 装配、
 * 或由能力提供方暴露 Application 接口，而 {@code wms-master} 目前还没有货主查询能力（模块尚未落地），
 * 因此本批的实现在本模块的 infrastructure 里<b>直接读 {@code owner} 表</b>。
 *
 * <p>为什么把越界收敛到一个端口，而不是在列表 SQL 里 join {@code owner}：
 * 写进 SQL 的越界没有边界，往后每一处需要货主名称的查询都会照着抄一遍，等到 {@code wms-master}
 * 具备查询能力时没人找得到全部副本。收在一个实现类里，替换时只改那一个类。
 *
 * <p><b>等 {@code wms-master} 暴露货主查询能力后</b>，删除本端口的实现类，改为注入其 Application 接口
 * （或把实现搬到 {@code wms-app} 装配）；本端口的签名是按「能力提供方能满足的形状」设计的：
 * 一次给一批标识、拿回映射，调用方不必知道货主有几条。
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

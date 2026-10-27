package com.yunwms.master.application.query;

import java.util.Collection;
import java.util.Map;

/**
 * 对外暴露的货主名称查询能力：按标识批量取名称。
 *
 * <p><b>为什么由 {@code wms-master} 提供、由 {@code wms-app} 装配：</b>{@code owner} 表归本模块所有，
 * 别的模块要显示货主名称时不能自己写 SQL。{@code docs/coding-project.md} 第二节要求跨模块取数
 * 「在能力提供方暴露 Application 接口，需求方依赖该接口」，本接口就是那个能力；
 * {@code wms-app} 的在途适配器把需求方的端口接到本接口上。
 *
 * <p><b>口径：不按 {@code deleted_at} 过滤。</b>调用方是「显示账号绑的是哪个货主」这类查询，
 * 货主被软删除之后名称仍然必须显示——否则「绑了一个已删除的货主」与「没绑货主」在界面上
 * 长得一样。这条口径由 {@code OwnerMapper#selectNamesByIds} 的 SQL 保证，并在集成测试里固定。
 */
public interface OwnerNameLookup {

    /**
     * 按标识批量取货主名称。
     *
     * <p>标识集合为空时返回空映射且不发查询：空的 {@code IN} 会在 SQL 里退化成不带条件的查询。
     *
     * @param ownerIds 货主标识集合，可为 {@code null}（按空集合处理）
     * @return 货主标识到名称的映射；查不到的标识不出现在结果里
     */
    Map<Long, String> findNamesByIds(Collection<Long> ownerIds);
}

package com.yunwms.master.application.query;

import java.util.Set;

/**
 * 对外暴露的「某人负责哪些货主」查询能力。
 *
 * <p><b>为什么由 {@code wms-master} 提供、由 {@code wms-app} 装配：</b>
 * {@code owner_assignee} 是货主侧事实，别的模块要按负责人推导可见范围时不能自己写 SQL。
 * {@code docs/coding-project.md} 第二节要求跨模块取数「在能力提供方暴露 Application 接口，
 * 需求方依赖该接口」，本接口就是那个能力。
 *
 * <p><b>口径：主副负责人都算，且<b>不</b>按货主状态过滤。</b>调用方是 {@code ASSIGNED} 数据范围的
 * 实时推导，它需要的是「关系」；一个已终止的货主仍属于这位客服负责过，
 * 它是否出现在业务列表里由货主自身的查询条件决定，与本接口无关。
 */
public interface OwnerAssigneeLookup {

    /**
     * 取某个用户负责（主或副）的货主标识。
     *
     * @param userId 用户标识
     * @return 货主标识集合；没有负责任何货主时为空集合
     */
    Set<Long> findOwnerIdsByUserId(long userId);
}

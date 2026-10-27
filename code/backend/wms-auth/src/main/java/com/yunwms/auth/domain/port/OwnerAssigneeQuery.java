package com.yunwms.auth.domain.port;

import java.util.Set;

/**
 * 「我负责的货主」的推导入口：按用户取 {@code owner_assignee} 里的货主。
 *
 * <p>规格要求这个范围<b>实时推导</b>、存储为静态名单是不允许的：客服负责的货主会变，
 * 存成名单后每新增一个货主都要回写所有客服，漏一个就少看一个货主。因此本端口每次调用都查库，
 * 不缓存——缓存的粒度（请求级）由调用方决定，端口只保证「这一次回答的是库里此刻的事实」。
 *
 * <p>主副负责人都算：{@code owner_assignee} 的 {@code relation_type} 区分 PRIMARY 与 SECONDARY，
 * 而规格的可见范围是「当前担任主或副负责人的货主之并集」。
 *
 * <p>不按货主状态过滤：这里回答的是「关系」，不是「这个货主现在还有没有业务」。已终止的货主
 * 仍属于这位客服负责过，页面看得到但业务上不可用——那是货主自身状态该表达的事。
 *
 * <p><b>为什么是一个端口而不是在 auth 里直接查 {@code owner_assignee}：</b>该表归 {@code wms-master}
 * （它描述的是货主侧事实），跨模块直读会绕开对方的归属规则。本端口由 {@code wms-app} 的适配器
 * 接到 {@code wms-master} 的 Application 能力上。
 */
public interface OwnerAssigneeQuery {

    /**
     * 取该用户当前负责（主或副）的货主标识。
     *
     * @param userId 用户标识
     * @return 货主标识集合；没有负责任何货主时为空集合
     */
    Set<Long> findAssignedOwnerIds(long userId);
}

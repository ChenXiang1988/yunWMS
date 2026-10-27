package com.yunwms.master.application.query;

import java.util.Collection;
import java.util.Set;

/**
 * 对外暴露的货主可用性查询能力：给定一批标识，回答其中哪些存在、未删除且启用。
 *
 * <p><b>为什么由 {@code wms-master} 提供、由 {@code wms-app} 装配：</b>{@code owner} 表归本模块所有，
 * 别的模块要判断「货主能不能被引用」时不能自己写 SQL。{@code docs/coding-project.md} 第二节要求
 * 跨模块取数「在能力提供方暴露 Application 接口，需求方依赖该接口」，本接口就是那个能力。
 *
 * <p><b>口径：只回答「可用」，不含已删除与停用的货主。</b>调用方是数据范围配置的校验
 * （{@code wms-auth}），把停用或已删除的货主写进配置等于让配置指向一个界面上选不到的对象。
 * 这条口径与 {@link OwnerNameLookup} 正相反，两者不能互相替代。
 */
public interface OwnerExistenceLookup {

    /**
     * 从给定标识中筛出可用的货主。
     *
     * <p>标识集合为空时返回空集合且不发查询：空的 {@code IN} 要么拼成非法语句，
     * 要么退化成不带条件的全表查询。
     *
     * @param ownerIds 待判定的货主标识集合，可为 {@code null}（按空集合处理）
     * @return 可用的货主标识；一个都不可用时为空集合
     */
    Set<Long> findExistingActiveIds(Collection<Long> ownerIds);
}

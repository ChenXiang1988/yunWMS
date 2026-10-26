package com.yunwms.master.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Param;

/**
 * {@code sku} 的只读计数，供终止影响清单使用。
 *
 * <p><b>为什么不复用 {@link OwnerMapper}：</b>那个接口是 {@code owner} 一张表的映射，
 * 它的每个方法都能在同一个 XML 文件里读完。把 {@code sku} 的计数塞进去，「改 owner 表要动哪些
 * SQL」这个问题就多出一个不在同一张表上的答案。
 *
 * <p><b>只有一条语句，且只有统计用途：</b>商品档案的建、改、查属 {@code implement-sku}。
 * 这里刻意不提供列表或明细——影响清单只报数量，多一个查询方法就多一处「master 也能读出商品明细」
 * 的错觉，而那时它的寿命与归属都会变得说不清。
 *
 * <p>走 XML 而不是 {@code BaseMapper}：本接口不映射任何实体（没有 {@code SkuEntity}，
 * 也不该为了一个计数去建一个只用到 {@code owner_id} 的表映射类），且 {@code deleted_at} 条件
 * 必须写在语句里、让人一眼看见。
 */
public interface SkuCountMapper {

    /**
     * 统计该货主下仍存在（未逻辑删除）的商品数。
     *
     * @param ownerId 货主标识
     * @return 商品数量；一件都没有时返回 0
     */
    long countByOwnerId(@Param("ownerId") long ownerId);
}

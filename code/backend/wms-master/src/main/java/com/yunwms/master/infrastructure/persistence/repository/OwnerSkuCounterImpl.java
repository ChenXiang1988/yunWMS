package com.yunwms.master.infrastructure.persistence.repository;

import com.yunwms.master.domain.port.OwnerSkuCounter;
import com.yunwms.master.infrastructure.persistence.mapper.SkuCountMapper;
import org.springframework.stereotype.Repository;

/**
 * {@link OwnerSkuCounter} 的 MyBatis 实现。
 *
 * <p>放在 {@code infrastructure/persistence/repository} 而不是 {@code .service}：它读写的是库，
 * 名字里的 Repository 表示「持久化入口」，与用例无关。放成 {@code @Service} 会让一个只有一行
 * 计数的类看起来带着业务判断。
 *
 * <p>不做缓存、不做兜底：计数只有一次库往返，缓存会引入「终止弹窗上的数字过期多久」这个
 * 说不清的问题，而这条查询命中的是 {@code ix_sku_owner}（{@code owner_id} 上的部分索引）。
 */
@Repository
public class OwnerSkuCounterImpl implements OwnerSkuCounter {

    private final SkuCountMapper skuCountMapper;

    public OwnerSkuCounterImpl(SkuCountMapper skuCountMapper) {
        this.skuCountMapper = skuCountMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code count(*)} 恒返回一行，因此这里不存在「查不到」的分支：一件商品都没有时得到 0，
     * 而不是空值。
     */
    @Override
    public long countByOwnerId(long ownerId) {
        return skuCountMapper.countByOwnerId(ownerId);
    }
}

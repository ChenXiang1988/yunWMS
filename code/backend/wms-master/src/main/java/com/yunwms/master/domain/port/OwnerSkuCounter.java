package com.yunwms.master.domain.port;

/**
 * 按货主统计在册商品数，供「终止影响清单」使用。
 *
 * <p><b>为什么在 master 内直接查：</b>{@code sku} 属主数据域（{@code V012}），本模块是它的归属方，
 * 不需要跨模块装配。计数只读、单表、按 {@code owner_id} 等值，因此实现放在
 * {@code infrastructure/persistence}，不经过任何业务判断。
 *
 * <p><b>口径是「仍存在」：</b>排除 {@code deleted_at} 不为空的行。已删除的商品不构成终止的影响，
 * 把它们算进去会让影响清单比实际大，管理员据此做出的「确认终止」判断就建立在错误的数字上。
 *
 * <p><b>为什么单独成一个端口而不是塞进 {@link OwnerRepository}：</b>那个端口读写的是 {@code owner}
 * 这一张表，它的写语句按类型限定可改列。把 {@code sku} 的计数混进去，「OwnerRepository 能碰几张表」
 * 就变成一个要读完全部方法才能回答的问题。计数能力将来随商品模块（{@code implement-sku}）搬到
 * 商品侧时，换掉本端口的实现即可，用例层不动。
 */
public interface OwnerSkuCounter {

    /**
     * 统计该货主下仍存在（未逻辑删除）的商品数。
     *
     * @param ownerId 货主标识
     * @return 商品数量；一件都没有时返回 0
     */
    long countByOwnerId(long ownerId);
}

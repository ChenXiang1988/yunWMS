package com.yunwms.master.domain.model;

import java.time.Instant;

/**
 * 货主（客户）聚合，承载从库中读到的既成事实。
 *
 * <p><b>为什么与 {@link NewOwner} 分成两个类型：</b>整型标识、系统编码与状态只在落库之后才存在。
 * 用一个 {@code id=0}、{@code code=null} 的 {@code Owner} 冒充「待插入」，会让「已存在的货主」
 * 与「即将存在的货主」变成同一种类型，读代码的人无法从签名上分辨，也会诱使后续代码
 * 直接拿它去更新而不校验它到底有没有落库。创建入口见 {@link NewOwner}。
 *
 * <p><b>重建路径同样校验：</b>紧凑构造器里判该判的，读到脏数据当场失败而不是先放行再让下游
 * 拿它去算。{@code ck_owner_*} 与 {@code NOT NULL} 是最后一道防线，拦的是写入；
 * 空串能过 {@code NOT NULL}，被绕过 CHECK 写坏的编码也能过——那时唯一的发现点就是这里。
 *
 * <p>编码、SKU 前缀与状态在本聚合里只读：编码由取号引擎生成后不可修改，SKU 前缀在建档时即锁定
 * （草稿同样不可改，见 {@code V035} 与 3.1 的裁决），状态流转由 {@link OwnerStatus} 的流转表
 * 与状态变更用例负责（3.2）。
 * 本类型本身仍不提供任何改法——它承载的是「读到的既成事实」，流转后的新状态走一次写入，
 * 而不是就地改一个字段，这样「谁在什么时候把它改成终止」留在 {@code updated_by} / {@code updated_at} 上。
 *
 * @param id               标识
 * @param code             系统编码，形如 {@code OWNER0001}，全局唯一且不可修改
 * @param name             客户名称，可重复
 * @param shortName        货主简称，用于 SKUID 标签；为空时展示层取 {@code name} 前 8 字
 * @param nameEn           英文名称，可为 {@code null}
 * @param externalCode     外部系统编码，可为 {@code null}；未删除时唯一
 * @param skuPrefix        SKU 前缀，全局唯一，创建后不可修改
 * @param ownerType        客户类型，取 {@code OWNER_TYPE} 字典的 value
 * @param settlementEntity 结算主体，开票与对账的抬头
 * @param status           货主状态
 * @param address          注册地址段
 * @param remark           备注，可为 {@code null}
 * @param createdAt        创建时间
 * @param createdBy        创建人标识，可为 {@code null}（历史数据或系统导入）
 * @param updatedAt        最后修改时间
 * @param updatedBy        最后修改人标识，可为 {@code null}
 */
public record Owner(
        long id,
        String code,
        String name,
        String shortName,
        String nameEn,
        String externalCode,
        SkuPrefix skuPrefix,
        String ownerType,
        String settlementEntity,
        OwnerStatus status,
        OwnerAddress address,
        String remark,
        Instant createdAt,
        Long createdBy,
        Instant updatedAt,
        Long updatedBy) {

    /**
     * 校验档案属性并拒绝残缺的重建结果。
     *
     * @throws IllegalArgumentException 编码为空、名称/客户类型/结算主体为空，或状态、前缀、地址缺失
     */
    public Owner {
        if (code == null || code.isBlank()) {
            // id 可以出现在提示里，编码为空时它是唯一的定位线索
            throw new IllegalArgumentException("货主编码不能为空，货主 id=" + id);
        }
        requireAttributes(name, ownerType, settlementEntity);
        requireCoreParts(status, skuPrefix, address);
    }

    /**
     * 校验名称、客户类型与结算主体非空。
     *
     * <p>包内可见而不是 {@code public}：这是聚合的属性不变量，创建与重建共用同一份判定
     * （{@link NewOwner} 的构造器调用它），不对外提供第二个入口——规则一旦有了公开方法，
     * 就有人会拿它当「校验工具」在别处调用，而它只判这三项。
     *
     * @param name             客户名称
     * @param ownerType        客户类型
     * @param settlementEntity 结算主体
     * @throws IllegalArgumentException 任一项为 {@code null} 或空白
     */
    static void requireAttributes(String name, String ownerType, String settlementEntity) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("客户名称不能为空");
        }
        if (ownerType == null || ownerType.isBlank()) {
            throw new IllegalArgumentException("客户类型不能为空");
        }
        if (settlementEntity == null || settlementEntity.isBlank()) {
            throw new IllegalArgumentException("结算主体不能为空");
        }
    }

    /**
     * 校验重建时不能缺失的三个组成部分。
     *
     * <p>三者在库里都是 {@code NOT NULL}，缺失只可能来自读投影漏列或映射错列。
     * 那时继续构造会得到 {@code status=null} 的聚合，而 {@code isActive()} 之类的派生判断
     * 会把它当成「不是合作中」，从而在一段时间里静默地少算一批货主。
     *
     * @param status    货主状态
     * @param skuPrefix SKU 前缀
     * @param address   注册地址
     * @throws IllegalArgumentException 任一项为 {@code null}
     */
    private static void requireCoreParts(OwnerStatus status, SkuPrefix skuPrefix, OwnerAddress address) {
        if (status == null) {
            throw new IllegalArgumentException("货主状态缺失，重建结果不可用");
        }
        if (skuPrefix == null) {
            throw new IllegalArgumentException("SKU 前缀缺失，重建结果不可用");
        }
        if (address == null) {
            throw new IllegalArgumentException("注册地址缺失，重建结果不可用");
        }
    }
}

package com.yunwms.master.application.dto;

import com.yunwms.master.domain.model.OwnerAddress;

import java.time.Instant;

/**
 * 货主详情：列表行的那份摘要，加上只有详情页「基本信息」页签才需要的字段。
 *
 * <p><b>用组合而不是把摘要的字段再铺一遍：</b>详情与列表在「这是哪个货主、什么状态、什么时候改的」
 * 上必须是同一份口径。铺开成两个 record 之后，给摘要加一个字段就会漏掉其中一处，
 * 而漏掉的那处不会有编译错误（与 {@code RoleDetail} 同一取舍）。
 *
 * <p><b>为什么不直接把领域聚合 {@code Owner} 交给接口层：</b>聚合里带着 {@code createdBy} /
 * {@code updatedBy} 两个内部标识与 SKU 前缀值对象，接口不需要它们，也不该有机会把它们序列化出去。
 * 本类型是「详情页要显示什么」这一问题的答案，与聚合的完整性无关。
 *
 * @param summary          列表行同源的那份摘要：标识、编码、名称、客户类型、状态、更新时间
 * @param nameEn           英文名称，可为 {@code null}
 * @param shortName        货主简称，可为 {@code null}；为空时展示层取 {@code name} 前 8 字
 * @param externalCode     外部系统编码，可为 {@code null}
 * @param skuPrefix        SKU 前缀原文，只读；创建后不可修改
 * @param settlementEntity 结算主体，开票与对账的抬头
 * @param address          注册地址段，六个字段由接口层铺平后返回
 * @param remark           备注，可为 {@code null}
 * @param createdAt        创建时刻，取自库内 UTC 时间
 */
public record OwnerDetail(
        OwnerPage.Summary summary,
        String nameEn,
        String shortName,
        String externalCode,
        String skuPrefix,
        String settlementEntity,
        OwnerAddress address,
        String remark,
        Instant createdAt) {
}

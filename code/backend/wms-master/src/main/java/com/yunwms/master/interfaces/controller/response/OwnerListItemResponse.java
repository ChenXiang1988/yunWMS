package com.yunwms.master.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 货主列表的一行。
 *
 * <p><b>没有「主负责人」与「SKU 数」两个字段，这是接口契约的一部分，不是遗漏：</b>
 * 界面上那两列分别依赖任务 3.5（联系人）与商品模块，本批没有任何数据来源。
 * 返回一个恒为 {@code null} 的负责人或恒为 0 的计数，前端会以为「这一列存在但暂时没数据」；
 * 字段不出现，才表示「这个能力还没有」。两者都不该用占位值假装已实现。
 *
 * @param id        货主标识，字符串形式；详情、编辑、状态流转都用它定位
 * @param code      系统编码，形如 {@code OWNER0001}；由编码规则生成，创建后不可修改
 * @param name      客户名称，可重复——名称不是标识
 * @param ownerType 客户类型，取 {@code OWNER_TYPE} 字典的 value：{@code BRAND} 品牌方，{@code FORWARDER} 货代同行
 * @param status    货主状态编码：DRAFT 草稿，POTENTIAL 潜在，ACTIVE 合作中，SUSPENDED 暂停，TERMINATED 终止
 * @param updatedAt 最后修改时刻，ISO 8601 带时区（UTC）；编辑与状态流转都会推进它
 */
public record OwnerListItemResponse(
        @Schema(description = "货主标识，字符串形式的十进制整数；详情、编辑都用它定位", example = "5")
        String id,

        @Schema(description = "系统编码，形如 OWNER0001，由编码规则生成且不可修改；列表中按等宽字体展示",
                example = "OWNER0001")
        String code,

        @Schema(description = "客户名称，可与其他货主重名；点击进入详情", example = "深圳某某贸易")
        String name,

        @Schema(description = "客户类型，取自 OWNER_TYPE 字典的 value：BRAND 品牌方，FORWARDER 货代同行；"
                + "按标签样式展示", example = "BRAND")
        String ownerType,

        @Schema(description = "货主状态编码：DRAFT 草稿，POTENTIAL 潜在，ACTIVE 合作中，SUSPENDED 暂停，"
                + "TERMINATED 终止。终止态的货主仍出现在列表里，写操作按钮应置灰", example = "ACTIVE")
        String status,

        @Schema(description = "最后修改时刻，ISO 8601 带时区（UTC）", example = "2026-10-24T08:30:00Z")
        Instant updatedAt) {
}

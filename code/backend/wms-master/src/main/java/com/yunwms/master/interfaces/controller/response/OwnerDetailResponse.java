package com.yunwms.master.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 货主详情：列表行的全部字段，加上详情页「基本信息」页签的其余字段。
 *
 * <p><b>铺平成一层而不是嵌一层 {@code summary}：</b>详情与列表行在 HTTP 上是两个并列对象，
 * 前端按同一套字段名渲染，嵌一层只会让取值路径变成两种（与 {@code RoleDetailResponse} 同一取舍）。
 *
 * <p><b>地址六字段也是铺平的</b>，不嵌套成 {@code address} 对象：它与建档 / 编辑请求体的字段名
 * 一一对应，前端「读详情 → 填进编辑表单 → 全量提交」这条链路上不需要任何字段搬移。
 *
 * <p><b>本响应只覆盖「基本信息」页签。</b>联系人、税号与账号三个页签分别属任务 3.5 / 3.6 / 3.3，
 * 本批不返回任何占位字段——理由见 {@code OwnerListItemResponse} 关于「主负责人」与「SKU 数」的说明。
 *
 * @param id               货主标识，字符串形式；与列表行同名字段含义一致
 * @param code             系统编码，形如 {@code OWNER0001}；只读，创建后不可修改
 * @param name             客户名称，可重复
 * @param ownerType        客户类型，取 {@code OWNER_TYPE} 字典的 value
 * @param status           货主状态编码；只读，状态流转属任务 3.2
 * @param updatedAt        最后修改时刻，ISO 8601 带时区（UTC）
 * @param nameEn           英文名称，可为 {@code null}
 * @param shortName        货主简称，可为 {@code null}；为空时展示层取 {@code name} 前 8 字
 * @param externalCode     外部系统编码，可为 {@code null}；在未删除的货主之间唯一
 * @param skuPrefix        SKU 前缀原文，只读；创建后不可修改，也不会出现在编辑接口里
 * @param settlementEntity 结算主体，开票与对账的抬头
 * @param country          注册国家，2 位大写字母的国家代码
 * @param province         州省；国家为美国时必填，其余国家可为 {@code null}
 * @param city             注册城市
 * @param district         区县，可为 {@code null}
 * @param postalCode       邮政编码，可为 {@code null}；港澳台等无邮编地区为空
 * @param addressLine      详细地址
 * @param remark           备注，可为 {@code null}
 * @param createdAt        创建时刻，ISO 8601 带时区（UTC）
 */
public record OwnerDetailResponse(
        @Schema(description = "货主标识，字符串形式的十进制整数", example = "5")
        String id,

        @Schema(description = "系统编码，形如 OWNER0001；只读，创建后不可修改", example = "OWNER0001")
        String code,

        @Schema(description = "客户名称，可与其他货主重名", example = "深圳某某贸易")
        String name,

        @Schema(description = "客户类型，取自 OWNER_TYPE 字典的 value：BRAND 品牌方，FORWARDER 货代同行",
                example = "BRAND")
        String ownerType,

        @Schema(description = "货主状态编码：DRAFT 草稿，POTENTIAL 潜在，ACTIVE 合作中，SUSPENDED 暂停，"
                + "TERMINATED 终止。只读——流转入口属状态流转任务", example = "ACTIVE")
        String status,

        @Schema(description = "最后修改时刻，ISO 8601 带时区（UTC）", example = "2026-10-24T08:30:00Z")
        Instant updatedAt,

        @Schema(description = "英文名称，可为 null", example = "Shenzhen ABC Trading")
        String nameEn,

        @Schema(description = "货主简称，可为 null；用于 SKUID 标签，为空时展示层取名称前 8 字",
                example = "某某贸易")
        String shortName,

        @Schema(description = "外部系统编码，可为 null；在未删除的货主之间唯一，被他人占用时编辑返回 40906",
                example = "EXT-001")
        String externalCode,

        @Schema(description = "SKU 前缀原文（4 位大写字母或数字，不含 0、O、1、I）；只读，创建后不可修改",
                example = "AMZ1")
        String skuPrefix,

        @Schema(description = "结算主体，开票与对账的抬头", example = "深圳某某贸易有限公司")
        String settlementEntity,

        @Schema(description = "注册国家，2 位大写字母的 ISO 3166-1 alpha-2 代码，取自 COUNTRY 字典",
                example = "CN")
        String country,

        @Schema(description = "州省，取 PROVINCE 字典中属于所选国家的值；国家为 US 时必有值，其余国家可为 null",
                example = "CN-GD")
        String province,

        @Schema(description = "注册城市", example = "深圳")
        String city,

        @Schema(description = "区县，可为 null", example = "南山区")
        String district,

        @Schema(description = "邮政编码；无邮编的地区（港澳台）为 null", example = "518000")
        String postalCode,

        @Schema(description = "详细地址", example = "科技园路 1 号")
        String addressLine,

        @Schema(description = "备注，可为 null", example = "")
        String remark,

        @Schema(description = "创建时刻，ISO 8601 带时区（UTC）", example = "2026-10-23T10:00:00Z")
        Instant createdAt) {
}

package com.yunwms.master.interfaces.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 编辑货主的请求体：全量替换可编辑字段，字段传 {@code null} 表示清空。
 *
 * <p><b>为什么没有 {@code code} / {@code skuPrefix} / {@code initialStatus} / {@code status}：</b>
 * 四者都不是「档案属性」。编码是标识，改了就不是同一个货主；SKU 前缀是已生成 SKUID 的前四位，
 * 改它会让已打印的标签指向另一个货主的货（草稿期同样不允许）；状态与建档入口属状态机（3.2），
 * 只能按流转规则走。多传这四个字段会被 {@code FAIL_ON_UNKNOWN_PROPERTIES} 拒绝，
 * 而不是静默忽略——静默忽略会让前端以为改生效了。
 *
 * <p>请求体里也没有 {@code ownerId}：它取自路径变量，一个请求只作用于一个货主，
 * 放在两处就出现了「以哪个为准」的问题。
 *
 * <p>长度上限与单字段必填的判定口径与建档请求体一致，理由见 {@link OwnerCreateRequest}。
 *
 * @param name             客户名称，可重复
 * @param nameEn           英文名称，可为空
 * @param shortName        货主简称，可为空
 * @param externalCode     外部系统编码，可为空；未删除时唯一
 * @param ownerType        客户类型：{@code BRAND} 品牌方，{@code FORWARDER} 货代同行
 * @param settlementEntity 结算主体
 * @param country          注册国家，2 位大写字母的国家代码
 * @param province         州省；国家为美国时必填
 * @param city             城市
 * @param district         区县，可为空
 * @param postalCode       邮政编码；无邮编的地区（港澳台）可为空
 * @param addressLine      详细地址
 * @param remark           备注，可为空
 */
public record OwnerUpdateRequest(
        @NotBlank(message = "客户名称不能为空")
        @Size(max = 100, message = "客户名称不能超过 100 个字符")
        @Schema(description = "客户名称，必填，最长 100 个字符；可与其他货主重名", example = "深圳某某贸易")
        String name,

        @Size(max = 100, message = "英文名称不能超过 100 个字符")
        @Schema(description = "英文名称，最长 100 个字符，可为空（传 null 表示清空）")
        String nameEn,

        @Size(max = 20, message = "货主简称不能超过 20 个字符")
        @Schema(description = "货主简称，最长 20 个字符，可为空（传 null 表示清空）")
        String shortName,

        @Size(max = 64, message = "外部码不能超过 64 个字符")
        @Schema(description = "外部系统编码，最长 64 个字符，可为空；在未删除的货主之间唯一，"
                + "被占用返回 40906。传 null 表示清空，清空后该编码可被其他货主使用")
        String externalCode,

        @NotBlank(message = "客户类型不能为空")
        @Size(max = 20, message = "客户类型不能超过 20 个字符")
        @Schema(description = "客户类型，必填，取自 OWNER_TYPE 字典的启用项：BRAND 品牌方，"
                + "FORWARDER 货代同行；不在字典里返回 40000")
        String ownerType,

        @NotBlank(message = "结算主体不能为空")
        @Size(max = 100, message = "结算主体不能超过 100 个字符")
        @Schema(description = "结算主体，必填，最长 100 个字符；开票与对账的抬头")
        String settlementEntity,

        @NotBlank(message = "注册国家不能为空")
        @Size(max = 2, message = "注册国家不能超过 2 个字符")
        @Schema(description = "注册国家，必填，2 位大写字母的 ISO 3166-1 alpha-2 代码，取自 COUNTRY 字典的"
                + "启用项；不在字典里返回 40000")
        String country,

        @Size(max = 64, message = "州省不能超过 64 个字符")
        @Schema(description = "州省，取 PROVINCE 字典中属于所选国家的启用项，不为空时必须属于该国（否则 40000）；"
                + "国家为 US 时必填")
        String province,

        @NotBlank(message = "注册城市不能为空")
        @Size(max = 64, message = "注册城市不能超过 64 个字符")
        @Schema(description = "注册城市，必填，最长 64 个字符")
        String city,

        @Size(max = 64, message = "区县不能超过 64 个字符")
        @Schema(description = "区县，最长 64 个字符，可为空（传 null 表示清空）")
        String district,

        @Size(max = 20, message = "邮政编码不能超过 20 个字符")
        @Schema(description = "邮政编码，最长 20 个字符；港澳台等无邮编地区可为空，其余地区必填，"
                + "缺失返回 40000")
        String postalCode,

        @NotBlank(message = "详细地址不能为空")
        @Size(max = 255, message = "详细地址不能超过 255 个字符")
        @Schema(description = "详细地址，必填，最长 255 个字符")
        String addressLine,

        @Size(max = 500, message = "备注不能超过 500 个字符")
        @Schema(description = "备注，最长 500 个字符，可为空（传 null 表示清空）")
        String remark) {
}

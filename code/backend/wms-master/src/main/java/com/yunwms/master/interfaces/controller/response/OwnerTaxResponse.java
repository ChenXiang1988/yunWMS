package com.yunwms.master.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 货主税号的响应体，列表与新增共用一份。
 *
 * <p><b>标识以字符串传输：</b>{@code Long} 超过 2^53 时 JS 会丢精度，等到出问题再改要动全部前端，
 * 而首日就按字符串发没有任何代价（{@code docs/api-convention.md} 第五节）。与
 * {@code OwnerContactResponse}、{@code OwnerListItemResponse} 同一约定。
 *
 * <p><b>没有 {@code ownerId}：</b>它就在请求路径上（{@code /owners/{ownerId}/taxes}）。
 * 响应里再带一份，等于给前端两个可能不一致的来源——某次编辑之后前端手里有两处 {@code ownerId}，
 * 用哪一个去拼下一次请求就成了没有依据的选择，而这处不一致只有在真出错时才会被发现。
 *
 * <p><b>不回带审计列与创建时间：</b>税号页签展示的是「哪一国的哪种税号」，{@code created_by} /
 * {@code updated_at} 由审计留痕负责（对象类型沿用 {@code OWNER}，从货主详情追溯），
 * 读侧没有第二个消费者。
 *
 * @param id        税号标识，字符串形式的十进制整数；编辑与删除都用它定位
 * @param country   国家，2 位大写字母（ISO 3166-1 alpha-2）
 * @param province  州省，未填时为 {@code null}；州销售税号一定有值
 * @param taxType   类型取值，取 {@code TAX_ID_TYPE} 字典的 value，如 {@code US_STATE_SALES}
 * @param taxNumber 税号，必填，原样返回；不做脱敏与格式化
 * @param isDefault 是否该货主在该国家下的默认税号；同一货主同一国家至多一条为 {@code true}
 */
public record OwnerTaxResponse(
        @Schema(description = "税号标识，字符串形式的十进制整数；编辑与删除都用它定位", example = "7")
        String id,

        @Schema(description = "国家，2 位大写字母的 ISO 3166-1 alpha-2 代码，取自 COUNTRY 字典的 value",
                example = "US")
        String country,

        @Schema(description = "州省，取 PROVINCE 字典的 value；国家级税号（统一社会信用代码、VAT）"
                + "未填时为 null，州销售税号一定有值", example = "US-CA")
        String province,

        @Schema(description = "税号类型，取自 TAX_ID_TYPE 字典的 value：CN_USCC 统一社会信用代码、"
                + "US_EIN 雇主识别号、US_STATE_SALES 州销售税号、VAT 增值税号；展示时按字典取中文标签，"
                + "接口只给 value", example = "US_STATE_SALES")
        String taxType,

        @Schema(description = "税号，必填字段，不会为 null；原样返回，不做脱敏与格式化",
                example = "941234567")
        String taxNumber,

        @Schema(description = "是否该货主在该国家下的默认税号，同一货主同一国家至多一条为 true；"
                + "页签上的「默认」标记用它渲染", example = "true")
        boolean isDefault) {
}

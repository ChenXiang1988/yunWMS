package com.yunwms.master.interfaces.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 货主税号的写入请求体，新增与编辑共用一份。
 *
 * <p><b>没有 {@code ownerId}：</b>归属从路径 {@code /api/console/owners/{ownerId}/taxes} 取。
 * 请求体里再带一份就出现「路径说 A、身体说 B」，而两边不一致时没有依据说哪一处算准；
 * 编辑时更糟——服务的归属判定根本不读它，字段却出现在契约里，调用方会以为自己改了归属。
 * 也没有 {@code taxId}：编辑与删除的标识在路径上（与联系人端点同一处理）。
 *
 * <p><b>编辑时 {@code country} 与 {@code taxType} 必填但不参与判定：</b>这两列是这一行的身份，
 * 服务以库里那一行为准，「类型是否适用该国家」「州省是否属于该国」判的也是行上的值（见
 * {@code OwnerTaxService#update}）。因此编辑时把 {@code country} 传成另一个国家不会报错、
 * 也不会把这条登记搬到另一个国家——这与联系人端点对路径 {@code ownerId} 的取舍同源：
 * 归属口径只留一处真源，宁可让传错的调用方得不到 400，也不新增第二处可以分歧的判据。
 * 保留这两个字段而不是为编辑另造请求体，是因为两条入口的字段集完全一致，拆开只多一次逐字段转写，
 * 而转写漏一个字段的后果是「改了个号码，默认标记被清空」，这类缺陷不会被编译拦下。
 *
 * <p><b>不在这里写「类型与国家是否匹配」「州销售税号是否选了州省」「州省是否属于国家」
 * 「统一社会信用代码是否 18 位」这四条：</b>它们要么需要查字典、要么依赖类型的语义，已由
 * {@code OwnerTaxService} 判定并给出带原值的 40000 提示。在这里用注解或枚举再表达一遍就是第二处真源：
 * 字典新增一个国家或停用某一项之后，注解放不过而用例放得过（或反过来），两种症状都只在运行期出现。
 * 本类只用标准注解表达单字段约束（必填与列宽），取值之间是否配得上交给持有那条规则的那一处。
 *
 * <p><b>列宽取自 {@code V009__owner_tax_registration.sql} 的列定义：</b>{@code country} 2、
 * {@code province} 64、{@code taxType} 20、{@code taxNumber} 64。没有这些约束，超长输入会以数据库的
 * {@code 22001} 出现在 50000 上，而那是一次纯粹的入参错误。
 *
 * <p><b>{@code isDefault} 用 {@code Boolean} 而不是 {@code boolean}：</b>让「没传」这件事在类型上可见。
 * 新增与编辑都是全量替换，缺省按 {@code false}（不是该国默认）处理——这是唯一不产生连带改动的默认值，
 * 换成 {@code true} 会让「漏传一个字段」变成「把同国另一行的默认标记清掉了」。转换在
 * {@code OwnerTaxController} 里用 {@code Boolean.TRUE.equals} 写死一次，读方法签名就能读到这个口径。
 *
 * <p>字段原样提交、原样入库，接口层不裁首尾空格：整串空白会在领域层被归一为 {@code null}
 * （{@code province} 因此按「没填」处理），而有内容的串一律保留用户的真实输入。
 *
 * @param country   国家，必填，2 位大写字母（ISO 3166-1 alpha-2），取 {@code COUNTRY} 字典的 value
 * @param province  州省，可为空，最长 64 个字符；取 {@code PROVINCE} 字典中属于所选国家的启用项，
 *                  州销售税号（{@code US_STATE_SALES}）必须填
 * @param taxType   税号类型，必填，取 {@code TAX_ID_TYPE} 字典的 value，最长 20 个字符
 * @param taxNumber 税号，必填，最长 64 个字符；{@code CN_USCC} 还要求 18 位大写字母或数字
 * @param isDefault 是否设为该货主在该国家下的默认税号；缺省按 {@code false} 处理，
 *                  置 {@code true} 会先清掉同国的旧默认
 */
public record OwnerTaxSaveRequest(

        @NotBlank(message = "国家不能为空")
        @Size(max = 2, message = "国家不能超过 2 个字符")
        @Schema(description = "国家，必填，2 位大写字母的 ISO 3166-1 alpha-2 代码，取 COUNTRY 字典的 value；"
                + "编辑时服务以记录自身的国家为准，此值不参与判定", example = "US")
        String country,

        @Size(max = 64, message = "州省不能超过 64 个字符")
        @Schema(description = "州省，可为空，最长 64 个字符，取 PROVINCE 字典中属于所选国家的启用项。"
                + "不为空时必须属于所选国家，否则返回 40000；税号类型为 US_STATE_SALES（州销售税号）时必填，"
                + "其余类型（统一社会信用代码、EIN、VAT）可以留空。传 null 或整串空白表示清空",
                example = "US-CA")
        String province,

        @NotBlank(message = "税号类型不能为空")
        @Size(max = 20, message = "税号类型不能超过 20 个字符")
        @Schema(description = "税号类型，必填，取 TAX_ID_TYPE 字典的启用项：CN_USCC 统一社会信用代码、"
                + "US_EIN 雇主识别号、US_STATE_SALES 州销售税号、VAT 增值税号。空白、停用项、未知取值，"
                + "或该类型不适用于所填国家，均返回 40000；编辑时服务以记录自身的类型为准，此值不参与判定",
                example = "US_STATE_SALES")
        String taxType,

        @NotBlank(message = "税号不能为空")
        @Size(max = 64, message = "税号不能超过 64 个字符")
        @Schema(description = "税号，必填，最长 64 个字符；按原样保存，不去首尾空格。"
                + "只有 CN_USCC 校验形态（18 位大写字母或数字），其余类型随各国税制而变，不做格式校验",
                example = "941234567")
        String taxNumber,

        @Schema(description = "是否设为该货主在该国家下的默认税号，缺省（不传）按 false 处理。"
                + "置 true 时接口会先把该货主同国家的原有默认税号置为非默认再写入本条，因此一个国家"
                + "至多一条默认。要保留一条已有的默认标记必须显式传 true——本接口是全量替换，"
                + "省略该字段等于提交 false", example = "true")
        Boolean isDefault) {
}

package com.yunwms.master.application.command;

/**
 * 货主税号的写入入参：新增与编辑共用一份。
 *
 * <p><b>为什么新增与编辑共用一个类型：</b>与 {@link SaveOwnerContactCommand} 同理由——两条入口的字段集
 * 一致，且唯一带状态味道的 {@code isDefault} 恰恰<b>必须</b>可改（换默认税号就是改这一位）。
 * 为编辑另造一个 record 只多一次逐字段转写，而转写漏一个字段的后果是「改了个号码，默认标记被清空」，
 * 这类缺陷不会被编译拦下。
 *
 * <p><b>本类型不做字段校验，也不归一空白串：</b>必填与列宽由 {@code OwnerTaxDraft} 的构造器判，
 * 入口（接口层的声明式约束）只校验一次；「税号类型是否适用该国家」与「中国统一社会信用代码是否 18 位」
 * 这两条依赖字典与类型语义的规则归 {@code OwnerTaxService}。同一条规则若在两个类型里各写一遍，
 * 两处对「什么算空白」迟早判得不一样。空白串的归一同样不在这里：{@code OwnerTaxDraft} 刻意把整串空白
 * 落成 {@code null}、非空白串原样保留，在这里再处理一次，落库的值与提交的值就不一致，
 * 审计里也解释不清「库里为什么和填的不一样」。
 *
 * <p><b>编辑时 {@link #ownerId}、{@link #country} 与 {@link #taxType} 都不参与任何判定：</b>
 * 归属与「哪一国的哪种税号」是库里那一行的身份（{@code OwnerTaxRepository#update} 的 SET 列表里
 * 没有这三列），用例先按税号标识取出该行、再判货主可见性。否则调用方自报一个归属就能改到别人的税号，
 * 自报一个类型就能把一条登记悄悄挪到另一个类型名下。保留这些字段而不是为编辑另造类型，
 * 理由见第一条。
 *
 * @param ownerId   所属货主标识，仅新增时使用
 * @param country   国家，两位大写字母（ISO 3166-1 alpha-2），仅新增时使用
 * @param province  州省，可为 {@code null}，最长 64 字符；国家级税号（如统一社会信用代码）没有州省
 * @param taxType   税号类型，取 {@code TAX_ID_TYPE} 字典的 value（{@code CN_USCC} / {@code US_EIN} /
 *                  {@code US_STATE_SALES} / {@code VAT}），仅新增时使用
 * @param taxNumber 税号，必填，最长 64 字符；{@code CN_USCC} 还要求 18 位大写字母或数字
 * @param isDefault 是否设为该货主在该国家下的默认税号；置 {@code true} 时用例会先清掉同国的旧默认
 */
public record SaveOwnerTaxCommand(
        long ownerId,
        String country,
        String province,
        String taxType,
        String taxNumber,
        boolean isDefault) {
}

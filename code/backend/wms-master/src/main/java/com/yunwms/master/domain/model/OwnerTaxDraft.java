package com.yunwms.master.domain.model;

/**
 * 货主税务登记的写入模型：新建与更新共用的输入值对象。
 *
 * <p><b>为什么新建与更新共用一个类型：</b>与 {@link OwnerContactDraft} 同理由——两者的可写字段集
 * 完全一致，没有编码、没有状态机、没有创建后锁定的字段，唯一带状态味道的 {@code isDefault}
 * 恰恰必须可改（换默认税号就是改这一位）。拆成两个类型只多一次逐字段转写，而转写漏一个字段的
 * 后果是「改了个号码，默认标记被清空」，这类缺陷不会被编译拦下。
 *
 * <p><b>但更新只落库其中三项：</b>{@code province}、{@code taxNumber}、{@code isDefault}。另两项
 * {@code country} 与 {@code taxType} 是这一行的身份——「哪一国的哪种税号」，更新语句的 SET 列表里
 * 没有它们（见 {@code OwnerTaxMapper#updateTax}），传进去的值只参与校验、不落库；{@code ownerId}
 * 根本不在本类型里。改归属或改「哪国哪种税号」在业务上是另一条登记记录，应删旧登新，
 * 而端口连「写这两列」的能力都不给。
 *
 * <p><b>为什么 {@code province} 可为 {@code null}，而规格写「每条 MUST 包含国家、州省、税号类型与税号」：</b>
 * {@code V009} 的 {@code ux_owner_tax_unique} 用 {@code COALESCE(province, '')} 把空州省归一再判唯一，
 * 说明建表时就把「没有州省」当成合法形态：国家级税号（统一社会信用代码、跨国的 VAT）
 * 不对应某个州省。本类型因此允许空——在写入侧一律拒绝会让这类税号根本登记不进来。
 * 「哪些国家/类型必须填州省」（如 {@code US_STATE_SALES}）是一项依赖 {@code TAX_ID_TYPE} 字典语义的
 * 规则，与「中国统一社会信用代码 18 位」这类格式校验一起在用例层判（3.6），不在模型里。
 *
 * <p><b>为什么归属不在这里：</b>本类没有 {@code ownerId}。新建时「挂到哪个货主」由仓储
 * {@code insert} 的入参决定，编辑时归属根本不可改（端口方法拿不到它），因此不存在
 * 「一次编辑把税号搬到另一个货主名下」的路径，而不是靠注释提醒别写那一列。
 * {@code id} 同理由库生成，{@link OwnerTaxRegistration} 的标识是「已落库」的既成事实。
 *
 * <p><b>空白串归一为 {@code null}，但非空白串原样保留：</b>四个文本字段先做一次
 * 「整串空白 → {@code null}」，再判不变量。归一是因为 {@code province} 可空，而整串空白在业务上
 * 与「没填」没有区别，存下 {@code ' '} 会让 {@code COALESCE(province, '')} 的唯一判重出现两条
 * 互相看不见的「空州省」；必填三项同样先归一，因此「只填了空格」得到的是「不能为空」而不是
 * 一条合法记录。反过来，非空白串一律不去首尾空格：那是用户的真实输入，领域层悄悄改写会让
 * 审计无法解释「库里的值和提交的不一致」。
 *
 * <p>本类不判「{@code taxType} 是否属于 {@code TAX_ID_TYPE} 字典的启用项、且适用该国家」：
 * 那需要查字典，属用例层的 {@code DictLookup#isItemApplicableTo}。也不判「该货主在该国家是否已有
 * 默认税号」：那是 {@code ux_owner_tax_default} 的事，端口的 {@code clearDefault} 负责先腾出位置，
 * 模型不重复判定。
 *
 * @param country   国家，必填，两位大写字母（ISO 3166-1 alpha-2）
 * @param province  州省，可为 {@code null}，最长 64 字符
 * @param taxType   税号类型，取 {@code TAX_ID_TYPE} 字典的 value（{@code CN_USCC} / {@code US_EIN} /
 *                  {@code US_STATE_SALES} / {@code VAT}），必填，最长 20 字符
 * @param taxNumber 税号，必填，最长 64 字符；格式随类型而变，不在本类判
 * @param isDefault 是否设为该货主在该国家下的默认税号；置 {@code true} 前须由调用方先清掉旧默认
 */
public record OwnerTaxDraft(
        String country,
        String province,
        String taxType,
        String taxNumber,
        boolean isDefault) {

    /**
     * 归一空白串并校验创建期不变量。
     *
     * <p>这是最后一道防线：用例层会先查字典、给出可展示的业务错误，这里拦的是绕过用例直接构造
     * （将来的批量导入、数据修复脚本）把脏数据写下去。规则本体在
     * {@link OwnerTaxRegistration#requireFields}，本类只多做一步归一。
     *
     * @throws IllegalArgumentException 国家不是两位大写字母，或税号类型/税号为空、任一字段超出列宽
     */
    public OwnerTaxDraft {
        country = blankToNull(country);
        province = blankToNull(province);
        taxType = blankToNull(taxType);
        taxNumber = blankToNull(taxNumber);
        OwnerTaxRegistration.requireFields(country, province, taxType, taxNumber);
    }

    /**
     * 把整串空白归一为 {@code null}，其余值原样返回。
     *
     * <p>只做「整串是否空白」这一个判断，不裁剪：理由见类注释「空白串归一为 {@code null}，
     * 但非空白串原样保留」一段。
     *
     * @param value 原始输入，可为 {@code null}
     * @return 整串空白时为 {@code null}，否则为原值
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

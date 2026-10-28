package com.yunwms.master.domain.port;

/**
 * 字典查询的窄端口：只回答客户管理需要问的四个问题。
 *
 * <p><b>为什么不直接依赖字典模块的查询端口：</b>字典能力当前落在 {@code wms-auth}，而架构约束是
 * {@code auth} 与 {@code master} 互不引用（见 {@code docs/coding-project.md} 第二节与
 * {@code ModuleBoundaryTest}）。由 {@code wms-app} 装配适配器，是「两个模块都在场的那份 classpath」
 * 才能做的事，因此这里不出现另一个模块的类型。
 *
 * <p><b>为什么只有四个方法，而不是把字典的完整查询能力复制一遍：</b>端口按使用方的需要声明，
 * 不按提供方的能力声明。本模块目前只问四件事——某个值在某个字典里是否存在且启用、某个州省
 * 是否属于某个国家、某个地区是否要求填写邮政编码、某个字典项是否适用于某个国家——多复制一个方法，
 * 就多一处「本模块也能查字典」的错觉，也会让适配器有多个实现分支要维护。将来商品模块需要按字典
 * 批量读取时，由它自己声明自己的窄端口。
 *
 * <p>{@link #isActiveItem} 与 {@link #isProvinceOfCountry} 的参数都要求非 {@code null}：调用方
 * （{@code OwnerDictionaryValidator}）判过非空才调用，收到 {@code null} 说明是编程错误，不是业务输入问题。
 * {@link #isItemApplicableTo} 对三个参数同样要求非空，且刻意不把「未填国家」当作合法输入，理由见该方法。
 * {@link #isPostalCodeRequired} 是例外，它把 {@code null} 当作一个有业务含义的输入（未填州省），
 * 理由见该方法自己的说明。
 */
public interface DictLookup {

    /**
     * 判断某字典下是否存在值为 {@code value} 的<b>启用</b>项。
     *
     * <p>停用项不算存在：停用意味着「不再被新数据引用」，历史数据照常展示。新建货主属于新数据。
     *
     * @param dictType 字典类型编码，如 {@code OWNER_TYPE}、{@code COUNTRY}
     * @param value    待判定的取值，即 {@code sys_dict_item.value}
     * @return 存在且启用时返回 {@code true}；字典类型或该项不存在时返回 {@code false}
     * @throws IllegalArgumentException 任一参数为 {@code null} 或空白
     */
    boolean isActiveItem(String dictType, String value);

    /**
     * 判断某州省是否属于某国家。
     *
     * <p>层级的权威定义在 {@code PROVINCE} 字典项上（{@code ext.parent} 指向国家 value），
     * 判定因此由实现去查字典，而不是让调用方记住「省码以国家码开头」这类巧合。
     *
     * @param provinceValue 州省取值，即 {@code PROVINCE} 字典项的 value，如 {@code US-CA}
     * @param countryValue  国家取值，即 {@code COUNTRY} 字典项的 value，如 {@code US}
     * @return 该州省存在、启用且属于该国家时返回 {@code true}
     * @throws IllegalArgumentException 任一参数为 {@code null} 或空白
     */
    boolean isProvinceOfCountry(String provinceValue, String countryValue);

    /**
     * 该地区是否要求填写邮政编码。
     *
     * <p>港澳台等地区没有邮政编码，强制填写会让这些货主无法建档。判断依据是字典项
     * 的 {@code ext.postalCodeRequired}，<b>缺失该键时为 true</b>——默认必填，只有明确
     * 无邮编的地区才豁免。
     *
     * <p>地区为空时返回 true：省州都不是必填项（美国除外），但邮编在规格里是必填。
     *
     * @param provinceValue 州省取值，即 {@code PROVINCE} 字典项的 value；{@code null} 或空白表示未填州省
     * @return 该地区要求填写邮政编码时返回 {@code true}；未填州省、字典里没有该项、或该项未写
     *         {@code postalCodeRequired} 时同样返回 {@code true}
     */
    boolean isPostalCodeRequired(String provinceValue);

    /**
     * 判断某字典项是否适用于某国家。
     *
     * <p>「适用」由字典项的 {@code ext.countries} 声明：缺该键（或清单为空）表示不限国家，
     * 例如跨多国的 {@code VAT}；列出了国家则只对清单内的国家适用，例如中国的统一社会信用代码、
     * 美国的 EIN 与州销售税号。判据放在字典数据里而不是本模块的代码里，是因为「哪个国家有哪些
     * 税号类型」会随贸易伙伴变化，写进 Java 就是每扩一国都要改代码发版。
     *
     * <p>本方法只暴露「适不适用」这一个结论，不交出 {@code ext} 的内容：一旦让调用方拿到属性表，
     * {@code wms-master} 就要认识每一项扩展键的名字与取值形状，字典侧改一个键就会穿透到业务规则里。
     *
     * <p>{@code countryValue} 不接受 {@code null} 或空白，与 {@link #isPostalCodeRequired} 不同：
     * 那个方法把空值当作「未填州省」这种有业务含义的输入，而税号的国家是必填列
     * （{@code V009} 的 {@code country varchar(2) NOT NULL}），调用方手里一定有一个国家值。
     * 在这里容忍空值，只会让一次调用方 bug 悄悄变成「这个类型不限国家」——本该被拦下的
     * 「州销售税号配中国」会一路放行。
     *
     * @param dictType     字典类型编码，如 {@code TAX_ID_TYPE}
     * @param value        待判定的字典项取值，即 {@code sys_dict_item.value}
     * @param countryValue 国家取值，即 {@code COUNTRY} 字典项的 value，如 {@code US}
     * @return 该项存在、启用，且其适用国家清单未限定国家或包含该国家时返回 {@code true}；
     *         字典类型或该项不存在、该项已停用、或清单里没有这个国家时返回 {@code false}
     * @throws IllegalArgumentException 任一参数为 {@code null} 或空白
     */
    boolean isItemApplicableTo(String dictType, String value, String countryValue);
}

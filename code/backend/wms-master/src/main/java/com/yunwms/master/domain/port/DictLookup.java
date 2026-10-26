package com.yunwms.master.domain.port;

/**
 * 字典查询的窄端口：只回答货主建档需要问的两个问题。
 *
 * <p><b>为什么不直接依赖字典模块的查询端口：</b>字典能力当前落在 {@code wms-auth}，而架构约束是
 * {@code auth} 与 {@code master} 互不引用（见 {@code docs/coding-project.md} 第二节与
 * {@code ModuleBoundaryTest}）。由 {@code wms-app} 装配适配器，是「两个模块都在场的那份 classpath」
 * 才能做的事，因此这里不出现另一个模块的类型。
 *
 * <p><b>为什么只有两个方法，而不是把字典的完整查询能力复制一遍：</b>端口按使用方的需要声明，
 * 不按提供方的能力声明。本模块目前只问两件事——某个值在某个字典里是否存在且启用、某个州省
 * 是否属于某个国家——多复制一个方法，就多一处「本模块也能查字典」的错觉，
 * 也会让适配器有多个实现分支要维护。将来商品模块需要按字典批量读取时，由它自己声明自己的窄端口。
 *
 * <p>方法的参数都要求非 {@code null}：调用方（{@code OwnerDictionaryValidator}）判过非空才调用，
 * 收到 {@code null} 说明是编程错误，不是业务输入问题。
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
}

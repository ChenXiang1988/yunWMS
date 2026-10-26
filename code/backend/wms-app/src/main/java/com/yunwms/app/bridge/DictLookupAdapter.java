package com.yunwms.app.bridge;

import com.yunwms.auth.domain.model.DictItem;
import com.yunwms.auth.domain.model.DictStatus;
import com.yunwms.auth.domain.port.DictQuery;
import com.yunwms.master.domain.port.DictLookup;
import org.springframework.stereotype.Component;

/**
 * 把 {@code wms-master} 的字典窄端口接到 {@code wms-auth} 的字典查询上。
 *
 * <p><b>为什么这个类在 {@code wms-app}：</b>字典能力当前落在 {@code wms-auth}，而
 * {@code auth} 与 {@code master} 互不引用（{@code docs/coding-project.md} 第二节，由
 * {@code ModuleBoundaryTest} 拦截）。只有 {@code wms-app} 的运行期 classpath 同时包含两边，
 * 适配器放在任一业务模块里另一个模块都不可见。
 *
 * <p><b>为什么要额外过滤，而不是让字典侧提供一个「按 value 查单项」的接口：</b>那需要改
 * {@code wms-auth}（本批不允许），而字典的规模本来就小——最大的 {@code PROVINCE} 只有 82 项，
 * 且按国家过滤后一次最多 51 项；{@code COUNTRY} 28 项、{@code OWNER_TYPE} 2 项。建档是一次
 * 用户点击触发的单次操作，不是批量导入的热路径，多搬几十行的代价远小于给两个模块再加一条契约。
 * 若将来出现按字典批量校验的入口（导入），应由那时的使用方重新评估，而不是现在预造一个批量接口。
 *
 * <p>本类只做翻译：不缓存字典（缓存会让「管理员停用了某一项」在多长时间后生效变成一个说不清的问题），
 * 不在本地兜底默认值（字典里没有就是没有）。
 */
@Component
public class DictLookupAdapter implements DictLookup {

    /** 行政区划的字典类型编码，与 {@code V032__seed_dictionaries.sql} 的 {@code dict_type} 一致。 */
    private static final String PROVINCE_DICT = "PROVINCE";

    private final DictQuery dictQuery;

    public DictLookupAdapter(DictQuery dictQuery) {
        this.dictQuery = dictQuery;
    }

    /**
     * {@inheritDoc}
     *
     * <p>只查启用项：停用项对新建数据而言等于不存在。字典类型不存在时字典侧返回空列表，
     * 于是这里得到 {@code false}——「类型没种进来」与「该项已被删除」对调用方是同一个结论：
     * 这个值现在不能用。
     */
    @Override
    public boolean isActiveItem(String dictType, String value) {
        requireText(dictType, "字典类型编码");
        requireText(value, "字典项取值");
        return dictQuery.findItems(dictType, DictStatus.ACTIVE, null).stream()
                .map(DictItem::value)
                .anyMatch(value::equals);
    }

    /**
     * {@inheritDoc}
     *
     * <p>层级用字典项的 {@code ext.parent} 表示，由字典侧按 {@code parentValue} 过滤后返回本国的项；
     * 本类不解析 {@code ext}，也不假设省码以国家码开头——那只是当前种子数据的写法，不是字典的规则。
     */
    @Override
    public boolean isProvinceOfCountry(String provinceValue, String countryValue) {
        requireText(provinceValue, "州省取值");
        requireText(countryValue, "国家取值");
        return dictQuery.findItems(PROVINCE_DICT, DictStatus.ACTIVE, countryValue).stream()
                .map(DictItem::value)
                .anyMatch(provinceValue::equals);
    }

    /**
     * 拒绝空参数。
     *
     * <p>不用「空就当查不到」兜底：那会让一次调用方 bug 表现为「字典里没有这一项」，
     * 排查时看到的是业务数据问题，实际是代码问题。参数为空属于编程错误，直接失败。
     *
     * @param text  待判定的参数
     * @param label 参数的中文名，用于错误信息定位
     * @throws IllegalArgumentException 参数为 {@code null} 或空白
     */
    private static void requireText(String text, String label) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }
}

package com.yunwms.app.bridge;

import com.yunwms.auth.domain.model.DictItem;
import com.yunwms.auth.domain.model.DictStatus;
import com.yunwms.auth.domain.port.DictQuery;
import com.yunwms.master.domain.port.DictLookup;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * 把 {@code wms-master} 的字典窄端口接到 {@code wms-auth} 的字典查询上。
 *
 * <p><b>为什么这个类在 {@code wms-app}：</b>字典能力当前落在 {@code wms-auth}，而
 * {@code auth} 与 {@code master} 互不引用（{@code docs/coding-project.md} 第二节，由
 * {@code ModuleBoundaryTest} 拦截）。只有 {@code wms-app} 的运行期 classpath 同时包含两边，
 * 适配器放在任一业务模块里另一个模块都不可见。
 *
 * <p><b>为什么要额外过滤，而不是让字典侧提供一个「按 value 查单项」的接口：</b>那需要改
 * {@code wms-auth}（本批不允许），而字典的规模本来就小——最大的 {@code PROVINCE} 只有 85 项，
 * 且判定州省归属时按国家过滤后一次最多 51 项；{@code COUNTRY} 28 项、{@code OWNER_TYPE} 2 项。
 * 建档是一次用户点击触发的单次操作，不是批量导入的热路径，多搬几十行的代价远小于给两个模块
 * 再加一条契约。若将来出现按字典批量校验的入口（导入），应由那时的使用方重新评估，
 * 而不是现在预造一个批量接口。
 *
 * <p>本类只做翻译：不缓存字典（缓存会让「管理员停用了某一项」在多长时间后生效变成一个说不清的问题），
 * 不在本地兜底默认值（字典里没有就是没有）。例外只有两处，都是端口契约里写明的约定、不是本类擅自
 * 补的默认值：{@code ext.postalCodeRequired} 缺失时按 {@code true} 处理；{@code ext.countries}
 * 缺失时按不限国家处理。
 */
@Component
public class DictLookupAdapter implements DictLookup {

    /** 行政区划的字典类型编码，与 {@code V032__seed_dictionaries.sql} 的 {@code dict_type} 一致。 */
    private static final String PROVINCE_DICT = "PROVINCE";

    /**
     * 字典项 {@code ext} 里表示「该地区要求填写邮政编码」的键。
     *
     * <p>键名与 {@code V034} 写入的一致。只有无邮编的地区（港澳台）写 {@code false}，
     * 其余地区不写——缺失即必填，这样新增地区时不会因为忘记写这个键而放宽校验。
     */
    private static final String POSTAL_CODE_REQUIRED_KEY = "postalCodeRequired";

    /**
     * 字典项 {@code ext} 里列出「适用国家」的键。
     *
     * <p>键名与 {@code V042} 写入的 {@code TAX_ID_TYPE} 项一致。缺该键表示不限国家，
     * 因此新增一个国家专用类型时必须写这个键——漏写会让它放开到所有国家，见
     * {@link #appliesToCountry} 对类型写坏的说明。
     */
    private static final String COUNTRIES_KEY = "countries";

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
     * {@inheritDoc}
     *
     * <p>取值来自字典项的 {@code ext.postalCodeRequired}。空州省直接返回 {@code true} 而不查库：
     * {@code parentValue = null} 在字典侧表示「不按层级筛选」，拿空值去查会把整个 {@code PROVINCE}
     * 字典搬回来，而结论与查不到该项相同（默认必填）。
     *
     * <p>这里不按国家过滤：已经知道州省取值，不知道它属于哪个国家，而省码以国家码开头只是当前
     * 种子数据的写法，不是字典的规则——按前缀猜国家会让一条数据格式约定变成业务判据。
     */
    @Override
    public boolean isPostalCodeRequired(String provinceValue) {
        if (provinceValue == null || provinceValue.isBlank()) {
            return true;
        }
        return dictQuery.findItems(PROVINCE_DICT, DictStatus.ACTIVE, null).stream()
                .filter(item -> provinceValue.equals(item.value()))
                .findFirst()
                .map(DictLookupAdapter::postalCodeRequiredOf)
                .orElse(true);
    }

    /**
     * {@inheritDoc}
     *
     * <p>适用国家取字典项的 {@code ext.countries}。只查启用项，理由同 {@link #isActiveItem}：
     * 停用项对新建数据而言等于不存在。整类项一次取回再在内存里找，与另外三个方法一致——
     * {@code TAX_ID_TYPE} 只有四项，为「按 value 查单项」再加一条字典侧契约不划算。
     */
    @Override
    public boolean isItemApplicableTo(String dictType, String value, String countryValue) {
        requireText(dictType, "字典类型编码");
        requireText(value, "字典项取值");
        requireText(countryValue, "国家取值");
        return dictQuery.findItems(dictType, DictStatus.ACTIVE, null).stream()
                .filter(item -> value.equals(item.value()))
                .findFirst()
                .map(item -> appliesToCountry(item, countryValue))
                .orElse(false);
    }

    /**
     * 按字典项声明的国家清单判断它是否适用于某国家。
     *
     * <p>缺键、值为 {@code null}、或清单为空都表示不限国家：判据是「有没有列出国家」，
     * 而不是清单长度。种子数据里的 {@code VAT} 写的就是空对象——它跨多国，写死一份清单
     * 等于给自己造一个必然过期的第二真源。
     *
     * <p>值不是集合（如把 {@code ["CN"]} 误写成 {@code "CN"}）时判为<b>不适用</b>：当成不限国家
     * 会把这个类型放开到所有国家，与规格的「税号类型与国家匹配」相反；判为不适用则用户提交时
     * 会看到「类型与国家不匹配」，问题当场暴露。这个分支只有手工改库才能进入，种子数据不会产生它。
     *
     * @param item         字典项
     * @param countryValue 国家取值，已由调用方判过非空白
     * @return 该项适用于该国家时返回 {@code true}
     */
    private static boolean appliesToCountry(DictItem item, String countryValue) {
        Object countries = item.ext().get(COUNTRIES_KEY);
        if (countries == null) {
            return true;
        }
        if (!(countries instanceof Collection<?> declaredCountries)) {
            return false;
        }
        return declaredCountries.isEmpty()
                || declaredCountries.stream().anyMatch(countryValue::equals);
    }

    /**
     * 从字典项的扩展属性里取出邮编必填标记。
     *
     * <p>只有 {@code Boolean.FALSE} 才算豁免：键缺失、值为 {@code null}、或有人把 {@code false}
     * 写成字符串 {@code "false"}，一律按必填处理。这个方向的错误是「多要求一个字段」，用户提交时
     * 会看到提示；反方向的错误是「少要求一个字段」，它会一路静默落库，直到某天地址寄不出去。
     *
     * @param item 字典项
     * @return 该地区要求填写邮政编码时返回 {@code true}
     */
    private static boolean postalCodeRequiredOf(DictItem item) {
        return !Boolean.FALSE.equals(item.ext().get(POSTAL_CODE_REQUIRED_KEY));
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

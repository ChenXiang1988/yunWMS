package com.yunwms.auth.application.query;

import com.yunwms.auth.domain.model.DictItem;
import com.yunwms.auth.domain.model.DictStatus;
import com.yunwms.auth.domain.model.DictTypeSummary;
import com.yunwms.auth.domain.port.DictQuery;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 字典的读取用例：类型清单、某类字典项、多类型批量读取。
 *
 * <p>本类只做三件事：把 {@code enabledOnly} 翻译成状态筛选、把空白 {@code parentValue} 归一成
 * 不筛选、把「类型不存在」转成 404。查询与聚合全部下沉到 {@link DictQuery}。
 *
 * <p><b>一个开关承载两种语义</b>：{@code enabledOnly} 为 {@code true}（默认）时只取启用项，
 * 供给下拉；为 {@code false} 时连停用项一起取，供给历史数据回显。故意不拆成两个端点：
 * 编辑页往往同时需要一个「只有启用项的下拉」和一个「可能指向停用项的历史值」，拆开后同一页要调两次，
 * 而两次调用的时间点不同，还会出现「下拉里没有、历史值里没有」这种自相矛盾的中间态。
 *
 * <p><b>不回退语言</b>：本类不判断界面语言，也不在 {@code labelEn} 为空时填 {@code label}。
 * 回退规则属于展示层，放在这里会让「英文界面看到中文」这件事从「数据缺英文名」变成「代码某处回退过」，
 * 无法排查。
 */
@Service
public class DictQueryService {

    private final DictQuery dictQuery;

    public DictQueryService(DictQuery dictQuery) {
        this.dictQuery = dictQuery;
    }

    /**
     * 取全部字典类型及其项数。
     *
     * <p>不筛选状态：字典管理页要能看到已停用的类型，否则停用之后就再也找不到它、无法重新启用。
     *
     * @return 字典类型，按类型编码升序
     */
    @Transactional(readOnly = true)
    public List<DictTypeSummary> listTypes() {
        return dictQuery.findAllTypes();
    }

    /**
     * 取某类字典项。
     *
     * <p>先判类型是否存在再取项：项查询按 {@code dict_type} 过滤，类型不存在时天然返回空列表，
     * 但「这个类型没有项」与「根本没有这个类型」对调用方是两件事——前者是数据没配好，
     * 后者是前端拿了过期的类型常量。把后者转成 404，前端才有一个可展示的错误。
     *
     * @param dictType    类型编码，按库里的值精确匹配（不做裁剪：字典类型是程序常量，
     *                    带空格的入参不可能是任何一个真实类型）
     * @param enabledOnly {@code true} 只取启用项（下拉），{@code false} 含停用项（历史回显）
     * @param parentValue 只取 {@code ext.parent} 等于该值的项；{@code null} 或全空白表示不筛选
     * @return 字典项，按排序号、标识升序
     * @throws BizException 字典类型不存在或已删除（40400）
     */
    @Transactional(readOnly = true)
    public List<DictItem> items(String dictType, boolean enabledOnly, String parentValue) {
        if (!dictQuery.existsType(dictType)) {
            throw BizException.notFound("字典类型不存在");
        }
        return dictQuery.findItems(dictType, statusFilter(enabledOnly), stripToNull(parentValue));
    }

    /**
     * 一次取回多个类型的字典项，按类型分组。
     *
     * <p>不存在的类型静默跳过：入参里的类型可能来自管理员刚删掉的配置，为此报错会让整页挂掉，
     * 而正确行为是「有就返回、没有就少一个键」。由此有一条调用方必须知道的后果：
     * 结果里出现某个键是「该类型下确实取到了项」的充分必要条件，
     * 因此「类型不存在」「类型下没有任何项」「项全部被状态筛掉」三种情况都表现为不出现该键。
     *
     * <p>类型编码先去除空白并去重再下发：重复的类型会把 {@code IN} 列表撑大，
     * 而 SQL 里同一个条件写两次不会有任何额外过滤效果。
     *
     * @param types       类型编码集合，可含空白项
     * @param enabledOnly {@code true} 只取启用项，{@code false} 含停用项
     * @return 类型编码到字典项的映射，同一类型内按排序号、标识升序；归一后没有类型时为空 Map 且不查库
     */
    @Transactional(readOnly = true)
    public Map<String, List<DictItem>> batch(Collection<String> types, boolean enabledOnly) {
        List<String> normalizedTypes = normalizeTypes(types);
        if (normalizedTypes.isEmpty()) {
            // 不查库：空的 IN 条件在拼装时会被省略，查询会退化成「查全部字典项」
            return Map.of();
        }

        Map<String, List<DictItem>> grouped = new LinkedHashMap<>();
        for (DictItem item : dictQuery.findItemsByTypes(normalizedTypes, statusFilter(enabledOnly))) {
            // 单次查询已按 (类型, 排序号, 标识) 排序，顺序分组即可保持每个类型内部的顺序
            grouped.computeIfAbsent(item.dictType(), key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    /**
     * 把「是否只要启用项」翻译成状态筛选条件。
     *
     * <p>这是本类唯一的业务判断：它必须在一个地方完成，两处各写一遍必然有一处写成取反。
     *
     * @param enabledOnly {@code true} 只要启用项
     * @return 启用时返回 {@link DictStatus#ACTIVE}，否则返回 {@code null}（不限状态）
     */
    private static DictStatus statusFilter(boolean enabledOnly) {
        return enabledOnly ? DictStatus.ACTIVE : null;
    }

    /**
     * 归一类型编码集合：去掉 {@code null}、裁剪空白、丢弃空串、去重。
     *
     * @param types 原始入参，可为 {@code null}
     * @return 可直接用于 {@code IN} 条件的类型编码；没有有效值时为空列表
     */
    private static List<String> normalizeTypes(Collection<String> types) {
        if (types == null || types.isEmpty()) {
            return List.of();
        }
        // LinkedHashSet 同时完成去重与保序：用 List 判重是 O(n²)，而类型数由调用方决定，不该有这种隐含上限
        Set<String> normalized = new LinkedHashSet<>(types.size());
        for (String type : types) {
            String stripped = stripToNull(type);
            if (stripped != null) {
                normalized.add(stripped);
            }
        }
        return List.copyOf(normalized);
    }

    /**
     * 去掉两侧空白，空串按未填处理。
     *
     * <p>不归一的话，一个只有空格的 {@code parentValue} 会变成
     * {@code ext ->> 'parent' = ' '}，匹配不到任何项，而调用方的本意是「不按层级筛选」。
     *
     * @param value 原始值，可为 {@code null}
     * @return 裁剪后的值；{@code null}、空串或全空白时为 {@code null}
     */
    private static String stripToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}

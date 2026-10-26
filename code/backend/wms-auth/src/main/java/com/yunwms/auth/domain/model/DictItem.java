package com.yunwms.auth.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一个字典项的读模型。
 *
 * <p>{@code label} 与 {@code labelEn} 原样交出，<b>不在后端做语言回退</b>：接口不知道调用方
 * 用的是哪个界面语言，也不知道该回退到哪一个。在这里补一次「{@code labelEn} 为空就用 {@code label}」，
 * 前端就再也分不清「英文名缺失」与「英文名与中文名恰好相同」，而前者正是要修的数据问题。
 *
 * <p>{@code ext} 是使用方的扩展属性，字典本身不解释它的业务含义，只做透传。列可为
 * {@code NULL}，这里归一成空 Map：前端取值不必先判空，而「没有扩展属性」与
 * 「扩展属性是空对象」对使用方没有区别。
 *
 * <p>{@code dictType} 放在项里是为了批量读取能按类型分组，不必在端口层另造一个
 * {@code Map<类型, 项[]>} 的返回结构。
 *
 * @param id       项标识，接口层以字符串形式返回（超过 2^53 的整数在 JS 里会丢精度）
 * @param dictType 所属类型编码
 * @param label    中文名
 * @param labelEn  英文名，可为 {@code null}（由前端按当前语言决定是否回退）
 * @param value    取值，业务数据存的就是它；同一类型下唯一
 * @param sortNo   排序号，越小越靠前
 * @param status   项状态；停用项不出现在下拉里，但历史数据仍按它展示
 * @param ext      扩展属性，可为空 Map；需要层级的字典在 {@code ext.parent} 里写父项的 value
 */
public record DictItem(long id,
                       String dictType,
                       String label,
                       String labelEn,
                       String value,
                       int sortNo,
                       DictStatus status,
                       Map<String, Object> ext) {

    /**
     * 归一扩展属性，使 {@code ext} 恒不为 {@code null}、且不受调用方后续修改影响。
     *
     * <p>拷贝成不可变的 {@link LinkedHashMap} 而不是 {@code Map.copyOf}：后者不接受
     * {@code null} 值，而 {@code ext} 来自 JSON，里面完全可能写着 {@code {"parent": null}}，
     * 那会让一次合法读取抛 {@code NullPointerException}。保留插入顺序则是为了让响应里的
     * 键序与库里的书写顺序一致，便于人工比对。
     */
    public DictItem {
        ext = ext == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(ext));
    }
}

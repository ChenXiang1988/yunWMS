package com.yunwms.auth.interfaces.controller.assembler;

import com.yunwms.auth.domain.model.DictItem;
import com.yunwms.auth.domain.model.DictTypeSummary;
import com.yunwms.auth.interfaces.controller.response.CreatedDictItemResponse;
import com.yunwms.auth.interfaces.controller.response.DictItemResponse;
import com.yunwms.auth.interfaces.controller.response.DictTypeListItemResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字典读模型到响应体的转换。
 *
 * <p>转换集中在这里，是因为「标识以字符串传输」「状态以枚举名传输」「{@code ext} 原样透传」都属于
 * 接口层约定，不属于用例：用例只认数字标识与领域枚举，是否序列化成字符串由契约决定。
 *
 * <p>这里<b>不做</b>语言回退：{@code labelEn} 为空就返回 {@code null}。回退规则属于展示层
 * （见 {@link DictItemResponse}）。
 */
@Component
public class DictResponseAssembler {

    /**
     * 组装新增字典项的响应。
     *
     * <p>数字标识到字符串的转换放在这里而不是 Controller 内联：标识的传输形态是接口层约定，
     * 与其余响应的处理保持一致，将来契约改成别的形式时只有一处要改。
     *
     * @param id 数据库生成的字典项标识
     * @return 可直接序列化的响应体
     */
    public CreatedDictItemResponse toCreatedResponse(long id) {
        return new CreatedDictItemResponse(Long.toString(id));
    }

    /**
     * 组装字典类型列表。
     *
     * @param types 字典类型读模型
     * @return 响应体列表，顺序与入参一致
     */
    public List<DictTypeListItemResponse> toTypeListResponse(List<DictTypeSummary> types) {
        return types.stream()
                .map(type -> new DictTypeListItemResponse(
                        type.dictType(),
                        type.name(),
                        type.nameEn(),
                        type.itemCount(),
                        type.status().name()))
                .toList();
    }

    /**
     * 组装某类字典项列表。
     *
     * @param items 字典项读模型
     * @return 响应体列表，顺序与入参一致
     */
    public List<DictItemResponse> toItemListResponse(List<DictItem> items) {
        return items.stream()
                .map(DictResponseAssembler::toItemResponse)
                .toList();
    }

    /**
     * 组装批量读取的分组响应。
     *
     * <p>用 {@link LinkedHashMap} 保留入参的顺序：分组顺序来自一次有序查询，
     * 保留它可以让同一批请求的响应在两次调用之间逐字节一致，便于前端缓存与人工比对。
     *
     * @param itemsByType 类型编码到字典项的分组
     * @return 类型编码到响应体列表的映射，键集合与入参一致
     */
    public Map<String, List<DictItemResponse>> toBatchResponse(Map<String, List<DictItem>> itemsByType) {
        Map<String, List<DictItemResponse>> response = new LinkedHashMap<>();
        itemsByType.forEach((dictType, items) -> response.put(dictType, toItemListResponse(items)));
        return response;
    }

    /**
     * 组装单个字典项。
     *
     * @param item 字典项读模型
     * @return 响应体
     */
    static DictItemResponse toItemResponse(DictItem item) {
        return new DictItemResponse(
                Long.toString(item.id()),
                item.label(),
                item.labelEn(),
                item.value(),
                item.sortNo(),
                item.status().name(),
                item.ext());
    }
}

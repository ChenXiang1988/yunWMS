package com.yunwms.auth.infrastructure.persistence.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.auth.domain.model.DictItem;
import com.yunwms.auth.domain.model.DictStatus;
import com.yunwms.auth.domain.model.DictTypeSummary;
import com.yunwms.auth.domain.port.DictQuery;
import com.yunwms.auth.infrastructure.persistence.entity.DictItemRow;
import com.yunwms.auth.infrastructure.persistence.entity.DictTypeRow;
import com.yunwms.auth.infrastructure.persistence.mapper.SysDictMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * {@link DictQuery} 的 MyBatis 实现。
 *
 * <p>每条用例固定一条 SQL：类型清单的项数在语句里聚合，多类型读取用一个 {@code IN}，
 * 没有逐类型查询，所以 SQL 条数与字典类型的数量无关。
 *
 * <p>{@code ext} 的 JSON 解析放在这里而不是 SQL 或响应组装处：{@code jsonb} 经 JDBC 以文本返回，
 * 解析它属于持久化实现细节；放在持久化层之外，领域模型与接口层就都要认识「库里存的是 JSON 文本」。
 *
 * <p>状态列的中文说明不做在这里：{@link DictStatus#fromCode} 遇到未知编码直接抛异常。
 * 这是刻意的——库里出现 {@code 'FOO'} 说明数据被绕过 CHECK 写坏，静默回落到「启用」
 * 会让一个本该停用的字典项重新出现在下拉里。
 */
@Repository
public class DictQueryImpl implements DictQuery {

    /** {@code ext} 的形状：任意字符串键到任意 JSON 值。字典本身不解释它的业务含义。 */
    private static final TypeReference<Map<String, Object>> EXT_TYPE = new TypeReference<>() {
    };

    private final SysDictMapper sysDictMapper;
    private final ObjectMapper objectMapper;

    public DictQueryImpl(SysDictMapper sysDictMapper, ObjectMapper objectMapper) {
        this.sysDictMapper = sysDictMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<DictTypeSummary> findAllTypes() {
        return sysDictMapper.selectTypeSummaries().stream()
                .map(DictQueryImpl::toTypeSummary)
                .toList();
    }

    @Override
    public boolean existsType(String dictType) {
        return sysDictMapper.existsType(dictType);
    }

    @Override
    public List<DictItem> findItems(String dictType, DictStatus status, String parentValue) {
        return sysDictMapper.selectItems(dictType, status == null ? null : status.name(), parentValue)
                .stream()
                .map(this::toItem)
                .toList();
    }

    @Override
    public List<DictItem> findItemsByTypes(Collection<String> dictTypes, DictStatus status) {
        if (dictTypes.isEmpty()) {
            // 空的 IN 条件在拼装时会被省略，查询会退化成「查全部字典项」；
            // 这里必须提前短路，否则一次空请求会把整张表捞回来
            return List.of();
        }
        return sysDictMapper.selectItemsByTypes(dictTypes, status == null ? null : status.name())
                .stream()
                .map(this::toItem)
                .toList();
    }

    /**
     * 把类型行转成读模型。
     *
     * @param row 数据库返回的行
     * @return 类型读模型
     * @throws IllegalArgumentException 库里的状态编码未知，说明数据已损坏
     */
    private static DictTypeSummary toTypeSummary(DictTypeRow row) {
        return new DictTypeSummary(
                row.getDictType(),
                row.getName(),
                row.getNameEn(),
                row.getItemCount() == null ? 0L : row.getItemCount(),
                DictStatus.fromCode(row.getStatus()));
    }

    /**
     * 把项行转成读模型，并解析 {@code ext}。
     *
     * @param row 数据库返回的行
     * @return 字典项读模型
     * @throws IllegalStateException {@code ext} 不是合法的 JSON 对象，说明数据已损坏
     * @throws IllegalArgumentException 库里的状态编码未知，说明数据已损坏
     */
    private DictItem toItem(DictItemRow row) {
        return new DictItem(
                row.getId(),
                row.getDictType(),
                row.getLabel(),
                row.getLabelEn(),
                row.getValue(),
                row.getSortNo() == null ? 0 : row.getSortNo(),
                DictStatus.fromCode(row.getStatus()),
                parseExt(row));
    }

    /**
     * 解析 {@code ext} 列。
     *
     * <p>失败时带上项标识再抛：SQL 只回一个 JSON 文本，没有标识的话「哪一条字典项的 ext 写坏了」
     * 只能靠人翻库。原始异常保留为 cause，堆栈里仍有 Jackson 的定位信息。
     *
     * @param row 数据库返回的行
     * @return 扩展属性；列为 {@code NULL} 时为空 Map
     * @throws IllegalStateException {@code ext} 不是合法的 JSON 对象
     */
    private Map<String, Object> parseExt(DictItemRow row) {
        String ext = row.getExt();
        if (ext == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(ext, EXT_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("字典项 ext 不是合法的 JSON 对象：id=" + row.getId(), ex);
        }
    }
}

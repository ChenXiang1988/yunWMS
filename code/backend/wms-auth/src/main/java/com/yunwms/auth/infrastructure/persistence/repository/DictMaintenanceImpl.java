package com.yunwms.auth.infrastructure.persistence.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.auth.domain.model.DictItem;
import com.yunwms.auth.domain.model.DictStatus;
import com.yunwms.auth.domain.port.DictMaintenance;
import com.yunwms.auth.infrastructure.persistence.entity.DictItemRow;
import com.yunwms.auth.infrastructure.persistence.mapper.SysDictMapper;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;

/**
 * {@link DictMaintenance} 的 MyBatis 实现。
 *
 * <p><b>写侧全部走 XML，与读侧同一个 Mapper：</b>四条写语句都需要 MyBatis-Plus 的 Wrapper 表达不出来的
 * 东西——新增要拿回 {@code GENERATED ALWAYS AS IDENTITY} 生成的主键，编辑要把 {@code labelEn} 与
 * {@code ext} 写成 {@code NULL} / 空对象（{@code updateById} 按「非空字段才进 SET」生成语句，
 * 「清空英文名」会变成一次静默的空操作），三条写语句还都要同时写 {@code updated_at = now()} 与
 * {@code updated_by}。手写 SQL 拿不到逻辑删除配置的补偿，因此每条语句自己带
 * {@code deleted_at IS NULL}。
 *
 * <p><b>不继承 {@code BaseMapper}、不建写实体：</b>继承会白送 {@code deleteById} 这类本模块不该有的能力
 * （字典项只允许逻辑删除）。{@code DictItemRow} 的类注释预期本批会另建与表一一对应的写实体，实际没有建：
 * 唯一的写入是一条 INSERT，{@code RETURNING id} 已经解决了主键回填，而一个带 setter 的行对象会多出一条
 * 绕过 {@link DictItemDraft} 校验的路径，也为「这张表怎么写」提前押一个没人验证过的形状。用
 * {@code <select>} 承接带 {@code RETURNING} 的 INSERT 是 MyBatis 的既定手法，理由见
 * {@code SysDictMapper.xml}（与 {@code SysCodeRuleMapper#advance} 同一处理）。
 *
 * <p><b>{@code ext} 的 JSON 处理集中在本类：</b>写入时用 {@link ObjectMapper} 把 {@code Map} 序列化成文本、
 * 由 SQL 里的 {@code CAST(... AS jsonb)} 落到列上；读取时反向解析。列存的是文本这件事因此不外泄到
 * 领域模型与接口层（与 {@code DictQueryImpl} 同一口径）。
 */
@Repository
public class DictMaintenanceImpl implements DictMaintenance {

    /** {@code ext} 的形状：任意字符串键到任意 JSON 值。字典本身不解释它的业务含义。 */
    private static final TypeReference<Map<String, Object>> EXT_TYPE = new TypeReference<>() {
    };

    private final SysDictMapper sysDictMapper;
    private final ObjectMapper objectMapper;

    public DictMaintenanceImpl(SysDictMapper sysDictMapper, ObjectMapper objectMapper) {
        this.sysDictMapper = sysDictMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>主键回填依赖 {@code RETURNING id}；拿不到主键时当场失败，不返回 0 或 -1 充当标识——
     * 那会让调用方拿着一个不存在的 id 继续往下走，错误要到更远的链路才暴露
     * （与 {@code OwnerRepositoryImpl#insert} 同一处理）。
     *
     * <p>不在这里判「该类型下是否已有同值的未删除项」：{@code ux_sys_dict_item_value} 是唯一权威，
     * 端口连这条查询都没提供，也就没有「顺手替你查一下」的能力。
     */
    @Override
    public long insert(String dictType, String value, DictItemDraft draft, long operatorId) {
        Long id = sysDictMapper.insertItem(
                dictType,
                value,
                draft.label(),
                draft.labelEn(),
                draft.sortNo(),
                toJson(draft.ext(), "dict_type=" + dictType + ", value=" + value),
                operatorId);
        if (id == null) {
            throw new IllegalStateException("字典项写入后未取回主键：dict_type=" + dictType + ", value=" + value);
        }
        return id;
    }

    /**
     * {@inheritDoc}
     *
     * <p>影响行数不参与判定：调用方已经读过这一行，命中零行只可能是它在两次调用之间被删除。
     * 那不是需要纠正的失败（{@code docs/coding.md}「持久化与副作用」明确禁止依据影响行数判成功或冲突）。
     */
    @Override
    public void update(long id, DictItemDraft draft, long operatorId) {
        sysDictMapper.updateItem(
                id,
                draft.label(),
                draft.labelEn(),
                draft.sortNo(),
                toJson(draft.ext(), "id=" + id),
                operatorId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>也不看影响行数：把已停用的项再停用一次是重复点击的正常结果，不是冲突。
     */
    @Override
    public void changeStatus(long id, DictStatus status, long operatorId) {
        sysDictMapper.updateStatus(id, status.name(), operatorId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>这里必须看影响行数：删除是「这条记录从此不该再存在」的断言，而调用方刚读过它。命中零行只剩
     * 一种解释——它在两次调用之间被另一个请求删掉了，调用方要据此回 404。
     */
    @Override
    public boolean softDelete(long id, long operatorId) {
        return sysDictMapper.softDelete(id, operatorId) > 0;
    }

    /**
     * {@inheritDoc}
     *
     * <p>SQL 自带 {@code deleted_at IS NULL}：已删除的行在这里读不到，与「不存在」同口径。
     */
    @Override
    public Optional<DictItem> findById(long id) {
        return Optional.ofNullable(sysDictMapper.selectItemById(id))
                .map(this::toItem);
    }

    /**
     * 把 {@code ext} 序列化成 {@code jsonb} 列的文本输入。
     *
     * <p>{@code ext} 来自 {@link DictItemDraft}，{@code null} 已被归一成空 Map，因此这里写出的永远是
     * 一个合法 JSON 对象而不是 {@code NULL}：读写两侧对「没有扩展属性」的表达因此只有一个（{@code {}}），
     * 不会出现「库里一半是 NULL、一半是 {}」这种要靠人记住的差异。
     *
     * <p>失败时带上定位信息再抛：序列化只有在调用方塞进了非 JSON 值（例如自己造的对象）时才会失败，
     * 那属于编程错误；原始异常保留为 cause，堆栈里仍有 Jackson 的定位信息。
     *
     * @param ext     扩展属性，已由 {@link DictItemDraft} 归一并拷贝成不可变 Map
     * @param context 出错时用于定位的上下文，如 {@code id=12}
     * @return JSON 对象文本
     * @throws IllegalStateException {@code ext} 无法序列化成 JSON
     */
    private String toJson(Map<String, Object> ext, String context) {
        try {
            return objectMapper.writeValueAsString(ext);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("字典项 ext 无法序列化为 JSON：" + context, ex);
        }
    }

    /**
     * 把一行搬成读模型，并解析 {@code ext}。
     *
     * <p>这段转换与 {@code DictQueryImpl#toItem} 现在是同一份逻辑，刻意没有合并：合并要动 {@code DictQueryImpl}，
     * 而读侧在本批被冻结（那一次的验证只覆盖读）。代价是两处要一起改，收益是这一批的改动面不越过
     * 「字典项维护」——两者的合并留给读侧下一次真正要改动的时候做，届时有测试覆盖两边。
     *
     * @param row 数据库返回的行
     * @return 字典项读模型
     * @throws IllegalStateException    {@code ext} 不是合法的 JSON 对象，说明数据已损坏
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
     * 只能靠人翻库（与 {@code DictQueryImpl#parseExt} 同一处理，重复的原因见 {@link #toItem}）。
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

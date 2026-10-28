package com.yunwms.auth.domain.port;

import com.yunwms.auth.domain.model.DictItem;
import com.yunwms.auth.domain.model.DictStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 字典项维护的写入入口：新增、编辑、启停、逻辑删除，外加一条供用例判存在与取变更前快照的读取。
 *
 * <p><b>为什么与 {@link DictQuery} 分成两个端口：</b>读侧不需要事务语义、不写审计列，也不需要
 * 「这一行还在不在」的回执；写侧三样都要。混在一个端口里，「这个方法会不会改数据」就成了必须读实现
 * 才能回答的问题。{@link DictQuery} 的类注释已经把这条分工写在前头，本端口是它的另一半。
 *
 * <p><b>编辑收不到 {@code dictType} 与 {@code value}：</b>这两个是业务数据的引用键——
 * {@code owner.owner_type}、{@code owner_contact.role_type} 里存的就是 {@code value}。改掉它等于让
 * 历史数据指向一个不存在的项，且这种损坏在界面上看不出来（记录还在，只是显示不出名字）。因此
 * {@link #update} 只接收 {@link DictItemDraft}，它连这两个字段都没有；靠「记得别写 SET」的注释约束
 * 不住下一次顺手加字段，能编译拦住的才可靠（与 {@code OwnerContactDraft} 不带 {@code ownerId} 同一处理）。
 *
 * <p><b>删除是逻辑删除，不是物理删除：</b>置 {@code deleted_at}。{@code value} 可能被历史业务数据引用，
 * 物理删掉之后「这条记录当时写的是哪个 value」就无从追溯——那正是字典存在的意义。
 *
 * <p><b>删除之后同值可以重建：</b>{@code V018} 的唯一索引是
 * {@code CREATE UNIQUE INDEX ux_sys_dict_item_value ON sys_dict_item (dict_type, value) WHERE deleted_at IS NULL}
 * （部分唯一索引，带 {@code deleted_at IS NULL} 条件）。已删除的行不再参与唯一性判定，因此同一类型下
 * 可以重新新增一个同值的项；新建的是另一行 {@code id}，历史数据引用的仍是那个 value，两边对得上。
 * 若哪天该索引改成不带条件的全量索引，这条路径会撞唯一键——届时必须在迁移与本文档里同时改口径。
 *
 * <p><b>启停只改 {@code status}，与删除是两件事：</b>停用是「不再被新数据引用，历史数据照常展示」，
 * 行还在、还能重新启用；删除是「这条记录不该再出现」。把停用做成删除，会让管理员清理下拉时
 * 顺手毁掉历史数据的可解释性。
 *
 * <p><b>{@code sort_no} 冲突不拦：</b>库里没有唯一约束，两行同号不是数据错误——读侧按
 * {@code (sort_no, id)} 排序，同号时由 {@code id} 兜住，顺序仍然确定，只是「哪一个在前」由插入先后
 * 决定。为此加一道应用层判重，会让「调换两个项的排序」这种正常操作必须分两步完成（先把 A 改成 3，
 * 再把 B 改成 2），而那个中间态本来就不是错误。
 *
 * <p><b>本端口不翻译数据库约束违反，也不判 {@code dictType} 是否存在：</b>类型不存在由
 * {@code sys_dict_item.dict_type} 的外键拦下，同值重复由 {@code ux_sys_dict_item_value} 拦下，
 * 它们都是 {@link RuntimeException}，由用例翻译成业务错误码（与货主、联系人的写端口同一处理）。
 * 在仓储里再判一次，规则就有了两个落点，而两个落点对「已删除的同类项算不算占用」迟早会判得不一样。
 *
 * <p><b>入口校验不在本端口：</b>「必填」「超长」这类用户可见的错误由 Controller 的声明式校验给出，
 * 本端口只负责把调用方交来的值写下去。{@link DictItemDraft} 里那几项检查是最后一道防线，拦的是
 * 绕过用例直接构造（将来的批量导入、数据修复脚本），属类型不变量而不是重复的入口校验；
 * {@code dictType} 与 {@code value} 连这道防线都没有，由外键与列约束兜底。
 */
public interface DictMaintenance {

    /**
     * 新增一个字典项，返回数据库生成的标识。
     *
     * <p><b>为什么 {@code value} 是本方法的参数而不是 {@link DictItemDraft} 的字段：</b>{@code value}
     * 是 {@code NOT NULL} 且属于 {@code (dict_type, value)} 唯一索引，新增时必须给出；但它创建后不可改，
     * 放进 draft 就等于让 {@link #update} 也收到一个改不动的字段。只读的键放在 draft 之外，与
     * {@code OwnerRepository#insert(NewOwner, String code, ...)} 把只读的 {@code code} 放在
     * {@code NewOwner} 之外是同一处理。
     *
     * <p>新增项一律为启用状态：列有 {@code DEFAULT 'ACTIVE'}，本方法不接收状态参数——「建好就停用」
     * 没有真实用法，而多一个入参就多一条「建档即停用」的路径，它只能在事后被审计发现。
     *
     * <p>{@code created_at} / {@code updated_at} 交给库的 {@code DEFAULT now()}，应用侧只写
     * {@code created_by} / {@code updated_by}：应用侧再取一次时钟会引入第二个时间来源。
     *
     * @param dictType   类型编码，调用方保证该类型存在且未删除
     * @param value      取值，调用方保证非空白且不超过 64 字符；同一类型下（未删除的项里）唯一
     * @param draft      可编辑字段，已由 {@link DictItemDraft} 归一并校验
     * @param operatorId 操作人标识，写入 {@code created_by} 与 {@code updated_by}
     * @return 数据库生成的字典项标识
     * @throws IllegalStateException 写入未取回主键，说明写入与主键回填不一致
     * @throws RuntimeException      违反库约束（外键、{@code ux_sys_dict_item_value}、长度、CHECK）时
     *                               由实现原样上抛，由调用方翻译成业务错误码
     */
    long insert(String dictType, String value, DictItemDraft draft, long operatorId);

    /**
     * 覆盖字典项的全部可编辑字段，写入 {@code updated_by} 与 {@code updated_at}。
     *
     * <p>可编辑字段由 {@link DictItemDraft} 的形状决定：{@code label}、{@code labelEn}、{@code sortNo}、
     * {@code ext}。{@code dictType}、{@code value}、{@code status} 与审计列都不在 SET 列表里，
     * 因此本方法也没有「把项挪到另一个类型下」「顺手改状态」的能力。
     *
     * <p>返回 {@code void} 而不是影响行数：调用方先用 {@link #findById} 判过存在，命中零行只可能是
     * 这一行在两次调用之间被删除。那不是需要纠正的失败，也不该被当成并发冲突
     * （{@code docs/coding.md}「持久化与副作用」）。
     *
     * @param id         字典项标识，调用方保证该行存在且未删除
     * @param draft      覆盖后的可编辑字段，已由 {@link DictItemDraft} 归一并校验
     * @param operatorId 操作人标识，写入 {@code updated_by}
     * @throws RuntimeException 违反库约束（长度、CHECK）时由实现原样上抛，由调用方翻译成业务错误码
     */
    void update(long id, DictItemDraft draft, long operatorId);

    /**
     * 把字典项置为启用或停用，写入 {@code updated_by} 与 {@code updated_at}。
     *
     * <p>单独成一个动作而不是复用 {@link #update}：停用的语义是「从下拉里消失」，它不能顺带改标签或
     * 排序，否则审计里只有一条「编辑」，看不出这次操作让某个取值从选项里消失了。
     *
     * <p>已经是目标状态时是无操作（命中的行数不做判定）：重复点「停用」不应报错，也不该被当成并发冲突。
     *
     * @param id         字典项标识，调用方保证该行存在且未删除
     * @param status     目标状态；不允许传 {@code null}，那是调用方没决定要改成什么
     * @param operatorId 操作人标识，写入 {@code updated_by}
     */
    void changeStatus(long id, DictStatus status, long operatorId);

    /**
     * 逻辑删除字典项：置 {@code deleted_at}，同时更新 {@code updated_by} 与 {@code updated_at}。
     *
     * <p><b>为什么返回布尔值，而 {@link #update} 不返回：</b>口径与货主、联系人的 {@code softDelete}
     * 一致。删除是「这条记录从此不该再存在」的断言，而调用方刚读过它——命中零行说明它在读到之后被
     * 另一个请求删掉了，那正是需要如实回 404 的情况。{@link #update} 是「改成一堆新值」，命中零行
     * 可能只是内容本来一样，不代表用户可见的差异。
     *
     * <p>删除不看当前状态：已停用的项可以直接删除，不需要先启用。删掉之后同值可以重新新增
     * （见接口注释），因此也不必为了腾出 {@code value} 而先动状态。
     *
     * @param id         字典项标识
     * @param operatorId 操作人标识，写入 {@code updated_by}
     * @return 实际写入了一行返回 {@code true}；该行已被删除（或本就不存在）返回 {@code false}
     */
    boolean softDelete(long id, long operatorId);

    /**
     * 按标识读取未删除的字典项。
     *
     * <p>已删除的行与「不存在」同口径：管理端看不到它，接口因此必须返回同一个结果。若这里能读到已
     * 删除的行，调用方就会把一条已删除的项当作有效记录去编辑，而所有写语句都带 {@code deleted_at IS NULL}
     * ——那次编辑既不生效也不报错，是最难排查的一类静默失败。
     *
     * <p>存在的两个用途是「判存在」与「取变更前快照」：后者要求读到的是完整的一行，因此返回值是
     * 读模型 {@link DictItem} 而不是「有没有」这个布尔——同一个问题不需要第二个入口。
     *
     * @param id 字典项标识
     * @return 未删除的字典项；不存在或已删除时为空
     * @throws IllegalArgumentException 库里的状态编码未知，说明数据已损坏，不静默回落
     * @throws IllegalStateException    库里的 {@code ext} 不是合法的 JSON 对象，说明数据已损坏
     */
    Optional<DictItem> findById(long id);

    /**
     * 字典项的可编辑字段：新增与编辑共用的输入值对象。
     *
     * <p><b>为什么新增与编辑共用一个类型：</b>这四个字段在两个动作里的可写性完全一致，没有「创建后
     * 锁定」的成员；真正创建后不可改的 {@code dict_type} 与 {@code value} 不在本类里，{@code id} 由库生成，
     * {@code status} 走 {@link #changeStatus}。拆成两个类型只会多一次逐字段转写，而转写漏一个字段的
     * 后果是「改了个标签，英文名被清空」——这类缺陷不会被编译拦下。
     *
     * <p><b>空白串归一为 {@code null}：</b>整串空白在业务上与「没填」没有区别，存下 {@code ' '} 会让
     * 「填了没有」的判空永远为真，展示层把它渲染成一个点不开的空选项。反过来，非空白串一律不裁剪：
     * 那是用户的真实输入，领域层悄悄改写会让审计无法解释「库里的值和提交的不一致」。
     *
     * <p><b>这里判非空与列宽，不是重复的入口校验：</b>它是最后一道防线，拦的是绕过用例直接构造
     * （将来的批量导入、数据修复脚本）。宽度取自 {@code V018__sys_dict.sql} 的列定义而不是散在调用方，
     * 改列宽时因此只有一处要跟着改（与 {@code OwnerContactDraft} 同一处理）。
     *
     * <p><b>{@code ext} 归一成不可变 Map：</b>{@code Map.copyOf} 不接受 {@code null} 值，而 {@code ext}
     * 来自 JSON，里面完全可能写着 {@code {"parent": null}}，那会让一次合法写入抛
     * {@code NullPointerException}；因此拷贝成不可变的 {@link LinkedHashMap}，与 {@link DictItem} 的处理一致，
     * 同时保证调用方在写入之后修改原 Map 也改不到已经落库的行。
     *
     * @param label   中文名，必填，最长 64 字符
     * @param labelEn 英文名，可为 {@code null}，最长 64 字符；缺失由前端回退到中文名，后端不代填
     * @param sortNo  排序号，越小越靠前；允许与其他项重复，由调用方给值
     * @param ext     扩展属性，{@code null} 归一成空 Map；需要层级的字典在 {@code ext.parent} 里写父项的 value
     */
    record DictItemDraft(String label, String labelEn, int sortNo, Map<String, Object> ext) {

        /** {@code label} / {@code label_en} 的列宽上限，取自 {@code V018__sys_dict.sql} 的 {@code varchar(64)}。 */
        private static final int NAME_MAX_LENGTH = 64;

        /**
         * 归一空白串与扩展属性，并校验创建期不变量。
         *
         * @throws IllegalArgumentException 中文名为空，或中文名、英文名超出列宽
         */
        public DictItemDraft {
            label = blankToNull(label);
            labelEn = blankToNull(labelEn);
            requirePresent(label, "字典项中文名");
            requireMaxLength(labelEn, "字典项英文名");
            ext = ext == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(ext));
        }

        /**
         * 把整串空白归一为 {@code null}，其余值原样返回。
         *
         * @param value 原始输入，可为 {@code null}
         * @return 整串空白时为 {@code null}，否则为原值
         */
        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value;
        }

        /**
         * 校验必填字段非 {@code null} 且不超列宽。
         *
         * <p>只判「有没有」与「多长」，不判格式：长度是列的不变量，格式属于入口的声明式校验。
         *
         * @param value 字段值
         * @param name  字段的中文名，用于拼出可直接展示的错误信息
         * @throws IllegalArgumentException 值为 {@code null} 或超出列宽
         */
        private static void requirePresent(String value, String name) {
            if (value == null) {
                throw new IllegalArgumentException(name + "不能为空");
            }
            requireMaxLength(value, name);
        }

        /**
         * 校验字段不超出列宽；{@code null} 视为未填，不算超长。
         *
         * @param value 字段值，可为 {@code null}
         * @param name  字段的中文名
         * @throws IllegalArgumentException 值超出列宽
         */
        private static void requireMaxLength(String value, String name) {
            if (value != null && value.length() > NAME_MAX_LENGTH) {
                throw new IllegalArgumentException(name + "最长 " + NAME_MAX_LENGTH + " 字符");
            }
        }
    }
}

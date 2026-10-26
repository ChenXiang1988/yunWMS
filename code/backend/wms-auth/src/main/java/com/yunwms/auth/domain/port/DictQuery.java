package com.yunwms.auth.domain.port;

import com.yunwms.auth.domain.model.DictItem;
import com.yunwms.auth.domain.model.DictStatus;
import com.yunwms.auth.domain.model.DictTypeSummary;

import java.util.Collection;
import java.util.List;

/**
 * 字典的读取入口：类型清单、某类字典项、多类型字典项。
 *
 * <p>本批只有读：新增、编辑、启停属于 2.7b，届时在同一个模块里另开写入端口，
 * 不把写方法加到本端口上——读侧不需要事务语义，也不需要审计字段，混在一起会让
 * 「这个方法是不是在改数据」变成要读实现才能回答的问题。
 *
 * <p>筛选条件一律由调用方给出，端口不预设业务口径：
 * <ul>
 *   <li>{@code status} 为 {@code null} 表示不限状态（历史回显），为 {@link DictStatus#ACTIVE}
 *       表示只要启用项（下拉）。两种用途共用同一份数据，不拆成两个端点。</li>
 *   <li>{@code parentValue} 命中项上 {@code ext.parent} 的值，用来取某个国家下的省/州。
 *       层级写在 {@code ext} 里而不是新增 {@code parent_id} 列：加列会把字典绑死成树，
 *       而多数字典是平的。</li>
 * </ul>
 *
 * <p>多个类型的读取必须能用一次查询完成（{@link #findItemsByTypes}）：一个表单常要同时取
 * 客户类型、温区、合规属性，按类型循环会让请求里的类型数直接变成 SQL 条数。
 */
public interface DictQuery {

    /**
     * 取全部未删除的字典类型，含各自的项数。
     *
     * <p>项数必须与类型行在同一条语句里聚合得出，不得逐类再查。
     *
     * @return 全部字典类型，按类型编码升序；没有数据时为空列表
     */
    List<DictTypeSummary> findAllTypes();

    /**
     * 判断字典类型是否存在（未删除即视为存在）。
     *
     * <p>单独成一个方法而不是让调用方在 {@link #findAllTypes()} 的结果里查找：查单项是否存在
     * 只值一条带条件的语句，而把所有类型都取回内存再过滤，会随类型数量增长做无用的搬运。
     *
     * @param dictType 类型编码
     * @return 存在且未删除时为 {@code true}
     */
    boolean existsType(String dictType);

    /**
     * 取某类字典项。
     *
     * @param dictType    类型编码，调用方保证该类型存在
     * @param status      项状态筛选；{@code null} 表示不限状态
     * @param parentValue 只取 {@code ext.parent} 等于该值的项；{@code null} 表示不按层级筛选
     * @return 字典项，按排序号、标识升序；没有匹配时为空列表
     */
    List<DictItem> findItems(String dictType, DictStatus status, String parentValue);

    /**
     * 一次取回多个类型的字典项。
     *
     * <p>不返回「哪些类型不存在」：调用方传进来的类型可能来自管理员刚删掉的配置，
     * 为此报错会让整页挂掉，而正确行为是「有就返回、没有就少一个键」。
     *
     * <p>为空集合时返回空列表且不查库：空的 {@code IN} 条件在拼装时会被省略，
     * 查询会退化成「查全部字典项」。
     *
     * @param dictTypes 类型编码集合
     * @param status    项状态筛选；{@code null} 表示不限状态
     * @return 命中的字典项，按类型编码、排序号、标识升序；没有匹配时为空列表
     */
    List<DictItem> findItemsByTypes(Collection<String> dictTypes, DictStatus status);
}

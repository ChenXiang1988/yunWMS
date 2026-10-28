package com.yunwms.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.yunwms.auth.infrastructure.persistence.entity.DictItemRow;
import com.yunwms.auth.infrastructure.persistence.entity.DictTypeRow;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * {@code sys_dict} / {@code sys_dict_item} 的映射：四条读取语句与四条写入语句，外加一条按标识读取。
 *
 * <p><b>不继承 {@code BaseMapper}：</b>读侧语句需要「聚合项数」与「按 jsonb 内部键过滤」，
 * 写侧则要拿回主键、把列写成 {@code NULL} 并同时写审计列，都不是 Wrapper 能表达的。继承来的
 * {@code deleteById} 尤其不能有——字典项只允许逻辑删除，物理删除会毁掉历史业务数据对 {@code value}
 * 的引用（见 {@code DictMaintenance} 的类注释）。
 *
 * <p><b>读与写放在同一个 Mapper 里：</b>同一张表的两套 SQL 分到两个文件时，「已删除怎么表示」
 * 「审计列怎么写」这两条口径会被各写一遍，而漏写的那一遍就是一条能把数据改坏的语句。
 * 端口仍然分成 {@code DictQuery} 与 {@code DictMaintenance}：分开的理由是事务语义与回执不同，
 * 不是 SQL 该住在不同文件里。
 *
 * <p>手写 SQL 必须自己带 {@code deleted_at IS NULL}：MyBatis-Plus 的逻辑删除只作用于它生成的语句，
 * 漏掉的话已逻辑删除的字典项会重新出现在下拉里，或已被删除的行被编辑悄悄「改活」。
 */
public interface SysDictMapper {

    /**
     * 取全部未删除的字典类型，附带各自的项数。
     *
     * <p>项数必须在这条语句里聚合：逐类再查一次会让类型列表的 SQL 条数随类型数量增长。
     *
     * @return 字典类型行，按类型编码升序
     */
    List<DictTypeRow> selectTypeSummaries();

    /**
     * 判断字典类型是否存在（未删除即视为存在）。
     *
     * @param dictType 类型编码
     * @return 存在且未删除时为 {@code true}
     */
    boolean existsType(@Param("dictType") String dictType);

    /**
     * 取某类字典项。
     *
     * @param dictType    类型编码
     * @param status      项状态编码；{@code null} 表示不限状态
     * @param parentValue 只取 {@code ext.parent} 等于该值的项；{@code null} 表示不按层级筛选
     * @return 字典项行，按排序号、标识升序
     */
    List<DictItemRow> selectItems(@Param("dictType") String dictType,
                                  @Param("status") String status,
                                  @Param("parentValue") String parentValue);

    /**
     * 一次取多个类型的字典项。
     *
     * <p>调用方保证 {@code dictTypes} 非空：空的 {@code IN} 条件在拼装时会被省略，
     * 查询会退化成「查全部字典项」。
     *
     * @param dictTypes 类型编码集合
     * @param status    项状态编码；{@code null} 表示不限状态
     * @return 字典项行，按类型编码、排序号、标识升序
     */
    List<DictItemRow> selectItemsByTypes(@Param("dictTypes") Collection<String> dictTypes,
                                         @Param("status") String status);

    /**
     * 按标识取一条未删除的字典项。
     *
     * <p>读侧现有的四条语句都不能替代它：{@link #selectItems} 按类型取全量，拿它按标识找一条要先把
     * 整个类型读进内存。管理端的「判存在」与「取变更前快照」都是按标识定位，因此单独一条语句。
     *
     * @param id 字典项标识
     * @return 字典项行；不存在或已删除时返回 {@code null}
     */
    DictItemRow selectItemById(@Param("id") long id);

    /**
     * 写入新字典项，返回数据库生成的主键。
     *
     * <p>用 {@code INSERT ... RETURNING id} 取主键而不是 {@code useGeneratedKeys}：后者要求参数是一个
     * 带 {@code id} 属性的对象，而本批没有为写入建实体；{@code RETURNING} 让主键随语句本身返回，
     * 参数因此可以只有标量（与 {@code SysCodeRuleMapper#advance} 同一手法）。
     *
     * <p><b>必须跳过数据权限拦截器：</b>本语句用 {@code <select>} 承载 {@code INSERT}，MyBatis 因此
     * 走 {@code Executor.query}，被注册在 {@code SqlSessionFactory} 上的数据权限插件会拿它去做 SQL 解析；
     * 而 {@code JsqlParserSupport.processInsert} 分支在插件未覆写时直接抛
     * {@code UnsupportedOperationException}——语句根本到不了数据库，接口表现为 50000。字典项的写入与
     * 货主数据范围无关，本就不该被这条插件解析；插件侧 {@code DataPermissionInterceptor.beforeQuery}
     * 的第一句就是 {@code InterceptorIgnoreHelper.willIgnoreDataPermission(ms.getId())}，因此这里跳过解析
     * 只作用于本语句，其它语句的数据权限覆盖面不受影响。
     *
     * <p><b>为什么不改造成 {@code <insert useGeneratedKeys>}：</b>{@code <insert>} 走
     * {@code executeUpdate}，PostgreSQL 驱动不会把 {@code RETURNING} 的行作为结果集交出来，要拿回主键
     * 就得先为写入引入一个带 {@code id} 属性的实体，代价大于收益。也<b>不</b>改成「让拦截器只解析
     * SELECT」：那会改变全仓所有语句的数据权限覆盖面，属于另一次需要单独评估的改动，不该由一条字典项
     * 语句的缺陷来推动。
     *
     * <p><b>「用 {@code <select>} 包 DML」这条方案的风险（留给后来者）：</b>它能成立，依赖的是
     * 「拦截器恰好承认这类语句」这个实现细节，不是 JDBC 语义。{@code SysCodeRuleMapper#advance} 的
     * {@code UPDATE ... RETURNING} 同样用 {@code <select>} 承接却没出问题，只因
     * {@code JsqlParserSupport} 只在 {@code processInsert} 分支抛异常，UPDATE 路径恰好能过——这不是
     * 「可以忽略」的巧合。将来若用 {@code <select>} 包 {@code MERGE}、{@code DELETE ... RETURNING} 等
     * 其它 DML，必须重新确认拦截器是否有对应分支（必要时同样加注解），否则同一个 50000 会换条语句复现。
     *
     * <p>{@code status}、{@code created_at}、{@code updated_at}、{@code deleted_at} 不出现在列清单里：
     * 分别由列默认值（{@code 'ACTIVE'}、{@code now()}）与「未删除」的初始状态负责，应用侧不预设，
     * 也就不存在「新增即停用」或「新增即带删除标记」的写法。
     *
     * @param dictType   类型编码，外键指向 {@code sys_dict}
     * @param value      取值，调用方保证非空白
     * @param label      中文名
     * @param labelEn    英文名，可为 {@code null}
     * @param sortNo     排序号
     * @param ext        扩展属性的 JSON 对象文本，由仓储层序列化；{@code {}} 表示没有扩展属性
     * @param operatorId 操作人标识，写入 {@code created_by} 与 {@code updated_by}
     * @return 数据库生成的字典项标识；语句没有返回行时为 {@code null}，由仓储据此判失败
     */
    @InterceptorIgnore(dataPermission = "1")
    Long insertItem(@Param("dictType") String dictType,
                    @Param("value") String value,
                    @Param("label") String label,
                    @Param("labelEn") String labelEn,
                    @Param("sortNo") int sortNo,
                    @Param("ext") String ext,
                    @Param("operatorId") long operatorId);

    /**
     * 覆盖一条未删除字典项的可编辑列，写入 {@code updated_by} 与 {@code updated_at}。
     *
     * <p>SET 列表里没有 {@code dict_type}、{@code value}、{@code status}、创建痕迹与 {@code deleted_at}：
     * 前两个是业务数据的引用键，状态走 {@link #updateStatus}，删除标记只能由 {@link #softDelete} 写。
     * 写死在这里比依赖调用方「记得别传」可靠——即使上层把 {@code value} 放进了模型，这条语句也改不到它。
     *
     * @param id         字典项标识
     * @param label      中文名
     * @param labelEn    英文名，{@code null} 表示清空
     * @param sortNo     排序号
     * @param ext        扩展属性的 JSON 对象文本，{@code {}} 表示没有扩展属性
     * @param operatorId 操作人标识，写入 {@code updated_by}
     */
    void updateItem(@Param("id") long id,
                    @Param("label") String label,
                    @Param("labelEn") String labelEn,
                    @Param("sortNo") int sortNo,
                    @Param("ext") String ext,
                    @Param("operatorId") long operatorId);

    /**
     * 改一条未删除字典项的状态，写入 {@code updated_by} 与 {@code updated_at}。
     *
     * <p>不带「仅当状态不同才更新」的条件：重复点同一个状态是正常操作，命中行数不作为成功与否的判据。
     *
     * @param id         字典项标识
     * @param status     目标状态编码，取 {@code DictStatus} 的枚举名
     * @param operatorId 操作人标识，写入 {@code updated_by}
     */
    void updateStatus(@Param("id") long id,
                      @Param("status") String status,
                      @Param("operatorId") long operatorId);

    /**
     * 逻辑删除一条字典项：置 {@code deleted_at}，并留下「谁删的」。
     *
     * <p>不写 {@code status}：删掉一个停用项之后它仍是停用的，顺带改状态会让「这次删的是哪一类」在
     * 数据层面看不出来。
     *
     * <p>返回影响行数：{@code WHERE} 上的 {@code deleted_at IS NULL} 同时是「不重复删除」的判据，
     * 仓储据此告诉调用方「这一行在我读到之后已经不在了」（{@code docs/coding.md} 要求非插入写入
     * 校验实际结果，这是本 Mapper 唯一需要它的语句）。
     *
     * @param id         字典项标识
     * @param operatorId 操作人标识，写入 {@code updated_by}
     * @return 实际写入的行数：{@code 1} 表示本次删除生效，{@code 0} 表示该行本就不存在或已被删除
     */
    int softDelete(@Param("id") long id, @Param("operatorId") long operatorId);
}

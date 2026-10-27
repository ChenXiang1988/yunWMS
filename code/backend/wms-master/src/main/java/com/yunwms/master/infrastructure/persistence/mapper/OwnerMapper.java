package com.yunwms.master.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunwms.master.infrastructure.persistence.entity.OwnerEntity;
import com.yunwms.master.infrastructure.persistence.entity.OwnerNameRow;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * {@code owner} 的映射接口。
 *
 * <p>单行插入与按标识读取用 {@link BaseMapper}：它们不需要拼条件，{@code selectById} 还会自动带上
 * 逻辑删除条件。其余语句走 XML，各有具体原因，见 {@code mapper/master/OwnerMapper.xml}：
 * <ul>
 *   <li>列表与计数要拼三个可选条件并显式列出列名，Wrapper 拼出来的语句在「全空」与「部分为空」
 *       之间的差异读代码时看不出来；</li>
 *   <li>两条存在性判定的 {@code deleted_at} 口径<b>不相同</b>（前缀是全表唯一，外部码是未删除时唯一），
 *       而 Wrapper 会被逻辑删除配置统一加上条件——把差异写在 XML 里才看得见；</li>
 *   <li>编辑要能把列写成 {@code NULL}，而 {@code updateById} 会略过空字段，见 {@link #updateProfile}；</li>
 *   <li>锁读要拼 {@code FOR UPDATE}，而 {@code BaseMapper} 与 Wrapper 都没有这个能力，
 *       见 {@link #selectByIdForUpdate}；</li>
 *   <li>名称批量查询要<b>包含已软删除的货主</b>，而逻辑删除配置会给 Wrapper 生成的语句统一加上
 *       {@code deleted_at IS NULL}，见 {@link #selectNamesByIds}。</li>
 * </ul>
 */
public interface OwnerMapper extends BaseMapper<OwnerEntity> {

    /**
     * 统计满足筛选条件的货主数。
     *
     * @param status    状态编码；{@code null} 表示不筛选
     * @param ownerType 客户类型编码；{@code null} 表示不筛选
     * @param keyword   按编码、名称或外部码模糊匹配的关键字；{@code null} 表示不筛选
     * @return 货主总数
     */
    long countByFilter(@Param("status") String status,
                       @Param("ownerType") String ownerType,
                       @Param("keyword") String keyword);

    /**
     * 按筛选条件取一页货主，按标识升序。
     *
     * @param status    状态编码；{@code null} 表示不筛选
     * @param ownerType 客户类型编码；{@code null} 表示不筛选
     * @param keyword   按编码、名称或外部码模糊匹配的关键字；{@code null} 表示不筛选
     * @param limit     本页最多返回多少行
     * @param offset    跳过多少行
     * @return 本页货主行；没有数据时为空列表
     */
    List<OwnerEntity> selectPageByFilter(@Param("status") String status,
                                         @Param("ownerType") String ownerType,
                                         @Param("keyword") String keyword,
                                         @Param("limit") int limit,
                                         @Param("offset") long offset);

    /**
     * 按标识读取一行货主并加排他行锁（{@code SELECT ... FOR UPDATE}）。
     *
     * <p>不能改用 {@code BaseMapper#selectById} 加别的手段：{@code FOR UPDATE} 必须与那条 {@code SELECT}
     * 是同一条语句，先查再锁会留下一个「查到与锁上之间被别人改掉」的窗口，而状态流转的整个正确性
     * 都建立在「判定用的值与写回时的值属于同一份」之上。
     *
     * <p>手写 SQL 必须自己带 {@code deleted_at IS NULL}：MyBatis-Plus 的逻辑删除只作用于它生成的语句。
     * 漏掉这一条，一次针对已删除货主的流转会读到行、锁住行、更新却命中零行，
     * 调用方看到的是一次成功而库里什么都没变。
     *
     * <p>调用方必须在事务内调用：没有事务时锁在语句结束即释放，读到的只是一个可能立刻过期的快照。
     *
     * @param id 货主标识
     * @return 被锁住的货主行；不存在或已删除时为 {@code null}
     */
    OwnerEntity selectByIdForUpdate(@Param("id") long id);

    /**
     * 覆盖货主的档案列与审计列。
     *
     * <p>不走 {@code BaseMapper#updateById}：它按「非空字段才拼进 SET」的策略生成语句，
     * 于是「把外部码清空」「把区县清空」这类 {@code null} 值会被整列略过，旧值留在库里，
     * 而接口回了成功——这是最难自查的一类不一致。这里的 SET 列表写死全部可编辑列，
     * {@code null} 就是写 {@code NULL}。
     *
     * <p>SET 列表里没有 {@code code}、{@code sku_prefix}、{@code status}：三列在本批不可修改，
     * 写死在这里比依赖调用方不传更可靠。{@code updated_at} 用库内 {@code now()}，
     * 与 {@code insert} 走列默认值同源；{@code updated_by} 由调用方给出。
     *
     * <p>手写 SQL 必须自己带 {@code deleted_at IS NULL}：MyBatis-Plus 的逻辑删除只作用于它生成的语句。
     *
     * @param entity 覆盖后的档案值，必须带标识与操作人
     * @return 影响行数；本语句的调用方不据此判定成功，理由见 {@code OwnerRepository#update}
     */
    int updateProfile(OwnerEntity entity);

    /**
     * 只写状态列与审计列，供状态流转使用。
     *
     * <p>与 {@link #updateProfile} 分成两条语句而不是复用一条带可空参数的：编辑永远不该改状态，
     * 流转也永远不该改档案。合成一条后，两个用例都拿到了改对方字段的能力，靠「这次不传」来约束，
     * 而「不传」的代价是 {@code updateById} 式的空字段略过——那正是 {@link #updateProfile} 存在的理由。
     *
     * <p>手写 SQL 必须自己带 {@code deleted_at IS NULL}：MyBatis-Plus 的逻辑删除只作用于它生成的语句。
     *
     * @param id        货主标识
     * @param status    目标状态编码，取 {@code OwnerStatus} 的枚举名
     * @param updatedBy 操作人标识
     * @return 影响行数；本语句的调用方不据此判定成功，理由见 {@code OwnerRepository#updateStatus}
     */
    int updateStatus(@Param("id") long id, @Param("status") String status, @Param("updatedBy") long updatedBy);

    /**
     * 软删除：只置 {@code deleted_at} 与审计列。
     *
     * <p>不写 {@code status}：删除一个草稿之后它仍是草稿（只是看不见了）。把它改成别的状态会让
     * 「这个货主终止过吗」这个问题在数据层面出现第三种答案。
     *
     * @param id        货主标识
     * @param updatedBy 操作人标识
     * @return 影响行数；0 表示这一行已被删除或不存在，调用方据此回 404
     */
    int softDelete(@Param("id") long id, @Param("updatedBy") long updatedBy);

    /**
     * 判断 SKU 前缀是否已被占用，口径为全表（含已逻辑删除的货主），与全表唯一索引一致。
     *
     * @param skuPrefix SKU 前缀
     * @return 已被占用返回 {@code true}
     */
    boolean existsBySkuPrefix(@Param("skuPrefix") String skuPrefix);

    /**
     * 判断外部码是否已被占用，口径为未删除的货主，与部分唯一索引一致。
     *
     * @param externalCode 外部系统编码
     * @return 已被占用返回 {@code true}
     */
    boolean existsByExternalCode(@Param("externalCode") String externalCode);

    /**
     * 按 SKU 前缀取占用方的系统编码，口径为全表（含已逻辑删除的货主），与全表唯一索引一致。
     *
     * <p>返回单列而不是整行：调用方只需要把编码拼进一句提示，多搬十几列会让「这里为什么要读一整行」
     * 变成一个要翻调用方才能回答的问题。
     *
     * @param skuPrefix SKU 前缀
     * @return 占用方编码；前缀未被占用时为 {@code null}
     */
    String selectCodeBySkuPrefix(@Param("skuPrefix") String skuPrefix);

    /**
     * 按标识批量取货主的标识与名称，<b>口径是全表，刻意不带 {@code deleted_at} 条件</b>。
     *
     * <p>不是漏写：账号绑定的货主可能已被逻辑删除，而账号列表仍要显示它的名称，否则
     * 「绑了一个已删除的货主」与「没绑货主」在界面上长得一样。
     *
     * <p>因此本语句不能走 {@code BaseMapper} + Wrapper：逻辑删除配置会给它生成的语句统一加上
     * {@code deleted_at IS NULL}，那段条件拿不掉。也不复用 {@link OwnerEntity}：只搬两列，
     * 而整行读回要经过 {@code Owner} 聚合的重建校验，历史数据会在与显示无关的规则上失败。
     *
     * <p>调用方保证 {@code ownerIds} 非空：空集合会拼出 {@code IN ()}，或退化成不带条件的全表查询。
     *
     * @param ownerIds 货主标识集合，调用方保证非空
     * @return 标识与名称；查不到的标识不出现在结果里
     */
    List<OwnerNameRow> selectNamesByIds(@Param("ownerIds") Collection<Long> ownerIds);
}

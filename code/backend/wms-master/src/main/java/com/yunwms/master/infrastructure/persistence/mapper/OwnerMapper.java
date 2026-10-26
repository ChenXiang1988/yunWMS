package com.yunwms.master.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunwms.master.infrastructure.persistence.entity.OwnerEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code owner} 的映射接口。
 *
 * <p>单行插入与按标识读取用 {@link BaseMapper}：它们不需要拼条件，{@code selectById} 还会自动带上
 * 逻辑删除条件。另外四条走 XML，各有具体原因，见 {@code mapper/master/OwnerMapper.xml}：
 * <ul>
 *   <li>列表与计数要拼三个可选条件并显式列出列名，Wrapper 拼出来的语句在「全空」与「部分为空」
 *       之间的差异读代码时看不出来；</li>
 *   <li>两条存在性判定的 {@code deleted_at} 口径<b>不相同</b>（前缀是全表唯一，外部码是未删除时唯一），
 *       而 Wrapper 会被逻辑删除配置统一加上条件——把差异写在 XML 里才看得见。</li>
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
}

package com.yunwms.auth.infrastructure.persistence.mapper;

import com.yunwms.auth.infrastructure.persistence.entity.DictItemRow;
import com.yunwms.auth.infrastructure.persistence.entity.DictTypeRow;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * {@code sys_dict} / {@code sys_dict_item} 的读取映射。
 *
 * <p>四条查询全部走 XML，不继承 {@code BaseMapper}：本批只有读、没有实体，而三条业务语句各自
 * 需要「聚合项数」与「按 jsonb 内部键过滤」，Wrapper 表达不出来。继承来的
 * {@code selectList} / {@code insert} 之类的方法也用不上——一个用不上的写方法摆在那里，
 * 早晚会被当成「本模块也能写字典」的证据。
 *
 * <p>手写 SQL 必须自己带 {@code deleted_at IS NULL}：MyBatis-Plus 的逻辑删除只作用于它生成的语句，
 * 漏掉的话已逻辑删除的字典项会重新出现在下拉里。
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
}

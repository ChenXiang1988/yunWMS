package com.yunwms.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunwms.auth.infrastructure.persistence.entity.RoleSummaryRow;
import com.yunwms.auth.infrastructure.persistence.entity.SysRoleEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code sys_role} 的映射接口。
 *
 * <p>单表等值条件（登录链路取用户角色）用 {@code BaseMapper} 表达；角色管理页的三条查询走 XML，
 * 因为它们需要把 {@code sys_user_role} 与 {@code sys_role_menu} 的计数聚合进同一条语句，
 * Wrapper 表达不出子查询。这不只是为了少写几行：在循环里逐条数计数会让列表页的 SQL 条数
 * 随角色数量线性增长（10 个角色 21 次查询）。
 *
 * <p>手写 SQL 必须自己带 {@code deleted_at IS NULL}：MyBatis-Plus 的逻辑删除只作用于它生成的语句，
 * 漏掉的话已逻辑删除的角色会重新出现在管理界面里。
 */
public interface SysRoleMapper extends BaseMapper<SysRoleEntity> {

    /**
     * 统计满足筛选条件的角色数。
     *
     * @param keyword 按编码或名称模糊匹配的关键字；{@code null} 表示不筛选
     * @param status  角色状态编码；{@code null} 表示不筛选
     * @return 角色总数
     */
    long countByFilter(@Param("keyword") String keyword, @Param("status") String status);

    /**
     * 按排序取一页角色，同时带出占用人数与授权数量。
     *
     * @param keyword 按编码或名称模糊匹配的关键字；{@code null} 表示不筛选
     * @param status  角色状态编码；{@code null} 表示不筛选
     * @param limit   本页最多返回多少行
     * @param offset  跳过多少行
     * @return 本页角色行；没有数据时为空列表
     */
    List<RoleSummaryRow> selectSummaryPage(@Param("keyword") String keyword,
                                           @Param("status") String status,
                                           @Param("limit") int limit,
                                           @Param("offset") long offset);

    /**
     * 按标识取单个角色，同时带出占用人数与授权数量。
     *
     * @param roleId 角色标识
     * @return 角色行；不存在或已逻辑删除时为 {@code null}
     */
    RoleSummaryRow selectSummaryById(@Param("roleId") long roleId);
}

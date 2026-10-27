package com.yunwms.master.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code owner_assignee} 的只读映射接口：按内部员工取他负责的货主。
 *
 * <p>本表当前只有读取方（{@code wms-auth} 的 ASSIGNED 数据范围推导）；写入（3.5 负责客服维护）
 * 尚未落地，届时新增写入语句应走同一个 Mapper，或按职责拆出写入侧——现在只有读，不预建写接口。
 *
 * <p>手写 SQL 而不是 {@code BaseMapper} + Wrapper：本项目其余关联表（{@code sys_user_role} 等）
 * 都有实体映射，而这里只需要一列标识，为它造一个 {@code *Entity} 会让「这张表还有哪些列被读过」
 * 变成需要翻实体才能回答的问题。
 */
public interface OwnerAssigneeMapper {

    /**
     * 取某个用户负责（主或副）的全部货主标识。
     *
     * <p>用 {@code DISTINCT} 去重是必要的：唯一索引建在 {@code (owner_id, relation_type)} 上，
     * 同一个人对同一个货主既可以是主负责人也可以是副负责人，两行会投影出同一个标识。
     *
     * @param userId 用户标识
     * @return 货主标识，按标识升序；没有负责任何货主时为空列表
     */
    List<Long> selectOwnerIdsByUserId(@Param("userId") long userId);
}

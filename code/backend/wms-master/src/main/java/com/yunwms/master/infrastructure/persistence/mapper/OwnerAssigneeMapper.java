package com.yunwms.master.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code owner_assignee} 的映射接口：数据范围推导用的读，以及负责客服维护用的读写。
 *
 * <p><b>读与写放在同一个接口：</b>表只有一张、列只有六列，按读写拆成两个接口会让「这张表被谁碰过」
 * 需要翻两个文件才能回答。写入随负责客服维护（3.5）落在这里，与 3.5b 之前的
 * {@link #selectOwnerIdsByUserId} 同一个接口，不另起一个只多两行的兄弟。
 *
 * <p>手写 SQL 而不是 {@code BaseMapper} + Wrapper：本项目其余关联表（{@code sys_user_role} 等）
 * 都有实体映射，而这里只需要标识列，为它造一个 {@code *Entity} 会让「这张表还有哪些列被读过」
 * 变成需要翻实体才能回答的问题。没有实体也就没有 {@code BaseMapper} 可用，四条语句因此都在
 * {@code mapper/master/OwnerAssigneeMapper.xml} 里显式列出列名。
 *
 * <p>本表没有 {@code updated_*} 与 {@code deleted_at}（见 {@code V008}）：换负责人是「删一行 + 插一行」，
 * 撤销是物理删除。因此这里没有 update 语句，手写 SQL 也没有软删除条件可加——不是漏写。
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

    /**
     * 取某货主某角色当前的员工标识。
     *
     * <p>返回单值而不是列表：{@code ux_owner_assignee_role (owner_id, relation_type)} 保证同一货主
     * 同一角色至多一行。若投影出第二行，{@code selectOne} 会当场抛
     * {@code TooManyResultsException}——那说明索引被绕过约束写坏了，比「随便取一行」更接近事实。
     *
     * @param ownerId  货主标识
     * @param relation 关系编码，只允许 {@code PRIMARY} / {@code SECONDARY}（{@code ck_assignee_relation}），
     *                 由调用方按枚举名给出，SQL 里不做转换
     * @return 该角色当前的员工标识；该角色尚未指定时为 {@code null}
     */
    Long selectUserIdByOwnerIdAndRelation(@Param("ownerId") long ownerId,
                                          @Param("relation") String relation);

    /**
     * 插入一条负责客服关系。
     *
     * <p>{@code created_at} 不写：列上有 {@code DEFAULT now()}，交给数据库可以让同一行的创建时间
     * 只有一个来源；应用侧再取一次时钟只会引入两个可能相差几毫秒的时间来源
     * （与 {@code OwnerContactMapper} 的插入同一理由）。
     *
     * <p><b>不做 upsert（{@code ON CONFLICT DO NOTHING}）：</b>同角色旧行由调用方先删，而两笔并发替换
     * 中后写的一笔应当撞上 {@code ux_owner_assignee_role} 并原样上抛——端口已声明由调用方把它翻译成
     * 业务错误码。若这里吞掉冲突，后写的那笔会「成功」而库里留下的是前一笔的人，谁生效取决于并发时序。
     *
     * @param ownerId   货主标识，调用方保证该货主存在，外键兜底
     * @param userId    员工标识，调用方保证该用户存在，外键兜底
     * @param relation  关系编码，取值见 {@link #selectUserIdByOwnerIdAndRelation}
     * @param createdBy 操作人标识，写入 {@code created_by}
     * @return 影响行数
     */
    int insertAssignee(@Param("ownerId") long ownerId,
                       @Param("userId") long userId,
                       @Param("relation") String relation,
                       @Param("createdBy") long createdBy);

    /**
     * 物理删除某货主某角色的关系行。
     *
     * <p><b>是物理删除而不是软删除：</b>{@code V008} 没有 {@code deleted_at}，本表只存「现在是谁」。
     * 同一件事因此只有一种写法，不存在「删了但还在」的中间状态需要别的语句去分辨。
     *
     * <p><b>命中零行是正常结果：</b>该角色本来就没有人（第一次指定负责人正是这个情形），
     * 端口已把这种情况声明为无操作。调用方因此不据影响行数判定成功或并发冲突
     * （{@code docs/coding.md}「持久化与副作用」要求的校验结论在这里是「零行合法」）。
     *
     * @param ownerId  货主标识
     * @param relation 关系编码，取值见 {@link #selectUserIdByOwnerIdAndRelation}
     * @return 影响行数；唯一索引保证至多一行，因此为 0 或 1
     */
    int deleteByOwnerIdAndRelation(@Param("ownerId") long ownerId, @Param("relation") String relation);

    /**
     * 统计该员工当前担任主或副负责人的货主数。
     *
     * <p><b>主副都算，且按货主去重：</b>同一个人对同一个货主可以既是主负责人又是副负责人（两行），
     * 直接 {@code count(*)} 会让「还有几个货主要转交」多算。去重口径与
     * {@link #selectOwnerIdsByUserId} 一致——同一个人「能看见的货主」与「必须先转交的货主」
     * 不能是两个数字。
     *
     * <p><b>不联 {@code owner} 表、不按货主状态过滤：</b>已终止的货主仍在名下，理由见
     * {@code OwnerAssigneeRepository#countByUserId}；本 Mapper 也不该知道货主有哪些状态。
     *
     * @param userId 员工标识
     * @return 担任主或副负责人的货主数；一段关系都没有时为 {@code 0}
     */
    long countByUserId(@Param("userId") long userId);
}

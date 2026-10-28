package com.yunwms.master.infrastructure.persistence.repository;

import com.yunwms.master.domain.model.AssigneeRelation;
import com.yunwms.master.domain.port.OwnerAssigneeRepository;
import com.yunwms.master.infrastructure.persistence.mapper.OwnerAssigneeMapper;
import org.springframework.stereotype.Repository;

import java.util.OptionalLong;

/**
 * {@link OwnerAssigneeRepository} 的 MyBatis 实现。
 *
 * <p><b>为什么不开事务注解：</b>仓储不做事务边界，与 {@code OwnerRepositoryImpl} 一致。
 * {@link #replace} 的两条语句（先删同角色旧行、再插新行）必须原子，而边界归用例：配置负责人的
 * 用例（3.5b-3）需要把「校验员工 → 替换 → 写审计」放进同一个事务，那才是这次操作真正的最小完整单元。
 * 仓储再开一层不会更强（同一线程里嵌套仍是同一个事务），却会让「事务边界在哪」出现第二个答案。
 *
 * <p><b>删除是物理删除：</b>本表没有 {@code updated_*} 与 {@code deleted_at}（{@code V008}），只存
 * 「这个货主的这个角色现在是谁」，变更历史由审计日志承担。因此这里没有软删除，也没有「更新一行」的
 * 写法——为 {@code user_id} 单设一条 UPDATE 会让同一件事有两种写法，且两种写法对
 * {@code created_at} / {@code created_by} 记的是「这一次指定」还是「第一次指定」给出不同答案。
 *
 * <p><b>不做领域模型映射：</b>端口只收发标识与角色，没有需要重建的聚合；需要「完整的负责人关系」的
 * 调用方只有数据范围推导，它读的是 {@code OwnerAssigneeReader} 的集合形状。为两列标识造一个领域对象，
 * 只会让「这张表还有哪些列被读过」变成要翻两个类才能回答的问题。
 */
@Repository
public class OwnerAssigneeRepositoryImpl implements OwnerAssigneeRepository {

    private final OwnerAssigneeMapper ownerAssigneeMapper;

    public OwnerAssigneeRepositoryImpl(OwnerAssigneeMapper ownerAssigneeMapper) {
        this.ownerAssigneeMapper = ownerAssigneeMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@code null} 只会在「该角色还没有人」时出现：{@code user_id} 列是 {@code NOT NULL}，
     * 读到空值只能是查询没有命中行，不是库里存了一个空标识。
     */
    @Override
    public OptionalLong findUserId(long ownerId, AssigneeRelation relation) {
        Long userId = ownerAssigneeMapper.selectUserIdByOwnerIdAndRelation(ownerId, relation.name());
        return userId == null ? OptionalLong.empty() : OptionalLong.of(userId);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>先删再插，不「先查再决定」：</b>同一人重复被指定为某角色时，删掉旧行再插同一人，净效果
     * 为零——关系仍只指向这个人，也不会多出第二行同角色的行（端口已声明这是幂等语义）。加一条
     * 「已经是他就不写」的分支只会多一次查询，还要自己处理并发下的时序，而两条路径的结果相同。
     * 代价是每次指定都会换一个新的 {@code id}：业务上读的是 {@code user_id}，没有任何调用方持有它。
     *
     * <p><b>两条语句的原子性由调用方的事务保证</b>（见类注释）：只删不插会留下「该角色没有人」，
     * 只插不删会撞上 {@code ux_owner_assignee_role}，两种中间态都不该被别的请求看见。
     *
     * <p><b>两条语句的影响行数都不参与判定：</b>删除命中零行意味着该角色本来就没有人（第一次指定
     * 负责人正是这个情形），既不是失败也不是并发冲突；插入恒为一行。并发的两笔替换中后写的一笔会撞上
     * 唯一索引，那时数据库异常原样上抛，由调用方翻译成业务错误码——这里不吞、也不改写
     * （{@code docs/coding.md}「持久化与副作用」要求的校验结论在这里是「零行合法」）。
     */
    @Override
    public void replace(long ownerId, AssigneeRelation relation, long userId, long operatorId) {
        ownerAssigneeMapper.deleteByOwnerIdAndRelation(ownerId, relation.name());
        ownerAssigneeMapper.insertAssignee(ownerId, userId, relation.name(), operatorId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>不看影响行数：该角色本来就没有人时命中零行，正是本方法的正常输入（端口已声明是无操作）。
     * 与 {@code OwnerContactRepositoryImpl#clearPrimary} 同一取向，与「删除一条已知存在的记录」
     * 那种「零行说明记录已经不在了」的判据正好相反。
     */
    @Override
    public void remove(long ownerId, AssigneeRelation relation) {
        ownerAssigneeMapper.deleteByOwnerIdAndRelation(ownerId, relation.name());
    }

    /**
     * {@inheritDoc}
     *
     * <p>口径全部落在 SQL 里（主副都算、按货主去重、不按货主状态过滤），见
     * {@link OwnerAssigneeMapper#countByUserId}。不在应用层取回全部货主再数一遍：那会让这次调用
     * 的代价随该员工的货主数线性增长，而这个数字只在停用员工时用一次、且只进一句提示。
     */
    @Override
    public long countByUserId(long userId) {
        return ownerAssigneeMapper.countByUserId(userId);
    }
}

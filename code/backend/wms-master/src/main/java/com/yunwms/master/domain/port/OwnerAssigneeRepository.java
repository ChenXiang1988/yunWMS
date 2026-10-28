package com.yunwms.master.domain.port;

import com.yunwms.master.domain.model.AssigneeRelation;

import java.util.Optional;

/**
 * 货主负责客服关系的写入入口：指定、撤销，以及停用员工前要用的计数。
 *
 * <p><b>为什么与 {@link OwnerAssigneeReader} 分成两个端口：</b>那个端口只有一个方法，回答的是
 * 「这名员工能看见哪些货主」，唯一调用方是 {@code ASSIGNED} 数据范围的实时推导，它只该拿到读能力。
 * 本端口的语义（替换某个角色、判某个员工名下还有几段关系）与数据范围不同族，两者没有一行共用逻辑；
 * 合在一起等于让数据范围的调用方也拿得到改关系的方法。拆法与 {@code OwnerAccountLookup} /
 * {@code OwnerAccountProvisioner} 一致：按使用方要问的问题拆端口，不按「同一张表」拆。
 *
 * <p><b>参数用 {@link AssigneeRelation} 而不是 {@code String}：</b>理由见该枚举的类注释——
 * 库里的 CHECK 约束需要 Java 侧有一个对应物，否则拼错角色只有运行期才知道。
 *
 * <p><b>这张表没有 {@code updated_*} 与 {@code deleted_at}，因此改负责人是「删一行 + 插一行」：</b>
 * 表里只存「这个货主的这个角色现在是谁」，不存历史流水；被替换掉的那一行没有任何调用方需要，
 * 留着它只会让「现在是谁」多一个筛选条件。变更历史由审计日志承担（{@link AuditTrail}），
 * 不由这张表承担。本端口因此没有软删除，也没有「更新一行」的方法，理由见 {@link #replace}。
 *
 * <p><b>「每个货主必有一名主负责人」按不变量理解，因此本端口不实现这条判定：</b>规格原文是
 * 「每个货主 MUST 有一名主负责人」。若把它读成「建档时必须指定」，现存货主就都得补一个人，
 * 而现有建档流程里根本没有负责人这一步。本端口与配置负责人的用例共同守的是另一条口径：
 * <b>一旦给某货主配置了负责人，主负责人就在其中</b>——配副负责人前先配主负责人，撤销主负责人
 * 只能发生在转交之后。端口只如实执行「写这一段关系」或「删这一段关系」，不替用例决定操作顺序，
 * 也不判断这次操作之后该货主是否还有主负责人：判断所需的上下文（还有没有别的负责人、这次是不是
 * 转交流程）只有用例拿得到。
 *
 * <p><b>唯一索引是最后一道防线，本端口不翻译它：</b>{@code ux_owner_assignee_role} 保证同一货主
 * 同一角色至多一行。两笔并发的替换仍可能让后写的一笔撞上它，那时实现原样上抛库异常，
 * 由调用方翻译成业务错误码（错误码属于协议层，与 {@code OwnerContactRepository} 同一处理）。
 *
 * <p><b>数据范围不在这里判：</b>「这个货主对当前账号可见吗」由用例先经
 * {@link OwnerRepository#findVisibleById} 判定。端口再收一遍范围参数，等于把同一条数据权限规则
 * 写到第二个地方，而漏写的那一处就是越权。
 */
public interface OwnerAssigneeRepository {

    /**
     * 取某货主某角色当前的员工标识。
     *
     * <p>用途固定为回显与「改之前先看现在是谁」。不返回员工姓名等信息：本表只存标识，
     * 员工档案在 {@code sys_user}，要展示姓名的用例自己声明它需要的端口
     * （与 {@link StaffLookup} 同一取向：端口按使用方要问的问题声明）。
     *
     * <p>不复用 {@link OwnerAssigneeReader#findOwnerIdsByUserId}：那是同一条关系的反方向，
     * 返回形状与用途都不同（单值 vs 集合；回显 vs 数据范围），合并会让数据范围的端口长出一个
     * 它不需要的方法。
     *
     * @param ownerId  货主标识
     * @param relation 关系角色，调用方保证非 {@code null}（传 {@code null} 属于编程错误，不是业务输入）
     * @return 该角色当前的员工标识；该角色尚未指定时为 empty
     */
    Optional<Long> findUserId(long ownerId, AssigneeRelation relation);

    /**
     * 把某货主某角色的负责人替换成指定员工。
     *
     * <p><b>语义是「这一段关系最终是什么」，因此重复提交同一组参数不改变任何事实：</b>
     * 写入口会被重试、被前端重复提交（双击、刷新后再提交）。已经指向该员工时，删旧行再插同一个人
     * 与整段跳过，对调用方不可区分——关系仍然只指向这一名员工，也不会多出第二行同角色的行。
     *
     * <p><b>为什么不是 update：</b>表里没有 {@code updated_*} 可写（见类注释），而「换人」在行级
     * 就是换掉一行。为 {@code user_id} 单设一条 UPDATE 会让同一件事有两种写法，两种写法对
     * {@code created_at} / {@code created_by} 记的是谁给出不同答案（删插记的是「这一次指定」，
     * 更新记的是「第一次指定」）——那个差别没有消费方，却足够让下一次改动挑错写法。
     *
     * <p><b>为什么返回 {@code void}：</b>没有需要向调用方交代的结果。删除命中零行意味着该角色本来
     * 就没有人（第一次指定就是这个情形），既不是失败也不是并发冲突，因此没有可返回的信号
     * （见 {@code docs/coding.md}「持久化与副作用」：非插入写入要校验实际结果，而这里的校验结论是
     * 「零行合法」）。
     *
     * <p>实现 MUST 先删同角色旧行再插新行，两步 MUST 在同一事务里：只删不插会留下「该角色没有人」，
     * 只插不删会撞上唯一索引，两种中间态都不该被别的请求看见。事务边界归实现决定
     * （实现方法上开事务，或由用例的事务包住这次调用），本端口不认识 Spring。
     *
     * @param ownerId    货主标识，调用方保证该货主存在
     * @param relation   要替换的角色，调用方保证非 {@code null}
     * @param userId     作为负责人的员工标识，调用方保证是已启用员工（由 {@link StaffLookup} 判）
     * @param operatorId 操作人标识，写入 {@code created_by}
     * @throws RuntimeException 违反库约束（{@code ux_owner_assignee_role}、{@code ck_assignee_relation}、
     *                          外键）时由实现原样上抛，由调用方翻译成业务错误码
     */
    void replace(long ownerId, AssigneeRelation relation, long userId, long operatorId);

    /**
     * 清除某货主某角色的负责人。
     *
     * <p><b>用途固定为撤销副负责人。</b>转交主负责人走 {@link #replace} 覆盖，不先调本方法：
     * 「先清空再写入」会让该货主在一小段时间里没有主负责人，而规格要求它有；
     * 一次覆盖则没有那个中间态。
     *
     * <p><b>该角色本来就没有人时是无操作，不报错，因此返回 {@code void}：</b>与
     * {@code OwnerContactRepository#clearPrimary} 同一取向——「本来就没有」是正常输入之一，
     * 命中零行既不是失败也不是需要交代的情况。实现仍须确认删除语句执行成功（无异常即成立），
     * 只是这个结论没有可返回的形状。
     *
     * <p><b>本方法不判「删的是不是主负责人」：</b>那会让「主负责人必填」这条不变量出现第二个落点，
     * 而端口也拿不到判断所需的上下文（该货主还有没有别的负责人、这次撤销是否处在转交流程中）。
     *
     * @param ownerId  货主标识
     * @param relation 要清除的角色，调用方保证非 {@code null}
     */
    void remove(long ownerId, AssigneeRelation relation);

    /**
     * 统计该员工当前担任主或副负责人的货主数。
     *
     * <p><b>用途固定：</b>停用员工前的校验（规格「停用前必须转交」）。名下仍有货主时停用要被拒，
     * 而这个数字会进提示，让管理员知道要转交几个，而不是只得到一句「还有货主」。
     *
     * <p><b>口径：主副都算，不按货主状态过滤。</b>主副都算是规格写法：可见范围与停用条件都写
     * 「主或副」。不按状态过滤有两个理由：本端口不读 {@code owner} 表，「终止的货主不算」是一条
     * 业务判断，埋进一个计数里以后没人找得到它；而数据范围推导用的
     * {@link OwnerAssigneeReader} 同样不按状态过滤，两处口径一旦分歧，同一个人「能看见的货主」
     * 与「必须先转交的货主」就成了两个数字，提示里说 3 个、列表里只有 2 个。
     *
     * @param userId 员工标识
     * @return 该员工担任主或副负责人的货主数；一段关系都没有时返回 {@code 0}
     */
    long countByUserId(long userId);
}

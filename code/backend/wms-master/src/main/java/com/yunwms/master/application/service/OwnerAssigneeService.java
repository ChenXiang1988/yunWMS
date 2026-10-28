package com.yunwms.master.application.service;

import com.yunwms.master.application.audit.OwnerAuditSnapshots;
import com.yunwms.master.application.command.AssignOwnerAssigneeCommand;
import com.yunwms.master.application.dto.OwnerAssigneeView;
import com.yunwms.master.domain.model.AssigneeRelation;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerAuditEvent;
import com.yunwms.master.domain.port.AuditTrail;
import com.yunwms.master.domain.port.CurrentOperator;
import com.yunwms.master.domain.port.OwnerAssigneeRepository;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.master.domain.port.StaffLookup;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.OptionalLong;

/**
 * 货主负责客服的用例：回显当前配置、把主副两个角色一次写到目标状态，并留下审计。
 *
 * <p><b>配置是全量替换，没有「只改副负责人」的入口：</b>一次提交同时给出主、副两个角色的目标值
 * （副为空表示不设副）。理由见 {@link AssignOwnerAssigneeCommand}——部分更新会让后端分不清
 * 「没传字段」与「传了空值表示清除」，而一次「只换副负责人」的提交就可能静默地把主负责人一并清掉。
 *
 * <p><b>主负责人必填是本类的第一条不变量：</b>规格写「每个货主 MUST 有一名主负责人」。
 * 若把它读成「建档时必须指定」，现存货主就都得补一个人，而建档流程里根本没有负责人这一步；
 * 因此这条口径落实为「一旦配置负责人，主就在其中」——撤销主负责人只能通过换成另一名员工（转交）完成，
 * 那次替换由 {@link OwnerAssigneeRepository#replace} 一次覆盖，不留「该货主没有主负责人」的中间态。
 * 副负责人为空是合法输入，本条用例会把旧的那一段 {@code remove} 掉。
 *
 * <p><b>主副不能是同一人：</b>{@code ux_owner_assignee_role} 建在 {@code (owner_id, relation_type)} 上，
 * 只保证「每个角色至多一行」，拦不住同一个人同时占两个角色。那种数据在页面上看着是一主一副，
 * 在「停用前必须转交」的计数（{@link OwnerAssigneeRepository#countByUserId}）里却会被算成两段关系，
 * 提示于是说名下还有两个货主而实际只有一个。这条判定因此只能在本类做。
 *
 * <p><b>员工有效性不区分「不存在」与「已停用」：</b>{@link StaffLookup#isActiveStaff} 对两者都返回
 * {@code false}，这里统一翻成 40000 并提示「不是当前在册的启用员工」。区分两者对调用方的行动没有影响
 * （换个人），却要多一次查询，还会对枚举他人账号留出「这个人存在，只是停用了」这类额外信息
 * （端口注释里已定下同一取向）。
 *
 * <p><b>可见性口径与联系人一致：读侧与写侧都先过 {@link OwnerRepository#findVisibleById}。</b>
 * 负责人是货主的子资源，没有独立的可见范围；不可见与不存在同口径 40400。用 {@code findById}
 * 会让范围外的货主「看不见却改得动」，而 {@link #view} 若换成存在性判定，就等于让范围外的数据
 * 被读出来再决定要不要给出去。
 *
 * <p><b>开事务的是 {@link #assign}：</b>{@link OwnerAssigneeRepository#replace} 的两条语句
 * （删同角色旧行、插新行）必须原子——只删不插会留下「该角色没有人」，只插不删会撞唯一索引，
 * 两种中间态都不该被别的请求看见；审计又必须与业务变更同生共死，否则会留下「改了但没留痕」与
 * 「留了痕但没改」两种都要人工核对的残局。{@link #view} 不加事务：两次只读查询各取自己的快照，
 * 默认隔离级别下加注解也不会让这一对更一致（与 {@code OwnerAssigneeLookupService} 同一处理）。
 *
 * <p><b>并发撞上 {@code ux_owner_assignee_role} 已翻译成 40900，不再按 50000 上抛：</b>
 * 事务内的原子性只保证单笔请求不留中间态，挡不住两笔并发的替换——两边可能都走完「删同角色旧行」
 * （删除命中零行也是无操作），后写的插入被唯一索引拒绝。那是一次能被如实解释的正常失败（别人先改了
 * 同一角色），由 {@link OwnerConstraintTranslator} 翻成「该货主已有主负责人，请刷新后重试」，
 * 约束名到错误码的映射仍只归那一处，本类不判约束名。
 *
 * <p><b>{@code catch} 只包主角色那一次 {@link OwnerAssigneeRepository#replace}：</b>同一个方法里还有副负责人
 * 写入、{@code remove} 与审计，它们各自的库异常含义不同，一并报成「已有主负责人」既是谎报，
 * 也会把真正的存储故障（例如审计表写不进去）藏进一条看似正常的业务提示里。
 *
 * <p><b>审计记员工标识，不记姓名：</b>姓名会随改名变化，而审计表只追加——记了姓名就同时存在
 * 「当时的姓名」与「现在的姓名」两份，读日志的人无从判断哪个是准的。对象类型取 {@code OWNER}、
 * {@code bizId} 取货主编码，理由见 {@link OwnerAuditEvent#OWNER_ASSIGNEE_CHANGED}；
 * 编码取自这次可见性判定的返回值，不为审计多查一次货主。
 *
 * <p><b>值一个都没变时仍然留痕：</b>{@link OwnerAssigneeRepository#replace} 的语义是「删旧行 + 插新行」，
 * 同一名员工重复提交也会换掉那一行的 {@code created_by} 与 {@code created_at}，这次提交因此不是
 * 无副作用的重放，记一条前后相同的日志是如实记录。与 {@code OwnerUpdateService}「字段全等就不记」
 * 的差别在于那一条的前提是「除 {@code updated_at} 外没有别的事实变化」。
 */
@Service
public class OwnerAssigneeService {

    private final OwnerAssigneeRepository ownerAssigneeRepository;
    private final OwnerRepository ownerRepository;
    private final StaffLookup staffLookup;
    private final CurrentOperator currentOperator;
    private final AuditTrail auditTrail;

    /**
     * @param ownerAssigneeRepository 负责客服关系的读写端口
     * @param ownerRepository        货主仓储端口；只用于可见性判定与取审计用的货主编码
     * @param staffLookup            员工有效性窄端口，判两个标识是不是当前在册的启用员工
     * @param currentOperator        当前操作人端口
     * @param auditTrail             审计留痕端口，由 {@code wms-app} 接到审计能力上
     */
    public OwnerAssigneeService(OwnerAssigneeRepository ownerAssigneeRepository,
                                OwnerRepository ownerRepository,
                                StaffLookup staffLookup,
                                CurrentOperator currentOperator,
                                AuditTrail auditTrail) {
        this.ownerAssigneeRepository = ownerAssigneeRepository;
        this.ownerRepository = ownerRepository;
        this.staffLookup = staffLookup;
        this.currentOperator = currentOperator;
        this.auditTrail = auditTrail;
    }

    /**
     * 取某货主当前的主、副负责人，供详情页与配置弹窗回显。
     *
     * <p>先判可见性再读两个角色：不可见时的正确结果不是「两个都空」——那会被读成「这个货主还没有
     * 配置负责人」，而事实是「你无权看这个货主」，两者对调用方的下一步动作完全不同。
     *
     * @param ownerId 货主标识
     * @return 当前配置；某个角色还没有人时对应字段为 {@code null}
     * @throws BizException 货主不存在、已逻辑删除或不在当前数据范围内（40400）
     */
    public OwnerAssigneeView view(long ownerId) {
        requireVisibleOwner(ownerId);
        return new OwnerAssigneeView(ownerId,
                findAssigneeUserId(ownerId, AssigneeRelation.PRIMARY),
                findAssigneeUserId(ownerId, AssigneeRelation.SECONDARY));
    }

    /**
     * 把某货主的主、副负责人一次写到目标状态。
     *
     * <p><b>校验顺序固定为「取操作人 → 货主可见 → 主必填 → 主副不同 → 员工有效 → 读变更前 → 写 → 留痕」：</b>
     * 操作人先取，它既是写入语句要落的 {@code created_by}，也是审计记录的操作人；货主可见先于字段校验，
     * 因为「这个陌生的员工不对」这句话只有在货主确实可见时才对调用方有意义；员工有效性排在最后一条校验，
     * 因为它是这串判断里唯一要查一次库的一步，前置几条都是纯内存判断。
     *
     * <p><b>读变更前放在全部校验通过之后：</b>校验不通过时这次读取的结果用不着；而越早读，
     * 读到的旧值越可能是别人随后改掉的状态，把那个状态写成本次的「变更前」并不准确。
     *
     * <p><b>写入顺序是先主后副：</b>主负责人的目标值必然存在，副负责人可能是不设。
     * 两条语句顺序本身不影响最终结果（唯一索引建在角色上，不是建在人上），
     * 但先写必有的那一条，读代码的人不必先排除「主有没有写完」这个念头。
     *
     * <p><b>并发写同一角色的失败在这里被翻译：</b>后写的一笔撞上 {@code ux_owner_assignee_role} 时抛 40900，
     * 提示「该货主已有主负责人，请刷新后重试」——两条并发请求只有一条生效，另一条刷新后再提交。
     *
     * @param command 配置入参；主负责人为空或与副负责人相同都会被拒绝
     * @throws BizException 当前会话无效（40100）；货主不存在、已逻辑删除或不在当前数据范围内（40400）；
     *                      主负责人为空、主副为同一名员工、任一标识不是当前在册的启用员工（40000）；
     *                      并发下另一笔请求先写入了同一角色（40900）
     */
    @Transactional
    public void assign(AssignOwnerAssigneeCommand command) {
        long operatorId = currentOperator.currentUserId()
                // 与货主的四个写用例同一理由：取不到操作人就不能继续，created_by 写成空值后无从追溯
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        // 可见的货主顺带提供审计要用的系统编码，不再为它多查一次
        Owner owner = requireVisibleOwner(command.ownerId());

        Long primaryUserId = command.primaryUserId();
        Long secondaryUserId = command.secondaryUserId();
        if (primaryUserId == null) {
            // 空值在这里的语义是「撤销主负责人」，而规格要求每个货主必须有一名，因此它是非法目标状态，
            // 不是「这个字段没填」——宁可拒绝，也不替调用方猜他想保留原主负责人
            throw BizException.badRequest("主负责人不能为空：每个货主必须有一名主负责人");
        }
        if (primaryUserId.equals(secondaryUserId)) {
            // 唯一索引拦不住这一种：它建在 (owner_id, relation_type) 上，同一个人可以各占一行
            throw BizException.badRequest("主负责人与副负责人不能是同一名员工");
        }
        if (!staffLookup.isActiveStaff(primaryUserId)) {
            throw BizException.badRequest(
                    AssigneeRelation.PRIMARY.label() + "不是当前在册的启用员工，请重新选择");
        }
        if (secondaryUserId != null && !staffLookup.isActiveStaff(secondaryUserId)) {
            throw BizException.badRequest(
                    AssigneeRelation.SECONDARY.label() + "不是当前在册的启用员工，请重新选择");
        }

        Long previousPrimary = findAssigneeUserId(owner.id(), AssigneeRelation.PRIMARY);
        Long previousSecondary = findAssigneeUserId(owner.id(), AssigneeRelation.SECONDARY);

        try {
            ownerAssigneeRepository.replace(owner.id(), AssigneeRelation.PRIMARY, primaryUserId, operatorId);
        } catch (DataIntegrityViolationException e) {
            // 只包这一条写语句：下面的副负责人写入、remove 与审计各有各的失败含义，
            // 把它们一并说成「已有主负责人」是谎报，也会把真实的存储故障藏起来（见类注释）
            throw OwnerConstraintTranslator.translateAssigneeWrite(e);
        }
        if (secondaryUserId == null) {
            // 提交「不设副」与「本来就没有副」都要落到同一个事实：该角色没有人。
            // remove 命中零行是无操作，因此不必先判有没有旧值
            ownerAssigneeRepository.remove(owner.id(), AssigneeRelation.SECONDARY);
        } else {
            ownerAssigneeRepository.replace(owner.id(), AssigneeRelation.SECONDARY, secondaryUserId, operatorId);
        }

        auditTrail.record(OwnerAuditEvent.OWNER_ASSIGNEE_CHANGED, owner.code(),
                OwnerAuditSnapshots.ofAssignee(previousPrimary, previousSecondary),
                OwnerAuditSnapshots.ofAssignee(primaryUserId, secondaryUserId));
    }

    /**
     * 判定货主对当前请求可见，并顺手取回审计要用到的系统编码。
     *
     * @param ownerId 货主标识
     * @return 可见的货主
     * @throws BizException 货主不存在、已逻辑删除或不在当前数据范围内（40400）
     */
    private Owner requireVisibleOwner(long ownerId) {
        return ownerRepository.findVisibleById(ownerId)
                // 范围外的货主与「不存在」同口径：两者的区别本身就是一条能拿来枚举他方数据的信息
                .orElseThrow(() -> BizException.notFound("货主不存在"));
    }

    /**
     * 取某角色当前的员工标识，把端口的 {@link OptionalLong} 转成可空值。
     *
     * <p>转换只发生在这里：回显与审计要的都是「这个角色有没有人」，而 {@link OwnerAssigneeView}
     * 与审计快照的契约都是「键始终存在、值可空」。让两个消费点各自拆一次箱，拆法迟早就出现两种。
     *
     * @param ownerId  货主标识
     * @param relation 关系角色
     * @return 该角色当前的员工标识；该角色还没有人时为 {@code null}
     */
    private Long findAssigneeUserId(long ownerId, AssigneeRelation relation) {
        OptionalLong userId = ownerAssigneeRepository.findUserId(ownerId, relation);
        return userId.isPresent() ? userId.getAsLong() : null;
    }
}

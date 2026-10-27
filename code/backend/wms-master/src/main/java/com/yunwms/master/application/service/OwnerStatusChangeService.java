package com.yunwms.master.application.service;

import com.yunwms.master.application.command.ChangeOwnerStatusCommand;
import com.yunwms.master.application.dto.TerminationImpact;
import com.yunwms.master.application.query.OwnerImpactQueryService;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerStatus;
import com.yunwms.master.domain.port.CurrentOperator;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 货主状态流转的用例：五态之间的唯一写入口。
 *
 * <p><b>校验顺序固定为「取操作人 → 货主存在 → 目标状态可解析 → 流转是否允许 → 终止前确认 → 落库」：</b>
 * <ul>
 *   <li>操作人先取：{@code updated_by} 的来源，取不到就没有可追溯的责任人；</li>
 *   <li>存在性先于状态：货主不存在时「当前状态是什么」无从谈起，返回 404 才是调用方能据以行动的信息；</li>
 *   <li>目标状态可解析先于流转判定：{@code FOO} 不是「不允许的流转」，是「不存在的状态」，
 *       报成「当前状态不允许」会把一次拼写错误引向状态机；</li>
 *   <li>确认放在流转判定之后：{@code TERMINATED → ACTIVE} 无论确认与否都要被拒，先问确认
 *       等于让用户为一次注定失败的请求去读影响清单。</li>
 * </ul>
 *
 * <p><b>流转规则只调 {@link OwnerStatus#requireTransitionTo}，本类不写任何状态组合：</b>
 * 规则的第二处落点会让「界面拦住了、接口没拦住」成为可能。
 *
 * <p><b>同状态提交是幂等成功：</b>目标状态与当前相同时照常写库、不回错。与
 * {@code UserStatusChangeService} 同一口径，理由见 {@code docs/api-convention.md} 第七节。
 * 终止态的幂等同样成立——「终止是终态」说的是不能转去别的状态，不是「重复提交要报错」。
 *
 * <p><b>终止前要求二次确认（规格「终止时的处理边界」的落点）：</b>终止把「影响 N 个商品与 M 个账号」
 * 摆到调用方面前，{@code acknowledgedImpact = true} 才继续。后端不展示界面，但可以要求调用方显式确认，
 * 于是绕过前端直接调接口同样躲不掉这一步；这也正是「仅变更货主状态、不动商品与账号」的对照面——
 * 影响清单是提示，不是级联动作。
 *
 * <p><b>确认只对「本次真的要终止」生效：</b>当前已是 {@code TERMINATED} 时不查影响、也不要求确认，
 * 那次调用是幂等重放，没有新的后果要确认。
 *
 * <p><b>本类不改商品与账号：</b>规格明令终止时 MUST NOT 自动修改商品档案状态。这里没有对应的端口，
 * 也没有「顺手停用账号」的分支——将来要加，也必须先改规格。
 *
 * <p><b>必须开事务并锁读当前行：</b>本用例是典型的「读当前值 → 判定 → 写回」，三段之间若没有行锁，
 * 两个并发请求可以同时读到 {@code ACTIVE}：一个判定「终止允许」、一个判定「暂停允许」，
 * 两条 UPDATE 依提交顺序覆盖，最后落库的可能是一次暂停——于是终止返回了成功，却不再是终态。
 * {@link OwnerRepository#findByIdForUpdate} 拿到的锁一直持有到本事务提交，
 * 后到的那次请求因此读到的是前一次提交后的状态，判定依据与写回依据才是同一份。
 *
 * <p><b>代价是有意接受的：</b>同一货主的状态流转由并发变为串行，锁内只有一次锁读、一次状态判定
 * 与一条单行 UPDATE，未确认终止那次会再读一次只读的影响清单。管理端的状态流转是低频操作，
 * 拿这点延迟换「终止是终态」在并发下也成立，值得。
 */
@Service
public class OwnerStatusChangeService {

    private final OwnerRepository ownerRepository;
    private final OwnerImpactQueryService ownerImpactQueryService;
    private final CurrentOperator currentOperator;

    /**
     * @param ownerRepository         货主仓储端口
     * @param ownerImpactQueryService 影响清单读取用例；终止未确认时用它把数字写进提示
     * @param currentOperator         当前操作人端口
     */
    public OwnerStatusChangeService(OwnerRepository ownerRepository,
                                    OwnerImpactQueryService ownerImpactQueryService,
                                    CurrentOperator currentOperator) {
        this.ownerRepository = ownerRepository;
        this.ownerImpactQueryService = ownerImpactQueryService;
        this.currentOperator = currentOperator;
    }

    /**
     * 把货主流转到目标状态。
     *
     * <p>{@code @Transactional} 必须落在本方法上而不是某个私有方法里：Spring 的事务是基于代理的，
     * 同类内部调用不经过代理，事务不会开启，其中那次锁读拿到的锁也就随之失去意义。
     *
     * <p>锁读放在最前面、与状态判定和写回同处一个事务，是为了让「判定依据的状态」与
     * 「写回时所在的状态」是同一份：任何并发请求对同一货主的流转都在锁上排队，
     * 后到者读到的是前者提交后的状态，因此不会再出现「两次判定都基于 ACTIVE」。
     *
     * @param command 流转入参
     * @throws BizException 当前会话无效（40100）；货主不存在或已逻辑删除（40400）；
     *                      目标状态编码未知（40000）；当前状态不允许流转到目标状态（40900）；
     *                      终止未确认影响清单（40900，提示里带商品数与账号数）
     */
    @Transactional
    public void change(ChangeOwnerStatusCommand command) {
        long operatorId = currentOperator.currentUserId()
                // 与建档、编辑同一理由：取不到操作人就不能继续，updated_by 写成空值后无从追溯
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        Owner existing = ownerRepository.findByIdForUpdate(command.ownerId())
                // 已逻辑删除的货主与「不存在」同口径：管理端看不到它，流转它也就无从谈起。
                // 锁读同样带 deleted_at IS NULL，因此「锁不到」在这里与「不存在」是同一个分支
                .orElseThrow(() -> BizException.notFound("货主不存在"));

        OwnerStatus targetStatus = resolveTargetStatus(command.targetStatus());

        try {
            existing.status().requireTransitionTo(targetStatus);
        } catch (IllegalStateException e) {
            // 目标状态本身合法，被拒绝的是它相对当前状态的关系，因此落在 409 段位而不是 40000
            throw BizException.conflict(e.getMessage());
        }

        // 只在「本次会真的进入终止」时要求确认：已经是终止态的那次调用是幂等重放，
        // 库里不会有任何变化，确认一次没有意义
        if (targetStatus == OwnerStatus.TERMINATED && existing.status() != OwnerStatus.TERMINATED
                && !command.acknowledgedImpact()) {
            // 影响清单会再读一次货主：那次读是为了让「数字属于哪条货主」由它自己保证（它同时是
            // GET /termination-impact 的实现），代价是一次主键查询，换来两个入口只有一个存在性判定。
            // 已确认的路径不查，因此每次终止最多多出一条代价可忽略的语句
            TerminationImpact impact = ownerImpactQueryService.terminationImpact(existing.id());
            throw BizException.conflict("终止将影响 " + impact.skuCount() + " 个商品与 "
                    + impact.accountCount() + " 个账号，请先确认影响清单后再次提交");
        }

        ownerRepository.updateStatus(existing.id(), targetStatus, operatorId);
    }

    /**
     * 把目标状态编码解成枚举。
     *
     * <p>空白按「未提交目标状态」拒绝，而不是当成「不改」：这个接口的全部语义就是「改成什么」，
     * 缺了它没有可执行的动作，静默成功会让调用方以为流转发生了。
     *
     * @param targetStatus 目标状态编码，可为 {@code null}
     * @return 目标状态
     * @throws BizException 编码为空或不是已知的枚举名（40000）
     */
    private static OwnerStatus resolveTargetStatus(String targetStatus) {
        String normalized = OwnerTextNormalizer.toNullIfBlank(targetStatus);
        if (normalized == null) {
            throw BizException.badRequest("目标状态不能为空");
        }
        try {
            return OwnerStatus.fromCode(normalized);
        } catch (IllegalArgumentException e) {
            // 领域层用 IllegalArgumentException 表达「编码未知」，翻译成 40000 放在用例层
            throw BizException.badRequest(e.getMessage());
        }
    }
}

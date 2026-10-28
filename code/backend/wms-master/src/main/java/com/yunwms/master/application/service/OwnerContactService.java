package com.yunwms.master.application.service;

import com.yunwms.master.application.audit.OwnerAuditSnapshots;
import com.yunwms.master.application.command.SaveOwnerContactCommand;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerAuditEvent;
import com.yunwms.master.domain.model.OwnerContact;
import com.yunwms.master.domain.model.OwnerContactDraft;
import com.yunwms.master.domain.port.AuditTrail;
import com.yunwms.master.domain.port.CurrentOperator;
import com.yunwms.master.domain.port.DictLookup;
import com.yunwms.master.domain.port.OwnerContactRepository;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 货主联系人的增删改查用例：可见性判定、角色字典校验、主联系人切换与审计留痕都在这里。
 *
 * <p><b>数据范围只有一条规则：联系人对当前请求的可见性等于其货主的可见性。</b>联系人是货主的子资源，
 * 有自己的权限点（{@code system:owner:contact:edit}），但没有独立的可见范围。因此四条用例都先过
 * {@link OwnerRepository#findVisibleById}——<b>读侧与写侧一样</b>，不用 {@link OwnerRepository#findById}。
 * 两个方法回答的不是同一个问题：{@code findById} 回答「这一行存在吗」，{@code findVisibleById} 回答
 * 「这一行现在看得见吗」。管理端的联系人页签挂在货主详情上，范围外的货主连详情都打不开，
 * 那里却改得动联系人，就出现了「看不见却能改」；反之按存在性放行，则等于让范围外的货主被改。
 * 无请求上下文时（初始化脚本、数据修复）解析结果是「无范围」，可见性恒成立，写路径不会因此失效。
 *
 * <p><b>编辑与删除的顺序固定为「先按联系人标识取归属，再判货主可见」：</b>这两个入口只拿得到联系人标识，
 * 归属是库里那一行的事实，只能先读出来；但读到之后、任何返回或写入之前必须完成可见性判定，
 * 不通过时这次读取的结果直接丢弃——不回给调用方，也不对那一行做任何写入。
 * 反向顺序（先按某个外部传入的 {@code ownerId} 判可见性再去改联系人）等于相信调用方自报的归属。
 *
 * <p><b>主联系人切换固定为「先 {@link OwnerContactRepository#clearPrimary} 再写」，不由仓储隐式完成：</b>
 * {@code ux_owner_contact_primary} 是唯一权威，端口把顺序写进了契约。两个写方法各自隐式清一半的话，
 * 读代码的人无法从调用处看出「这次操作动了别人的记录」。{@code isPrimary} 为 {@code false} 时不清任何东西
 * ——把一条联系人改成非主，不该顺手动另一行。
 *
 * <p><b>三个写方法都开事务：</b>业务变更与审计必须同生共死（{@link AuditTrail} 的实现加入本方法的事务），
 * 否则会留下「联系人改了但没留痕」与「留了痕但联系人没改」两种都要人工核对的残局。
 * 切换主联系人还多一条：清旧主与写新主是同一次业务动作的两个半边，分开提交就会留下
 * 「这个货主一个主联系人也没有」的中间态。{@link #list} 不加事务——只有两次只读查询，
 * 各看各的快照，加注解不会让它们更一致（与 {@code OwnerQueryService} 同一处理）。
 *
 * <p><b>并发下撞上 {@code ux_owner_contact_primary} 时翻译成 40900：</b>两笔「设我为主」的请求若都越过了
 * 清理步骤，后写的一笔会被那个唯一索引拒绝——那是一次能被如实解释的正常失败（别人先设了主联系人），
 * 因此不再按 50000 暴露。提示里写明「该货主已有主联系人，请刷新列表后重新提交」，调用方才能知道
 * 重试前要先刷新，而不是把同一份请求反复提交。约束名到错误码的映射归 {@link OwnerConstraintTranslator}，
 * 本类不再判约束名。{@code catch} 只包住仓储写入那一行：扩大到整个方法，会把审计等其他来源的
 * {@link DataIntegrityViolationException} 一并报成「已有主联系人」，那是谎报。
 *
 * <p><b>审计的对象类型是 {@code OWNER}、{@code bizId} 是货主编码：</b>理由见 {@link OwnerAuditEvent}；
 * 编码取自这次可见性判定的返回值，因此不为审计多查一次货主。
 *
 * <p>字段级的前后值对比不在本类做：审计记完整的变更前后快照，差异由读日志的人（或他的工具）算，
 * 与货主的四个用例同一口径。
 */
@Service
public class OwnerContactService {

    /** 联系人角色的字典类型编码，与 {@code V039__seed_contact_role_dict.sql} 的 {@code dict_type} 一致。 */
    private static final String CONTACT_ROLE_DICT = "CONTACT_ROLE";

    private final OwnerContactRepository ownerContactRepository;
    private final OwnerRepository ownerRepository;
    private final DictLookup dictLookup;
    private final CurrentOperator currentOperator;
    private final AuditTrail auditTrail;

    /**
     * @param ownerContactRepository 联系人仓储端口
     * @param ownerRepository        货主仓储端口；只用于可见性判定与取审计用的货主编码
     * @param dictLookup             字典窄端口，判 {@code roleType} 是否在 {@code CONTACT_ROLE} 的启用项中
     * @param currentOperator        当前操作人端口
     * @param auditTrail             审计留痕端口，由 {@code wms-app} 接到审计能力上
     */
    public OwnerContactService(OwnerContactRepository ownerContactRepository,
                               OwnerRepository ownerRepository,
                               DictLookup dictLookup,
                               CurrentOperator currentOperator,
                               AuditTrail auditTrail) {
        this.ownerContactRepository = ownerContactRepository;
        this.ownerRepository = ownerRepository;
        this.dictLookup = dictLookup;
        this.currentOperator = currentOperator;
        this.auditTrail = auditTrail;
    }

    /**
     * 取某货主名下的全部联系人。
     *
     * <p>先判可见性再取列表，不能反过来：反过来等于「先把范围外的数据读出来，再决定要不要给出去」。
     * 不可见时的正确结果也不是空列表——空列表的意思是「这个货主没有联系人」，
     * 而事实是「你无权看这个货主」，两者对调用方的下一步动作完全不同。
     *
     * <p>不分页也不排序：单个货主的联系人是个位数到几十条，排序由端口固定为标识升序
     * （见 {@link OwnerContactRepository#findByOwnerId}）。
     *
     * @param ownerId 货主标识
     * @return 该货主的未删除联系人，按标识升序；没有时返回空列表
     * @throws BizException 货主不存在、已逻辑删除或不在当前数据范围内（40400）
     */
    public List<OwnerContact> list(long ownerId) {
        requireVisibleOwner(ownerId);
        return ownerContactRepository.findByOwnerId(ownerId);
    }

    /**
     * 新增一条联系人。
     *
     * <p>校验顺序是「取操作人 → 货主可见 → 角色取值 → 组装 draft → 清旧主 → 落库 → 留痕」：
     * 操作人先取，他既是 {@code created_by} 的来源，也是清旧主那条语句要写的 {@code updated_by}；
     * 货主可见先于角色，因为「这个陌生角色不对」这句话只有在货主确实存在时才对调用方有意义。
     *
     * <p>draft 在清旧主之前组装：它的构造是纯内存动作，字段不合法时不该先动到别人的行。
     * 「先清旧主、再写新主」的顺序不受影响——清理仍在写入之前。
     *
     * @param command 新增入参；其中的 {@code isPrimary} 决定是否切换主联系人
     * @return 新联系人，标识为数据库生成值
     * @throws BizException 当前会话无效（40100）；货主不存在、已逻辑删除或不在当前数据范围内（40400）；
     *                      角色为空白或不在 {@code CONTACT_ROLE} 字典的启用项中（40000）；
     *                      写入主联系人时该货主已有另一条主联系人，只可能在并发下发生（40900）
     */
    @Transactional
    public OwnerContact create(SaveOwnerContactCommand command) {
        long operatorId = currentOperatorId();
        Owner owner = requireVisibleOwner(command.ownerId());
        requireContactRole(command.roleType());

        OwnerContactDraft draft = toDraft(command);
        if (draft.isPrimary()) {
            // 不靠仓储隐式清：见类注释。没有旧主时它是无操作，因此不必先查一次「有没有主联系人」
            ownerContactRepository.clearPrimary(owner.id(), operatorId);
        }

        long id;
        try {
            id = ownerContactRepository.insert(owner.id(), draft, operatorId);
        } catch (DataIntegrityViolationException e) {
            // 只包这一条写语句：下面审计的库异常不该被解释成「已有主联系人」（见类注释）
            throw OwnerConstraintTranslator.translateContactWrite(e);
        }
        OwnerContact created = toContact(id, owner.id(), draft);
        // before 为 null：新增没有变更前值。bizId 用货主编码而不是主键——审计要能被「按编码追溯」查到
        auditTrail.record(OwnerAuditEvent.OWNER_CONTACT_CREATED, owner.code(), null,
                OwnerAuditSnapshots.ofContact(created));
        return created;
    }

    /**
     * 覆盖一条联系人的全部可编辑字段。
     *
     * <p>只动得了五个字段与主标记：归属在 {@link OwnerContactDraft} 里根本不存在，写语句的 SET 列表里
     * 也没有 {@code owner_id}，因此不存在「一次编辑把联系人搬到另一个货主名下」的路径。
     *
     * @param contactId 联系人标识
     * @param command   编辑入参；其中的 {@code ownerId} 不参与判定，归属以库里那一行为准
     * @return 更新后的联系人
     * @throws BizException 当前会话无效（40100）；联系人不存在或已逻辑删除（40400）；
     *                      该联系人的货主不在当前数据范围内（40400）；
     *                      角色为空白或不在 {@code CONTACT_ROLE} 字典的启用项中（40000）；
     *                      写入主标记时该货主已有另一条主联系人，只可能在并发下发生（40900）
     */
    @Transactional
    public OwnerContact update(long contactId, SaveOwnerContactCommand command) {
        long operatorId = currentOperatorId();
        OwnerContact existing = requireContact(contactId);
        // 归属只认库里那一行：先取到 ownerId 才判得了可见性，判定不通过时这次读取的结果直接丢弃
        Owner owner = requireVisibleOwner(existing.ownerId());
        requireContactRole(command.roleType());

        OwnerContactDraft draft = toDraft(command);
        if (draft.isPrimary()) {
            // 本条本来就已主时，清理会先把它自己置为非主、随后这次写入又置回主；净效果为零，
            // 多出的那条 UPDATE 落在同一行上，且不产生额外的审计记录
            ownerContactRepository.clearPrimary(existing.ownerId(), operatorId);
        }

        try {
            ownerContactRepository.update(existing.id(), draft, operatorId);
        } catch (DataIntegrityViolationException e) {
            // 与新增同一处理：只包这一条写语句，审计失败不能被说成「已有主联系人」
            throw OwnerConstraintTranslator.translateContactWrite(e);
        }
        OwnerContact updated = toContact(existing.id(), existing.ownerId(), draft);
        auditTrail.record(OwnerAuditEvent.OWNER_CONTACT_UPDATED, owner.code(),
                OwnerAuditSnapshots.ofContact(existing), OwnerAuditSnapshots.ofContact(updated));
        return updated;
    }

    /**
     * 软删除一条联系人。
     *
     * <p>删除会释放该货主的主联系人名额（部分唯一索引带 {@code deleted_at IS NULL}），
     * 因此删掉主联系人之后可以直接设新的，不需要先清一次。
     *
     * @param contactId 联系人标识
     * @throws BizException 当前会话无效（40100）；联系人不存在、已逻辑删除，或在读到之后被另一个请求
     *                      删掉（40400）；该联系人的货主不在当前数据范围内（40400）
     */
    @Transactional
    public void delete(long contactId) {
        long operatorId = currentOperatorId();
        OwnerContact existing = requireContact(contactId);
        Owner owner = requireVisibleOwner(existing.ownerId());

        if (!ownerContactRepository.softDelete(existing.id(), operatorId)) {
            // 读到之后、写入之前这一行被另一个请求删掉了：此刻它确实不存在，如实回答 404。
            // 回成功会掩盖「两次删除之间隔了一段时间」，也让调用方以为是自己删的
            throw BizException.notFound("联系人不存在");
        }

        // after 为 null：删除没有变更后快照。before 是这一行在界面上消失之后唯一的副本
        auditTrail.record(OwnerAuditEvent.OWNER_CONTACT_DELETED, owner.code(),
                OwnerAuditSnapshots.ofContact(existing), null);
    }

    /**
     * 取当前请求的操作人标识。
     *
     * @return 登录用户标识
     * @throws BizException 当前请求没有有效会话（40100）
     */
    private long currentOperatorId() {
        // 与货主的四个写用例同一理由：取不到操作人就不能继续，审计列写成空值后无从追溯这次变更
        return currentOperator.currentUserId()
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
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
     * 按标识读取未删除的联系人。
     *
     * @param contactId 联系人标识
     * @return 联系人
     * @throws BizException 联系人不存在或已逻辑删除（40400）
     */
    private OwnerContact requireContact(long contactId) {
        return ownerContactRepository.findById(contactId)
                // 已逻辑删除的联系人与「不存在」同口径：管理端看不到它，改它也就无从谈起
                .orElseThrow(() -> BizException.notFound("联系人不存在"));
    }

    /**
     * 校验角色取值属于 {@code CONTACT_ROLE} 字典的启用项。
     *
     * <p>空白单独判一次，而不是交给 {@link DictLookup#isActiveItem}：那个端口的契约要求调用方保证
     * 参数非空白（它把空参数当编程错误抛 {@code IllegalArgumentException}），而漏填角色是业务输入问题，
     * 该回 40000 并带一句人能读的提示。这一步属于「角色必须取自字典」这条规则，
     * 不是「必填」的第二次校验——必填由 {@code OwnerContactDraft} 判，这里判的是取值在不在字典里。
     *
     * <p>取值原样送字典比对，不去两侧空格：字典项的值就是 {@code BUSINESS} 这种整串，
     * 在这里裁一次会让「用来判定的值」与「落库的值」不是同一个串，出现「校验通过但存了个字典里没有的值」。
     * 带空格的 {@code " BUSINESS "} 因此被拒，正是应有的结果。
     *
     * @param roleType 提交的角色取值
     * @throws BizException 角色为空白，或不在字典的启用项中（40000）
     */
    private void requireContactRole(String roleType) {
        if (roleType == null || roleType.isBlank()) {
            throw BizException.badRequest("联系人角色不能为空");
        }
        if (!dictLookup.isActiveItem(CONTACT_ROLE_DICT, roleType)) {
            // 停用项不算存在：停用意味着「不再被新数据引用」，而新增与编辑联系人都属于新数据
            throw BizException.badRequest("联系人角色不在字典 " + CONTACT_ROLE_DICT + " 的启用项中：" + roleType);
        }
    }

    /**
     * 把入参搬成写入模型。
     *
     * <p>新增与编辑共用同一份字段清单：两处各写一遍时，漏一个字段的后果是「改了个电话，邮箱被清空」，
     * 而这类缺陷不会被编译拦下（与 {@code OwnerContactRepositoryImpl#fillEditableFields} 同一理由）。
     * 空白串归一与字段校验都在 {@link OwnerContactDraft} 的构造器里，本方法只搬值。
     *
     * @param command 写入入参
     * @return 待落库的字段值
     * @throws IllegalArgumentException 姓名、角色或电话为空，或任一字段超出列宽
     */
    private static OwnerContactDraft toDraft(SaveOwnerContactCommand command) {
        return new OwnerContactDraft(command.name(), command.roleType(), command.phone(),
                command.email(), command.isPrimary(), command.remark());
    }

    /**
     * 用「写进去的值」还原联系人，供返回与审计使用。
     *
     * <p>不回读一次库：落库的就是这份值，回查只多一次往返，还允许「读到的是别人改后的值」
     * 混进本次快照（与 {@link OwnerAuditSnapshots#ofUpdated} 同一取向）。
     *
     * @param id      联系人标识
     * @param ownerId 所属货主标识
     * @param draft   本次落库的字段值
     * @return 联系人
     * @throws IllegalArgumentException 字段不满足不变量，说明写入前已绕过了 {@link OwnerContactDraft}
     */
    private static OwnerContact toContact(long id, long ownerId, OwnerContactDraft draft) {
        return new OwnerContact(id, ownerId, draft.name(), draft.roleType(), draft.phone(),
                draft.email(), draft.isPrimary(), draft.remark());
    }
}

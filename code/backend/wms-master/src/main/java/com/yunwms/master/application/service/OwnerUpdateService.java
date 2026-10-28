package com.yunwms.master.application.service;

import com.yunwms.master.application.audit.OwnerAuditSnapshots;
import com.yunwms.master.application.command.UpdateOwnerCommand;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerAddress;
import com.yunwms.master.domain.model.OwnerAuditEvent;
import com.yunwms.master.domain.model.OwnerProfile;
import com.yunwms.master.domain.port.AuditTrail;
import com.yunwms.master.domain.port.CurrentOperator;
import com.yunwms.master.domain.port.DictLookup;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.master.domain.service.OwnerDictionaryValidator;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 编辑货主档案的用例：全量替换可编辑字段。
 *
 * <p><b>校验顺序固定为「取操作人 → 货主存在 → 领域模型 → 字典四条 → 落库」</b>：
 * 先判存在是因为后面的每条错误都在描述「这个货主的新档案哪里不对」，而货主不存在时那些话都没有意义，
 * 返回 40400 才是调用方能据以行动的信息。
 *
 * <p><b>改不到的东西由类型挡住，而不是靠这里少写几行：</b>{@link OwnerProfile} 里没有编码、SKU 前缀与状态，
 * 写语句的 SET 列表里也没有这三列。SKU 前缀尤其不能改——已生成的 SKUID 以它开头，
 * 改了前缀，已打印出去的标签就指向另一个货主的货；这条与状态无关，草稿期同样成立。
 *
 * <p><b>唯一性同样交给数据库：</b>只可能撞上 {@code ux_owner_external_code}（外部码改动），
 * 捕获后翻译成 40906。「先查再改」在并发下无效，理由与建档一致。
 *
 * <p><b>开事务：</b>一次 {@code findById}、一条 {@code UPDATE} 与一条审计。前两者本可以不进事务，
 * 审计则必须与它们同生共死——只有 {@code AuditTrail} 的实现加入本方法的事务，
 * 「业务改了但没留痕」与「留了痕但业务没改」才都不会发生。这几条语句都在同一个数据源上，
 * 事务边界因此放在这里就够。
 *
 * <p><b>字段一个都没变时不写审计：</b>提交了但没改动，记一条只会稀释信号——事后翻日志时，
 * 「谁把它改了」与「谁点了一次保存」看起来一模一样。判定用领域对象的等值（{@link OwnerProfile}），
 * 不比较快照 {@code Map}：后者把「值为 null」与「键缺失」的区别、以及键顺序都卷进了判定，
 * 一条本来没变的提交会因此被当成变更。
 *
 * <p>字段级的前后值对比不在本用例做：审计记的是完整的变更前后快照，差异由读日志的人（或他的工具）算。
 */
@Service
public class OwnerUpdateService {

    private final OwnerRepository ownerRepository;
    private final OwnerDictionaryValidator ownerDictionaryValidator;
    private final CurrentOperator currentOperator;
    private final AuditTrail auditTrail;

    /**
     * @param ownerRepository 货主仓储端口
     * @param dictLookup      字典窄端口；领域服务无状态，随用例持有即可
     * @param currentOperator 当前操作人端口
     * @param auditTrail      审计留痕端口，由 {@code wms-app} 接到审计能力上
     */
    public OwnerUpdateService(OwnerRepository ownerRepository,
                              DictLookup dictLookup,
                              CurrentOperator currentOperator,
                              AuditTrail auditTrail) {
        this.ownerRepository = ownerRepository;
        this.ownerDictionaryValidator = new OwnerDictionaryValidator(dictLookup);
        this.currentOperator = currentOperator;
        this.auditTrail = auditTrail;
    }

    /**
     * 覆盖货主档案的可编辑部分。
     *
     * @param command 编辑入参；{@code null} 表示清空对应字段
     * @throws BizException 当前会话无效（40100）；货主不存在或已删除（40400）；
     *                      名称/客户类型/结算主体为空、地址必填项缺失、客户类型/国家/州省/邮编
     *                      不满足字典规则（40000）；外部码被其他货主占用（40906）
     */
    @Transactional
    public void update(UpdateOwnerCommand command) {
        long operatorId = currentOperator.currentUserId()
                // 与建档同一理由：取不到操作人就不能继续，updated_by 写成空值后无从追溯
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        Owner existing = ownerRepository.findById(command.ownerId())
                // 已逻辑删除的货主与「不存在」同口径：管理端看不到它，改它也就无从谈起
                .orElseThrow(() -> BizException.notFound("货主不存在"));

        OwnerProfile profile;
        try {
            profile = toProfile(command);
            ownerDictionaryValidator.validate(profile.ownerType(), profile.address());
        } catch (IllegalArgumentException e) {
            // 与建档同一处理：领域层用 IllegalArgumentException 表达输入不合法，翻译成 40000 在用例层
            throw BizException.badRequest(e.getMessage());
        }

        OwnerProfile current = OwnerProfile.of(existing);

        try {
            ownerRepository.update(existing.id(), profile, operatorId);
        } catch (DataIntegrityViolationException e) {
            throw OwnerConstraintTranslator.translateUpdate(e);
        }

        // 提交了但字段一个都没变：库照常写（updated_at 是事实），但不留审计——记一条只会稀释信号
        if (current.equals(profile)) {
            return;
        }
        // before 复用 findById 的结果，不再查一次：那一次读只可能读到别人刚改的值，
        // 把它当成「本次变更前」会写出一条前后不属于同一次提交的日志
        auditTrail.record(OwnerAuditEvent.OWNER_UPDATED, existing.code(),
                OwnerAuditSnapshots.of(existing), OwnerAuditSnapshots.ofUpdated(existing, profile));
    }

    /**
     * 把入参搬成可编辑的档案属性。
     *
     * <p>空串一律落成 {@code null}（见 {@link OwnerTextNormalizer}）：编辑是真的能把外部码清空的操作，
     * 而空串在 {@code ux_owner_external_code} 眼里是一个具体取值，会让第二个不填外部码的货主撞车。
     *
     * @param command 编辑入参
     * @return 覆盖后的档案属性
     * @throws IllegalArgumentException 名称为空、客户类型为空、结算主体为空，或地址必填项缺失
     */
    private static OwnerProfile toProfile(UpdateOwnerCommand command) {
        OwnerAddress address = new OwnerAddress(
                OwnerTextNormalizer.toNullIfBlank(command.country()),
                OwnerTextNormalizer.toNullIfBlank(command.province()),
                OwnerTextNormalizer.toNullIfBlank(command.city()),
                OwnerTextNormalizer.toNullIfBlank(command.district()),
                OwnerTextNormalizer.toNullIfBlank(command.postalCode()),
                OwnerTextNormalizer.toNullIfBlank(command.addressLine()));
        return new OwnerProfile(
                OwnerTextNormalizer.toNullIfBlank(command.name()),
                OwnerTextNormalizer.toNullIfBlank(command.shortName()),
                OwnerTextNormalizer.toNullIfBlank(command.nameEn()),
                OwnerTextNormalizer.toNullIfBlank(command.externalCode()),
                OwnerTextNormalizer.toNullIfBlank(command.ownerType()),
                OwnerTextNormalizer.toNullIfBlank(command.settlementEntity()),
                address,
                OwnerTextNormalizer.toNullIfBlank(command.remark()));
    }
}

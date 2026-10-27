package com.yunwms.auth.application.service;

import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.port.OwnerExistenceQuery;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 数据范围配置的合法性判定：类型与货主明细是否搭配、明细里的货主是否可用。
 *
 * <p><b>为什么单独成类：</b>建角色、改角色范围、改用户范围三个入口共用同一套判定，而它们分属
 * 三个不同的用例类。各写一遍的后果不是重复几行，而是三条入口对「{@code ALL} 能不能带明细」
 * 判得不一样——那种不一致只有把三个接口都打一遍才能发现。
 *
 * <p><b>为什么在 application 而不是 domain：</b>判定要查「货主是否存在且启用」，那是一次跨模块
 * 查询，领域模型不认识外部端口以外的世界；而把这条规则写进 {@code DataScopeType} 又会让它带上
 * 一次 IO。
 *
 * <p>错误码一律 {@code 40000}：拒绝的原因是入参本身不成立（类型与明细矛盾、指向一个不存在或
 * 停用的货主），管理员改一个值就能过，不属于「与既有数据冲突」。
 */
@Service
public class DataScopeOwnerValidator {

    private final OwnerExistenceQuery ownerExistenceQuery;

    public DataScopeOwnerValidator(OwnerExistenceQuery ownerExistenceQuery) {
        this.ownerExistenceQuery = ownerExistenceQuery;
    }

    /**
     * 校验一份数据范围配置。
     *
     * <p>校验顺序固定为「类型必填 → 类型与明细是否搭配 → 明细里的货主是否可用」：前两项只看入参，
     * 第三项要查库，把免费的判定排在前面。
     *
     * <p>{@code CUSTOM} 与空明细是合法组合：规格要求「空明细的角色不吞并其他角色」，
     * 也就是说「指定货主但一个都没选」必须能存下去，效果是无权——拒绝它会逼管理员先选一个
     * 并不想要的货主。
     *
     * @param type     数据范围类型，不能为 {@code null}
     * @param ownerIds 指定货主标识集合，{@code ALL} 与 {@code ASSIGNED} 时必须为空
     * @throws BizException 类型为空、类型与明细矛盾（40000）；明细里有不存在或未启用的货主（40000）
     */
    public void requireConfigurable(DataScopeType type, Collection<Long> ownerIds) {
        if (type == null) {
            throw BizException.badRequest("数据范围类型不能为空");
        }
        Set<Long> requested = ownerIds == null ? Set.of() : new LinkedHashSet<>(ownerIds);
        if (!type.usesOwnerDetails()) {
            if (!requested.isEmpty()) {
                // 「全部货主」与「我负责的货主」是规则而不是名单：留着明细会让「这条明细算不算数」
                // 变成只有读过解析逻辑才能回答的问题，因此宁可拒绝也不静默丢弃
                throw BizException.badRequest("数据范围为 " + type.name() + " 时不能指定货主明细");
            }
            return;
        }
        if (requested.isEmpty()) {
            // 空明细是合法状态（结果为空集），见方法注释
            return;
        }
        Set<Long> existing = ownerExistenceQuery.findExistingActiveIds(requested);
        Set<Long> missing = new LinkedHashSet<>(requested);
        missing.removeAll(existing);
        if (!missing.isEmpty()) {
            // 点出具体标识：只说「有货主不可用」时，管理员无法在几十个勾选里找出是哪一个
            throw BizException.badRequest("指定的货主不存在或未启用：" + missing);
        }
    }
}

package com.yunwms.master.domain.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 货主状态，五态固定，与 {@code ck_owner_status} 的取值集合一致。
 *
 * <p><b>流转表定义在本枚举上，且只此一处：</b>「草稿 → 潜在 / 合作中」「潜在 → 合作中 / 终止」
 * 「合作中 → 暂停 / 终止」「暂停 → 合作中 / 终止」「终止 → 无」。用例层只调
 * {@link #requireTransitionTo}，不自己写 {@code if (current == X && target == Y)}：
 * 规则一旦有了第二个落点，两处迟早对某个组合判得不一样，而那时的表现是「界面拦住了、接口没拦住」。
 *
 * <p><b>同状态视为成功（幂等）：</b>{@link #canTransitionTo} 对 {@code target == this} 恒返回
 * {@code true}，{@link OwnerStatus#TERMINATED} 同样如此。这不是「终止可以回退」——回退到别的状态仍然被拒；
 * 幂等的含义是「重复提交同一个请求不该报错」，与用户启停的口径一致
 * （{@code docs/api-convention.md} 第七节：状态流转的目标状态与当前状态相同时返回成功）。
 * 若把 {@code TERMINATED → TERMINATED} 判成非法，一次网络重试就会给管理员一条「终止为终态」的报错，
 * 而库里其实早已是终止态。
 *
 * <p>数据库存 {@link #name()}，读取到未知编码时失败：库里出现 {@code 'FOO'} 说明数据被绕过
 * CHECK 写坏，静默回落成 {@link #DRAFT} 会让一个已终止的货主重新以草稿身份出现在列表里。
 */
public enum OwnerStatus {

    /** 草稿：不占用 SKU 前缀、不生成主账号、不出现在其他模块的货主下拉中。 */
    DRAFT,

    /** 潜在：已录入但尚未开始合作。 */
    POTENTIAL,

    /** 合作中：正常开展业务。 */
    ACTIVE,

    /** 暂停：临时停止收发货，历史数据照常可查。 */
    SUSPENDED,

    /** 终止：终态，不再发生任何业务；其商品档案保留，但不可用于新的业务单据。 */
    TERMINATED;

    /**
     * 按数据库中的编码还原。
     *
     * @param code 状态编码，取值为枚举名；对应列 {@code owner.status}
     * @return 对应的状态
     * @throws IllegalArgumentException 编码未知，说明数据已损坏，不静默回落
     */
    public static OwnerStatus fromCode(String code) {
        for (OwnerStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的货主状态编码：" + code);
    }

    /**
     * 本状态可直接流转到的目标状态，不含自身。
     *
     * <p>用 {@link EnumSet} 而不是 {@link Set#of}：后者的迭代顺序由哈希决定，把它拼进错误提示时
     * 同一份规则在两次调用里可能给出不同的措辞，而提示是用户会截图对照的东西。{@link EnumSet}
     * 按声明顺序迭代，提示因此稳定。
     *
     * <p>返回的是新集合，调用方可以随意持有或修改，不影响本枚举的规则。
     *
     * @return 目标状态集合；{@link #TERMINATED} 返回空集（终态，只能保持原状态）
     */
    public Set<OwnerStatus> allowedTargets() {
        return switch (this) {
            case DRAFT -> Collections.unmodifiableSet(EnumSet.of(POTENTIAL, ACTIVE));
            case POTENTIAL -> Collections.unmodifiableSet(EnumSet.of(ACTIVE, TERMINATED));
            case ACTIVE -> Collections.unmodifiableSet(EnumSet.of(SUSPENDED, TERMINATED));
            case SUSPENDED -> Collections.unmodifiableSet(EnumSet.of(ACTIVE, TERMINATED));
            case TERMINATED -> Collections.unmodifiableSet(EnumSet.noneOf(OwnerStatus.class));
        };
    }

    /**
     * 判断能否流转到目标状态。
     *
     * @param target 目标状态
     * @return 允许（含同状态的幂等提交）返回 {@code true}
     * @throws IllegalArgumentException 目标状态为 {@code null}
     */
    public boolean canTransitionTo(OwnerStatus target) {
        if (target == null) {
            throw new IllegalArgumentException("目标状态不能为空");
        }
        // 同状态先短路：TERMINATED 的 allowedTargets() 是空集，若不先判自身，
        // 「重复提交终止」会被当成非法流转，与幂等口径相反
        return target == this || allowedTargets().contains(target);
    }

    /**
     * 校验流转是否允许，不允许时抛出说明「当前状态与允许目标」的异常。
     *
     * <p>提示里必须同时给出当前状态与允许的目标状态：只说「不允许」时，管理员不知道该刷新页面
     * 看当前状态，还是该换一个目标；两个都写出来，他能立刻判断是自己点错了还是别人改过这一行。
     *
     * <p>用 {@link IllegalStateException} 而不是 {@link IllegalArgumentException}：目标状态本身是一个
     * 合法取值（已由 {@link #fromCode} 校验过），被拒绝的是它相对当前状态的关系。
     * 两类的错误码不同（409 段位与 40000），用例层据此翻译，不靠异常名猜测。
     *
     * @param target 目标状态
     * @throws IllegalArgumentException 目标状态为 {@code null}
     * @throws IllegalStateException    当前状态不允许流转到目标状态
     */
    public void requireTransitionTo(OwnerStatus target) {
        if (canTransitionTo(target)) {
            return;
        }
        throw new IllegalStateException("货主状态不允许从 " + name() + " 流转到 " + target.name()
                + "；当前状态 " + name() + " 允许的目标状态：" + describeAllowedTargets());
    }

    /**
     * 把允许的目标状态拼成提示里的一段文本。
     *
     * @return 形如 {@code SUSPENDED、TERMINATED}；终态返回一句说明而不是空串（空串会让提示以「：」结尾）
     */
    private String describeAllowedTargets() {
        Set<OwnerStatus> targets = allowedTargets();
        if (targets.isEmpty()) {
            return "无（" + name() + " 为终态，只能保持原状态）";
        }
        return targets.stream().map(OwnerStatus::name).collect(Collectors.joining("、"));
    }
}

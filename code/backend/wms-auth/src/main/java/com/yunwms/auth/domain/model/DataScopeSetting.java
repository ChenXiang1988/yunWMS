package com.yunwms.auth.domain.model;

import java.util.Set;

/**
 * 一份数据范围配置：类型，加上 {@link DataScopeType#CUSTOM} 时的货主明细。
 *
 * <p>角色与用户的配置用同一个模型：两者的字段完全一致，区别只在「用户配置优先于角色配置」这条
 * 优先级规则上，而那条规则属于生效范围的解析（见 {@code DataScopeResolver}），不属于配置本身。
 *
 * <p>非 {@code CUSTOM} 时明细必须为空：{@code sys_data_scope_owner} 的注释写明「ALL 与 ASSIGNED
 * 不落本表」。读到「{@code ALL} 却带着三条明细」说明数据被绕过接口写坏了，此时抛异常而不是
 * 悄悄丢掉明细——丢掉之后界面会显示一份「看起来正常」的配置，问题就被掩盖了。
 *
 * @param type     数据范围类型
 * @param ownerIds {@code CUSTOM} 下的指定货主标识；其他类型恒为空集合
 */
public record DataScopeSetting(DataScopeType type, Set<Long> ownerIds) {

    /**
     * 校验并规范化配置。
     *
     * @throws IllegalArgumentException 类型为 {@code null}，或非 {@code CUSTOM} 却带明细
     */
    public DataScopeSetting {
        if (type == null) {
            throw new IllegalArgumentException("数据范围类型不能为空");
        }
        ownerIds = ownerIds == null ? Set.of() : Set.copyOf(ownerIds);
        if (!type.usesOwnerDetails() && !ownerIds.isEmpty()) {
            throw new IllegalArgumentException("数据范围类型 " + type.name() + " 不应带指定货主明细");
        }
    }
}

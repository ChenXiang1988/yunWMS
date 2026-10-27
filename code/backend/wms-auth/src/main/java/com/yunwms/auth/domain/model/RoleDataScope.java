package com.yunwms.auth.domain.model;

/**
 * 一个<b>启用中</b>角色的数据范围配置，供并集计算使用。
 *
 * <p>只带类型而不带明细：{@link DataScopeType#CUSTOM} 的明细要按角色批量读取（一次 {@code IN} 查询），
 * 塞进这里会让「有哪些角色」与「各角色的明细」两次查询耦合在同一个方法签名上。
 *
 * <p>{@code roleId} 保留下来是因为并集计算需要把 {@code CUSTOM} 的明细按角色对回去：同一个人可能
 * 持有多个 {@code CUSTOM} 角色，明细必须分别取、再合并，不能混成一份。
 *
 * @param roleId 角色标识
 * @param type   该角色的数据范围类型
 */
public record RoleDataScope(long roleId, DataScopeType type) {

    /**
     * 校验必填项。
     *
     * @throws IllegalArgumentException 类型为 {@code null}
     */
    public RoleDataScope {
        if (type == null) {
            throw new IllegalArgumentException("角色数据范围类型不能为空，角色标识：" + roleId);
        }
    }
}

package com.yunwms.auth.domain.model;

/**
 * 数据范围的配置取值，与 {@code sys_role.data_scope_type} / {@code sys_user.data_scope_type}
 * 的取值集合一致。
 *
 * <p>三个取值描述的是「范围怎么算」，不是算完的结果：{@link #ASSIGNED} 每次请求都按
 * {@code owner_assignee} 实时推导，{@link #CUSTOM} 才需要 {@code sys_data_scope_owner} 的明细。
 * 算完的结果由 {@link DataScope} 表达，两者刻意分开——把「我负责的货主」固化成一份名单，
 * 新接货主就得回写所有相关角色，漏一个就少看一个货主。
 *
 * <p>数据库存 {@link #name()}：列是 varchar 且有 CHECK，存 {@code ordinal()} 会在枚举顺序调整后
 * 静默错位。读取到未知编码一律抛异常（见 {@link #fromCode}），不回落默认值——回落成 {@code CUSTOM}
 * 会让一个本该「全部货主」的角色变成无权，回落成 {@code ALL} 则是越权，两个方向的代价不对称。
 */
public enum DataScopeType {

    /** 全部货主。生效范围由 {@link DataScope.All} 表达，并在并集中吞并其他取值。 */
    ALL,

    /** 我负责的货主：按 {@code owner_assignee} 的主副关系实时推导，不落明细。 */
    ASSIGNED,

    /** 指定货主：明细存在 {@code sys_data_scope_owner}。空明细不贡献任何货主，是合法状态。 */
    CUSTOM;

    /**
     * 按数据库与请求体中的编码还原。
     *
     * @param code 编码，取值为枚举名
     * @return 对应的数据范围类型
     * @throws IllegalArgumentException 编码未知，说明数据被绕过 CHECK 写坏，或调用方传了非法值
     */
    public static DataScopeType fromCode(String code) {
        for (DataScopeType type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的数据范围类型：" + code);
    }

    /**
     * 该类型是否使用 {@code sys_data_scope_owner} 的明细。
     *
     * <p>写配置与校验明细都以它为准：{@code ALL} 与 {@code ASSIGNED} 是规则而不是名单，
     * 给它们写明细会让「这条明细到底算不算数」变成只有读过解析逻辑才能回答的问题。
     *
     * @return 仅 {@link #CUSTOM} 返回 {@code true}
     */
    public boolean usesOwnerDetails() {
        return this == CUSTOM;
    }
}

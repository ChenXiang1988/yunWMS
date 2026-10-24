package com.yunwms.auth.domain.model;

/**
 * 角色状态。
 *
 * <p>登录返回的角色标识只取启用中的角色：停用角色不应再把用户导进它授权的菜单，
 * 否则「停用角色」这个动作在登录链路上等于没生效。
 *
 * <p>数据库存 {@link #name()}，列上有 CHECK 约束兜底。
 */
public enum RoleStatus {

    /** 可授权。 */
    ACTIVE,

    /** 已停用，不计入用户的角色列表。 */
    DISABLED;

    /**
     * 按数据库中的编码还原。
     *
     * @param code 状态编码，取值为枚举名
     * @return 对应的状态
     * @throws IllegalArgumentException 编码未知，说明数据已损坏，不静默按启用处理
     */
    public static RoleStatus fromCode(String code) {
        for (RoleStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的角色状态编码：" + code);
    }
}

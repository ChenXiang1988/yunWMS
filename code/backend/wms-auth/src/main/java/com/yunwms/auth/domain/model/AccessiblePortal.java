package com.yunwms.auth.domain.model;

/**
 * 登录后可以进入的前端应用。
 *
 * <p>编码值与前端应用标识一一对应（`console` 仓网端、`portal` 货主端），是前后端之间的契约：
 * 改了编码等于改了路由入口，前端必须同步，因此必须走独立的 code 而不是序号或枚举名。
 */
public enum AccessiblePortal {

    /** 仓网管理端，内部员工使用。 */
    CONSOLE("console"),

    /** 货主端，货主账号使用。 */
    PORTAL("portal");

    private final String code;

    AccessiblePortal(String code) {
        this.code = code;
    }

    /**
     * @return 传给前端的应用编码，用于选择登录后跳转的端
     */
    public String code() {
        return code;
    }
}

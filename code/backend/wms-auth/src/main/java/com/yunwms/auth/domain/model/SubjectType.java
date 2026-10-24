package com.yunwms.auth.domain.model;

import java.util.List;

/**
 * 登录主体类型。
 *
 * <p>内部员工与货主账号同表存储，靠本枚举区分。两类的差异不在「能不能登录」，而在登录名的来源与
 * 可访问端：员工登录名由管理员指定、只进仓网端；货主账号登录名由系统生成、只进货主端。
 * 把可访问端挂在主体类型上，是为了让两端共用同一个登录入口时，判断只有一处——
 * 散落在各处的 if 必然漏改一个。
 *
 * <p>数据库存 {@link #name()}（`STAFF` / `OWNER`），列上有 CHECK 约束兜底。
 */
public enum SubjectType {

    /** 内部员工。 */
    STAFF(List.of(AccessiblePortal.CONSOLE)),

    /** 货主账号，必须绑定唯一货主。 */
    OWNER(List.of(AccessiblePortal.PORTAL));

    private final List<AccessiblePortal> accessiblePortals;

    SubjectType(List<AccessiblePortal> accessiblePortals) {
        this.accessiblePortals = accessiblePortals;
    }

    /**
     * 按数据库与会话中的编码还原。
     *
     * @param code 主体类型编码，取值为枚举名
     * @return 对应的主体类型
     * @throws IllegalArgumentException 编码未知，说明数据或配置已损坏，不静默回落成默认值
     */
    public static SubjectType fromCode(String code) {
        for (SubjectType type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的主体类型编码：" + code);
    }

    /**
     * @return 该主体登录后允许进入的前端应用，顺序即前端可选项的展示顺序
     */
    public List<AccessiblePortal> accessiblePortals() {
        return accessiblePortals;
    }
}

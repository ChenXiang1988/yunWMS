package com.yunwms.auth.domain.model;

/**
 * 审计对象类型：日志里的 {@code biz_type}，与 {@code biz_id} 一起定位「哪一条数据」。
 *
 * <p>新增类型时在此追加，不要用魔法字符串散在各处：查询端点按类型索引
 * （{@code ix_audit_biz (biz_type, biz_id, created_at DESC)}），类型字符串写错不会报错，
 * 只会让某个对象的追溯结果少一批记录。
 *
 * <p>子表也各占一个类型（{@link #OWNER_CONTACT}、{@link #OWNER_TAX}），而不是都挂在父对象上：
 * 规格要求子表变更保存**完整集合快照**，按父对象类型混存会让「联系人被谁删的」无法单独检索。
 *
 * <p>数据库存 {@link #name()}；读取到未知编码时失败，不回落默认值——理由见 {@link AuditAction}。
 */
public enum AuditBizType {

    /** 货主档案。 */
    OWNER,

    /** 货主联系人。 */
    OWNER_CONTACT,

    /** 货主税号。 */
    OWNER_TAX,

    /** 商品档案。 */
    SKU,

    /** 用户账号。 */
    USER,

    /** 角色与授权。 */
    ROLE,

    /** 标签打印记录。 */
    LABEL_PRINT;

    /**
     * 按数据库中的编码还原。
     *
     * @param code 对象类型编码，取值为枚举名
     * @return 对应的对象类型
     * @throws IllegalArgumentException 编码未知，说明数据已损坏，不静默回落
     */
    public static AuditBizType fromCode(String code) {
        for (AuditBizType bizType : values()) {
            if (bizType.name().equals(code)) {
                return bizType;
            }
        }
        throw new IllegalArgumentException("未知的审计对象类型编码：" + code);
    }
}

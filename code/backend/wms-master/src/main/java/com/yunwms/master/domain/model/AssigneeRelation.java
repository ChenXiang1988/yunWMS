package com.yunwms.master.domain.model;

/**
 * 货主负责客服的关系角色：主负责人与副负责人各至多一名。
 *
 * <p><b>为什么用枚举而不是把 {@code PRIMARY} / {@code SECONDARY} 当字符串传：</b>
 * 库里的 {@code owner_assignee.relation_type} 上有 {@code ck_assignee_relation}，只允许这两个取值，
 * 本枚举是那条约束在 Java 侧唯一的对应物。用字符串时，拼错一个字母要等到插入被数据库拒绝才暴露，
 * 而那时错误信息里只有一个列名；用枚举，同一处笔误在编译期就是红的。
 *
 * <p><b>本枚举只回答「角色是什么」，不回答「谁占这个位置」：</b>同一货主每个角色至多一名员工，
 * 由 {@code ux_owner_assignee_role (owner_id, relation_type)} 保证；「已有主负责人时再指定要先转交」
 * 是配置负责人的用例的事，写在这里会让一条唯一性规则有两个落点。
 *
 * <p>数据库存 {@link #name()}，读取到未知编码时失败：库里出现第三种取值说明某条写入绕过了 CHECK，
 * 静默回落成 {@link #PRIMARY} 会让一个副负责人以主负责人的身份出现在列表与数据范围里，
 * 而这类错误在页面上看不出来——它看起来就是一条正常的关系。
 */
public enum AssigneeRelation {

    /**
     * 主负责人：规格要求每个货主有一名，且至多一名。
     *
     * <p>「有一名」按不变量理解（一旦配置负责人，主负责人就在其中），口径与理由见
     * {@link com.yunwms.master.domain.port.OwnerAssigneeRepository} 的类注释。
     */
    PRIMARY("主负责人"),

    /** 副负责人：可选，至多一名。 */
    SECONDARY("副负责人");

    private final String label;

    AssigneeRelation(String label) {
        this.label = label;
    }

    /**
     * 中文标签，供审计摘要与面向用户的提示使用。
     *
     * <p>标签不参与任何判定：判定一律比较枚举本身，标签只出现在需要给人看的句子里，
     * 因此改文案不会改行为。
     *
     * @return 该角色的中文名称
     */
    public String label() {
        return label;
    }

    /**
     * 按数据库编码还原角色。
     *
     * @param code 关系编码，取值为枚举名；对应列 {@code owner_assignee.relation_type}
     * @return 对应的角色
     * @throws IllegalArgumentException 编码未知，说明数据已被绕过 CHECK 写坏，不静默回落
     */
    public static AssigneeRelation fromCode(String code) {
        for (AssigneeRelation relation : values()) {
            if (relation.name().equals(code)) {
                return relation;
            }
        }
        throw new IllegalArgumentException("未知的货主负责人关系编码：" + code);
    }
}

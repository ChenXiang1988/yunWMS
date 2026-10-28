package com.yunwms.master.domain.model;

/**
 * 货主域能产生的审计事件：每个写用例各一个，不再多。
 *
 * <p><b>为什么不复制一份 {@code AuditAction} / {@code AuditBizType}：</b>同一套词汇在两处定义，
 * 改一处就漏一处，而漏掉的那一处不会编译失败——只会让某些日志再也查不出来。本枚举因此只声明
 * 「本模块真的会产生的事件」，对象类型与动作以字符串出现，由 {@code wms-app} 的桥接实现验成
 * {@code wms-auth} 的枚举。
 *
 * <p><b>{@code action} 用字符串是刻意的：</b>{@code master} 拿不到 {@code auth} 的枚举（两模块互不引用），
 * 而把枚举名当字符串传的成本是「拼错只有运行时才知道」。这个成本被移到桥接层：桥接实现按
 * {@code Enum.valueOf} 校验，拼错立刻抛 {@code IllegalStateException}——是编程错误，不是业务错误，
 * 也不会写进一条对象类型或动作对不上的垃圾审计。
 *
 * <p><b>{@link #OWNER_ACCOUNT_OPENED} 的对象类型取 {@code OWNER} 而不是 {@code USER}：</b>按货主编码要能
 * 查到完整链条（建档 → 改状态 → 开号）。记成 {@code USER} 的话，从货主详情追溯时这一条会消失，
 * 而管理员恰恰是从货主页看这件事的。登录名同时写进摘要与快照。
 *
 * <p><b>三个联系人事件同样取 {@code OWNER}，不新增 {@code CONTACT} 对象类型：</b>与上一条同一取舍——
 * 管理端是<b>从货主详情的联系人页签</b>看这些记录的，按货主编码要能查到完整链条
 * （建档 → 改状态 → 改联系人 → 开号）。记成独立类型后，从货主详情追溯时这几条会消失，
 * 而追溯的入口恰恰在那里。代价要说清楚：这三条日志的 {@code biz_id} 只有货主编码，
 * 同一货主下的多条联系人日志只能靠前后快照的内容区分，不靠对象标识。
 *
 * <p><b>{@link #OWNER_ASSIGNEE_CHANGED} 同理取 {@code OWNER}：</b>调整主副负责人是货主详情上的动作，
 * 按编码追溯时要与建档、改状态、改联系人排在同一条链上。代价与联系人那三条相同：
 * {@code biz_id} 只有货主编码，具体换了谁只能从前后快照里的两个员工标识读出
 * （{@code OwnerAuditSnapshots#ofAssignee}）。
 */
public enum OwnerAuditEvent {

    /** 建档：新增一条货主档案。 */
    OWNER_CREATED("OWNER", "CREATE", "新建货主"),

    /** 编辑：覆盖货主档案的可编辑部分。 */
    OWNER_UPDATED("OWNER", "UPDATE", "修改货主"),

    /** 删除草稿：软删除。 */
    OWNER_DELETED("OWNER", "DELETE", "删除货主"),

    /** 状态流转：五态之间的写入口。 */
    OWNER_STATUS_CHANGED("OWNER", "UPDATE", "货主状态流转"),

    /** 开通主账号：绑在货主编码上的账号创建。 */
    OWNER_ACCOUNT_OPENED("OWNER", "CREATE", "开通主账号"),

    /** 新增联系人：货主名下的子资源，对象类型沿用 {@code OWNER}，理由见类注释。 */
    OWNER_CONTACT_CREATED("OWNER", "CREATE", "新增联系人"),

    /** 修改联系人：覆盖五个可编辑字段，置主时还会连带把旧的主联系人清为非主。 */
    OWNER_CONTACT_UPDATED("OWNER", "UPDATE", "修改联系人"),

    /** 删除联系人：软删除，快照只有变更前值。 */
    OWNER_CONTACT_DELETED("OWNER", "DELETE", "删除联系人"),

    /**
     * 调整负责客服：主、副两个角色一次写到目标状态，含「换人」与「撤销副负责人」。
     *
     * <p>动作取 {@code UPDATE} 而不是 {@code DELETE} 或 {@code CREATE}：这两种解读都只对了一半——
     * 一次提交可能同时换掉主负责人（删一行 + 插一行）并清掉副负责人，也可能只是首次指定。
     * 对象仍是同一个货主，变更的本质是「这两个角色现在是谁」，快照的前后值才说明具体动了哪一边。
     */
    OWNER_ASSIGNEE_CHANGED("OWNER", "UPDATE", "调整负责客服");

    private final String bizType;

    private final String action;

    private final String summary;

    OwnerAuditEvent(String bizType, String action, String summary) {
        this.bizType = bizType;
        this.action = action;
        this.summary = summary;
    }

    /**
     * 审计对象类型的编码，取值必须与 {@code AuditBizType} 的枚举名一致。
     *
     * @return 对象类型编码
     */
    public String bizType() {
        return bizType;
    }

    /**
     * 审计动作的编码，取值必须与 {@code AuditAction} 的枚举名一致。
     *
     * @return 动作编码
     */
    public String action() {
        return action;
    }

    /**
     * 摘要模板，不含对象标识。
     *
     * <p>对象标识由桥接层拼在模板之后（{@code "新建货主 OWNER0005"}）：同一个模板在五个事件里各写一遍
     * 带标识的完整句子，等于把标识格式固化在枚举里，而标识从哪来只有调用点知道。
     *
     * @return 摘要模板
     */
    public String summary() {
        return summary;
    }
}

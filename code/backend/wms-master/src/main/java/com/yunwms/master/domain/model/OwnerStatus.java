package com.yunwms.master.domain.model;

/**
 * 货主状态，五态固定，与 {@code ck_owner_status} 的取值集合一致。
 *
 * <p>本批只表达「状态是什么」，不表达「能不能从 A 转到 B」：流转约束属于任务 3.2，
 * 与状态取值放在一起会让两个任务的边界混在一处，且 3.2 需要的流转规则（终止为终态、
 * 草稿可删、提交后 SKU 前缀锁定）牵扯到字段级约束，不适合挂在一个枚举上。
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

    /** 终止：终态，不再发生任何业务。 */
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
}

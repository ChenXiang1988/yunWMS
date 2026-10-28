package com.yunwms.auth.domain.model;

/**
 * 审计动作，对应规格「审计日志」列出的七类操作。
 *
 * <p>取值刻意按规格的七类固定，而不是「每个接口一个动作」：审计要回答的是「发生了什么性质的事」，
 * 不是「调了哪个方法」。后者随接口重构而变，前者是规格本身。
 *
 * <p>数据库存 {@link #name()}，列上有 CHECK 兜底。读取到未知编码时失败，不回落成默认值：
 * 把脏数据当成 {@link #CREATE} 处理，会让一条「有人删了货主」的日志变成「新建了货主」——
 * 审计记录说谎比缺少一条记录严重得多。
 */
public enum AuditAction {

    /** 新增主数据。 */
    CREATE,

    /** 修改主数据。 */
    UPDATE,

    /** 删除主数据。 */
    DELETE,

    /** 审核：状态机流转中带审批语义的动作（提交审核、通过、驳回）。 */
    AUDIT,

    /** 权限变更：用户、角色、授权关系与数据范围的任何改动。 */
    PERMISSION_CHANGE,

    /** 登录相关事件。 */
    LOGIN,

    /** 标签打印。 */
    LABEL_PRINT;

    /**
     * 按数据库中的编码还原。
     *
     * @param code 动作编码，取值为枚举名
     * @return 对应的动作
     * @throws IllegalArgumentException 编码未知，说明数据已损坏，不静默回落
     */
    public static AuditAction fromCode(String code) {
        for (AuditAction action : values()) {
            if (action.name().equals(code)) {
                return action;
            }
        }
        throw new IllegalArgumentException("未知的审计动作编码：" + code);
    }
}

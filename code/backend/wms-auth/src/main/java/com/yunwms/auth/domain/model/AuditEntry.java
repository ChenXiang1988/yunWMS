package com.yunwms.auth.domain.model;

import java.util.Map;

/**
 * 一条待追加的审计日志内容，不含操作人与操作时间。
 *
 * <p>操作人（标识、姓名、来源地址、来源端）不在本类型里：它由 {@code AuditRecorder} 的实现从当前请求
 * 上下文取。让每个调用方各自传一遍，漏传一处就得到一条「不知道谁干的」日志，而漏传不会有编译错误；
 * 来源端更是只有实现层能可靠判定（调用方连自己是怎么被调起来的都不知道）。
 *
 * <p>{@code beforeValue} / {@code afterValue} 由调用方给出**完整快照**：规格要求子表类数据
 * （联系人、税号、合作仓）发生变更时保存变更前后的完整集合，MUST NOT 只记差异——只记差异时
 * 「谁被删了」恰恰不在差异里，事后无法追溯被删除的条目。父对象本身只含变更字段即可。
 *
 * @param bizType     审计对象类型
 * @param bizId       对象标识，落库到 {@code varchar(64)}；按它检索某个对象的全部变更记录
 * @param action      审计动作
 * @param summary     变更摘要，人读的一句话，可为 {@code null}；落库到 {@code varchar(500)}
 * @param beforeValue 变更前快照，{@code null} 表示无（如新增）；落库为 {@code jsonb}
 * @param afterValue  变更后快照，{@code null} 表示无（如删除）；落库为 {@code jsonb}
 */
public record AuditEntry(
        AuditBizType bizType,
        String bizId,
        AuditAction action,
        String summary,
        Map<String, Object> beforeValue,
        Map<String, Object> afterValue) {

    /** 与 {@code sys_audit_log.biz_id} 的列宽一致。 */
    private static final int MAX_BIZ_ID_LENGTH = 64;

    /** 与 {@code sys_audit_log.summary} 的列宽一致。 */
    private static final int MAX_SUMMARY_LENGTH = 500;

    /**
     * 校验「这条日志能不能被表达出来」。
     *
     * <p>判空与判长在这里做，是因为本类型是写入侧的唯一入口：越过它之后就是一条 INSERT，
     * 要么被数据库的列宽拒绝（那时堆栈里只有 SQL 错误，看不出是哪次业务调用），要么被静默截断。
     * 失败抛 {@link IllegalArgumentException} 而不是业务异常：这不是用户能做错的事，
     * 是调用方写错了代码，属于契约声明（见 {@code docs/coding.md} 的「校验与异常」）。
     *
     * @throws IllegalArgumentException 类型、动作或对象标识为空，标识为空白或超长，摘要超长
     */
    public AuditEntry {
        if (bizType == null) {
            throw new IllegalArgumentException("审计日志缺少对象类型");
        }
        if (action == null) {
            throw new IllegalArgumentException("审计日志缺少动作，对象类型=" + bizType);
        }
        if (bizId == null || bizId.isBlank()) {
            throw new IllegalArgumentException("审计日志缺少对象标识，对象类型=" + bizType + "，动作=" + action);
        }
        if (bizId.length() > MAX_BIZ_ID_LENGTH) {
            throw new IllegalArgumentException("审计日志的对象标识超过 " + MAX_BIZ_ID_LENGTH
                    + " 个字符，对象类型=" + bizType);
        }
        if (summary != null && summary.length() > MAX_SUMMARY_LENGTH) {
            throw new IllegalArgumentException("审计日志的摘要超过 " + MAX_SUMMARY_LENGTH
                    + " 个字符，对象类型=" + bizType + "，对象标识=" + bizId);
        }
    }
}

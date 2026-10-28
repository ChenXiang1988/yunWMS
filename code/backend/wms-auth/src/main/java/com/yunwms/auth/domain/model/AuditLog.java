package com.yunwms.auth.domain.model;

import java.time.Instant;
import java.util.Map;

/**
 * 一条已落库的审计日志（读模型）。
 *
 * <p>与写入侧的 {@link AuditEntry} 分开：写模型里没有标识、操作人与时间——它们由实现层补全，
 * 调用方给不出来；读模型里必须有，否则回查时无法回答「谁、什么时候」。两者复用同一个类型会让
 * 「哪些字段由调用方决定」变成要看每个字段的注释才能回答的问题。
 *
 * <p>操作人姓名是**写入当时的快照**（见 {@code sys_audit_log.operator_name} 的列注释）：
 * 人员之后改名不会改写历史记录。它与 {@code operatorId} 同时保留，前者供人读、后者供精确关联。
 *
 * @param id           日志标识
 * @param bizType      审计对象类型
 * @param bizId        对象标识
 * @param action       审计动作
 * @param operatorId   操作人标识；系统内部触发时为 {@code null}
 * @param operatorName 操作人姓名，写入当时的快照，恒不为 {@code null}
 * @param operatorIp   来源地址；取不到当前请求时为 {@code null}
 * @param sourcePortal 请求来源端；系统内部触发或来源无法判定时为 {@code null}
 * @param summary      变更摘要，可为 {@code null}
 * @param beforeValue  变更前快照；无变更前值（如新增）时为 {@code null}，与「变更前是空对象」区分开
 * @param afterValue   变更后快照；无变更后值（如删除）时为 {@code null}
 * @param createdAt    操作时间，按 UTC 记录
 */
public record AuditLog(
        long id,
        AuditBizType bizType,
        String bizId,
        AuditAction action,
        Long operatorId,
        String operatorName,
        String operatorIp,
        SourcePortal sourcePortal,
        String summary,
        Map<String, Object> beforeValue,
        Map<String, Object> afterValue,
        Instant createdAt) {
}

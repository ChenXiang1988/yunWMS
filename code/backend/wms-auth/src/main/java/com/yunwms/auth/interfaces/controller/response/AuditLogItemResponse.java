package com.yunwms.auth.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * 审计日志列表的一行。
 *
 * <p>标识一律以字符串传输（见 {@code docs/api-convention.md} 第五节）：超过 2^53 的整数在 JS 里会丢精度。
 *
 * <p>操作时间用 {@link Instant} 原样返回，由 Jackson 按 UTC 序列化成 ISO-8601 字符串；
 * 展示层的时区转换属于前端，后端不做。
 *
 * @param id           日志标识，字符串形式
 * @param bizType      审计对象类型编码，取值见后端的对象类型枚举名，如 {@code OWNER}
 * @param bizId        对象标识，如 {@code OWNER0001}
 * @param action       审计动作编码，取值见后端的动作枚举名，如 {@code UPDATE}
 * @param operatorId   操作人标识，字符串形式；系统内部触发时为 {@code null}
 * @param operatorName 操作人姓名，写入当时的快照，不随人员改名变化
 * @param operatorIp   来源地址；取不到当前请求时为 {@code null}
 * @param sourcePortal 来源端编码：{@code CONSOLE} 仓网端、{@code PORTAL} 货主端；
 *                     系统内部触发或来源无法判定时为 {@code null}
 * @param summary      变更摘要；没有摘要时为 {@code null}
 * @param beforeValue  变更前快照；无变更前值（如新增）时为 {@code null}
 * @param afterValue   变更后快照；无变更后值（如删除）时为 {@code null}
 * @param createdAt    操作时间，ISO-8601 UTC
 */
public record AuditLogItemResponse(
        @Schema(description = "日志标识，字符串形式的十进制整数")
        String id,

        @Schema(description = "审计对象类型编码，如 OWNER、OWNER_CONTACT、SKU、USER", example = "OWNER")
        String bizType,

        @Schema(description = "对象标识，与 bizType 一起定位被审计的那条数据", example = "OWNER0001")
        String bizId,

        @Schema(description = "审计动作编码：CREATE 新增、UPDATE 修改、DELETE 删除、AUDIT 审核、"
                + "PERMISSION_CHANGE 权限变更、LOGIN 登录、LABEL_PRINT 标签打印", example = "UPDATE")
        String action,

        @Schema(description = "操作人标识，字符串形式的十进制整数；系统内部触发为 null")
        String operatorId,

        @Schema(description = "操作人姓名，写入当时的快照，不随人员之后改名而变化")
        String operatorName,

        @Schema(description = "来源地址；取不到当前请求时为 null")
        String operatorIp,

        @Schema(description = "来源端编码：CONSOLE 仓网端、PORTAL 货主端；系统内部触发或来源无法判定时为 null",
                example = "CONSOLE")
        String sourcePortal,

        @Schema(description = "变更摘要，人读的一句话；没有摘要时为 null")
        String summary,

        @Schema(description = "变更前快照，原样返回；无变更前值（如新增）时为 null。子表类数据的快照是完整集合，"
                + "不是差异")
        Map<String, Object> beforeValue,

        @Schema(description = "变更后快照，原样返回；无变更后值（如删除）时为 null")
        Map<String, Object> afterValue,

        @Schema(description = "操作时间，ISO-8601 UTC 字符串")
        Instant createdAt) {
}

package com.yunwms.master.interfaces.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 货主负责客服的写入请求体。
 *
 * <p><b>语义是全量替换：</b>一次提交同时给出主、副两个角色的目标值，而不是「这次改哪一个角色」。
 * 理由见 {@code AssignOwnerAssigneeCommand}——部分更新必须区分「没传」与「传了空值表示清除」，
 * 而 JSON 反序列化与 {@code record} 都不携带这个区别，前端记错一次就会静默清掉主负责人。
 * 因此省略 {@code secondaryUserId} 与显式传 {@code null} 是同一件事：该货主不设副负责人。
 *
 * <p><b>没有 {@code ownerId}：</b>归属取路径
 * {@code /api/console/owners/{ownerId}/assignees}。请求体里再带一份就出现「路径说 A、身体说 B」，
 * 而两边不一致时没有依据说哪一处算准——写入口的归属只认路径（与
 * {@code OwnerContactSaveRequest} 同一口径）。
 *
 * <p><b>两个标识以字符串接收，转换停在 Controller：</b>{@code docs/api-convention.md} 第五节要求
 * JSON 里的标识是字符串，超过 2^53 的 {@code Long} 在 JS 里会丢精度；而 application 与 domain 只认识
 * {@code Long}。解析失败（非十进制整数、超出 {@code Long} 范围、只填空白）一律返回 40000 并带出字段名，
 * 不让 {@code NumberFormatException} 冒到全局兜底变成 50000——那会把「调用方传错了」伪装成「服务端故障」。
 * 不在这里用 {@code @Pattern} 表达格式：正则挡不住超出 {@code Long} 范围的值，那一类仍要在解析处翻译，
 * 两处并存只会让「什么算合法标识」有两个定义。
 *
 * <p><b>{@code primaryUserId} 的 {@code @NotBlank} 只表达 HTTP 侧的「必填」：</b>缺省与整串空白在入口
 * 就被拒，不必先把它当成一次「撤销主负责人」的提交再走一遍用例。这条不变量在用例里还有一处落点
 * （{@code OwnerAssigneeService#assign}），两处文案不同但都指向同一件事：主负责人不能为空。
 *
 * @param primaryUserId   主负责人的员工标识，必填，字符串形式的十进制整数；主负责人不可撤销，
 *                        换人只能通过提交另一名员工完成
 * @param secondaryUserId 副负责人的员工标识，字符串形式的十进制整数；不传或传 {@code null}
 *                        表示该货主不设副负责人，已有的那一段关系会被清掉
 */
public record OwnerAssigneeSaveRequest(

        @NotBlank(message = "主负责人不能为空")
        @Schema(description = "主负责人的员工标识，必填，以字符串传输：字符串形式的十进制整数，如 \"7\"。"
                + "主负责人不能撤销——换人请提交另一名员工，省略该字段或传空白返回 40000；"
                + "不是十进制整数的字符串同样返回 40000", example = "7")
        String primaryUserId,

        @Schema(description = "副负责人的员工标识，以字符串传输：字符串形式的十进制整数，如 \"9\"。"
                + "不传或传 null 表示该货主不设副负责人，已有的副负责人会被清除；"
                + "传了值就必须是可解析的十进制整数，否则返回 40000", example = "9")
        String secondaryUserId) {
}

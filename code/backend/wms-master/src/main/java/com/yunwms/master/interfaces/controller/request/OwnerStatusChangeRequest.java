package com.yunwms.master.interfaces.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 货主状态流转的请求体。
 *
 * <p><b>没有 {@code status} 或 {@code currentStatus}：</b>当前状态由服务端读库决定，不由调用方声明。
 * 让调用方带上「它以为的当前状态」会引入两种真相——一旦与库里不一致，要么按旧状态判流转，
 * 要么把一次并发改动报成一次参数错误，而正确做法永远是「按现在这个事实判」。
 *
 * <p>{@code targetStatus} 用字符串接收而不是枚举，理由见 {@code ChangeOwnerStatusCommand}：
 * 未知取值要能给出带原值的提示，而不是一句泛化的「请求格式不正确」。
 *
 * @param targetStatus       目标状态编码，取 {@code OwnerStatus} 的枚举名
 * @param acknowledgedImpact 是否已确认终止影响清单；只在目标为终止时有意义，缺省按未确认处理
 */
public record OwnerStatusChangeRequest(

        @NotBlank(message = "目标状态不能为空")
        @Schema(description = "目标状态，必填，取状态枚举名：POTENTIAL（潜在）、ACTIVE（合作中）、"
                + "SUSPENDED（暂停）、TERMINATED（终止）。允许的流向：草稿→潜在/合作中，"
                + "潜在→合作中/终止，合作中→暂停/终止，暂停→合作中/终止，终止为终态。"
                + "提交与当前相同的状态按成功处理（幂等），未知取值返回 40000，"
                + "不允许的流向返回 40900 且提示里带当前状态与允许的目标状态", example = "SUSPENDED")
        String targetStatus,

        @Schema(description = "是否已看过终止影响清单并确认。只在目标状态为 TERMINATED 时生效："
                + "未传或 false 时接口返回 40900，提示里带该货主下的商品数与账号数；"
                + "其余目标状态忽略本字段。重复提交终止（当前已是终止）不要求确认，按幂等成功处理",
                example = "false")
        Boolean acknowledgedImpact) {
}

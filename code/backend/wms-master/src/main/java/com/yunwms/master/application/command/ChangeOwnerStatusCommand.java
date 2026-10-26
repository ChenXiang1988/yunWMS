package com.yunwms.master.application.command;

/**
 * 货主状态流转的入参。
 *
 * <p><b>为什么 {@code targetStatus} 是字符串而不是枚举：</b>枚举由 Jackson 直接绑定时，一个未知取值
 * 会在反序列化阶段就失败，落到一句泛化的「请求格式不正确」，用户看不到是哪个值不合法；
 * 而已知取值反而要再判一次「哪些状态不能作为流转目标」。用字符串收进来、由用例经
 * {@code OwnerStatus.fromCode} 解析，未知值能给出带原值的提示，与列表接口的状态筛选用同一条路径。
 *
 * <p><b>{@code acknowledgedImpact} 的含义：</b>调用方已看过终止影响清单并确认。它在
 * {@code targetStatus = TERMINATED} 时才有意义，其余目标状态忽略该字段——把「确认」做成所有流转
 * 的必填步骤，会训练出一种无意识的勾选，终止那一次也就跟着失去分量。
 *
 * @param ownerId            被流转的货主标识
 * @param targetStatus       目标状态编码，取 {@code OwnerStatus} 的枚举名
 * @param acknowledgedImpact 是否已确认终止影响清单；只影响终止
 */
public record ChangeOwnerStatusCommand(long ownerId, String targetStatus, boolean acknowledgedImpact) {
}

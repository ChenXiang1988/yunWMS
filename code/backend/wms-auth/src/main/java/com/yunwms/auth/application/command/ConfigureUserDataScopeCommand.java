package com.yunwms.auth.application.command;

import com.yunwms.auth.domain.model.DataScopeType;

import java.util.List;

/**
 * 配置用户级数据范围覆盖的入参。
 *
 * <p>{@code dataScopeType} 为 {@code null} 表示<b>清除覆盖</b>，效果回落到角色的并集。它与
 * 「{@code CUSTOM} 空明细」是两种不同的状态：前者是「没有覆盖」，后者是「有覆盖且一个货主都不给」，
 * 后者的结果是无权。用一个 {@code null} 表达清除，正是为了让这两种意图在协议上就能区分。
 *
 * @param userId        用户标识
 * @param dataScopeType 目标类型；{@code null} 表示清除覆盖
 * @param ownerIds      {@code CUSTOM} 时的指定货主标识；其他类型（含 {@code null}）必须为空
 */
public record ConfigureUserDataScopeCommand(long userId, DataScopeType dataScopeType, List<Long> ownerIds) {
}

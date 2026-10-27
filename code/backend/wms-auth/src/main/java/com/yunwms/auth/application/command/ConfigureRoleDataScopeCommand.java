package com.yunwms.auth.application.command;

import com.yunwms.auth.domain.model.DataScopeType;

import java.util.List;

/**
 * 配置角色数据范围的入参。
 *
 * <p>独立成一条命令而不是塞进 {@link UpdateRoleCommand}：编辑资料与配置数据范围是两件事，
 * 合在一起之后「只改名称」的请求必须把范围也一起提交，漏传就等于清空——而漏传是很容易发生的，
 * 界面上少渲染一个字段就够了。
 *
 * <p>整批替换语义：提交的类型与明细就是该角色此后的全部范围。
 *
 * @param roleId        角色标识
 * @param dataScopeType 目标类型，不能为 {@code null}——「不配」没有意义，角色范围总有取值
 * @param ownerIds      {@code CUSTOM} 时的指定货主标识；其他类型必须为空
 */
public record ConfigureRoleDataScopeCommand(long roleId, DataScopeType dataScopeType, List<Long> ownerIds) {
}

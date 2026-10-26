package com.yunwms.auth.application.command;

import com.yunwms.auth.domain.model.RoleStatus;

/**
 * 启用或停用角色的入参。
 *
 * <p>只携带目标状态，不携带「当前状态」：状态流转是幂等的，用例以库里的事实为准判断是否需要写库，
 * 让调用方把它读到的当前状态传进来，等于允许一次基于过期快照的写入。
 *
 * @param roleId 角色标识
 * @param status 目标状态
 */
public record ChangeRoleStatusCommand(long roleId, RoleStatus status) {
}

package com.yunwms.auth.interfaces.controller.request;

import com.yunwms.auth.domain.model.RoleStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 启用或停用角色的请求体：{@code {"status": "DISABLED"}}。
 *
 * <p>{@code status} 声明为必传：省略字段与传错值都必须被打回，而不是「不传就不改」——
 * 后者会让一次「以为停用了」的操作变成空操作，而管理员已经把这个角色当成已停用。
 *
 * <p>状态值取自 {@link RoleStatus} 的枚举名，未知编码在反序列化阶段就失败，不会落进业务层。
 *
 * @param status 目标状态：{@code ACTIVE} 启用，{@code DISABLED} 停用
 */
public record ChangeRoleStatusRequest(
        @NotNull(message = "目标状态不能为空")
        RoleStatus status) {
}

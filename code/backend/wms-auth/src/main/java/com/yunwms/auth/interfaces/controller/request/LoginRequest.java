package com.yunwms.auth.interfaces.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求。
 *
 * <p>两类主体共用：请求体里没有主体类型字段，类型由后端判定。前端如果传了主体类型，
 * 说明契约被改坏了，应当被 {@code FAIL_ON_UNKNOWN_PROPERTIES} 直接拒掉。
 *
 * @param loginName 登录名；员工由管理员指定，货主账号由系统生成
 * @param password  明文密码，仅在传输层出现，服务端不落库、不写日志
 */
public record LoginRequest(
        @NotBlank(message = "登录名不能为空")
        String loginName,

        @NotBlank(message = "密码不能为空")
        String password) {
}

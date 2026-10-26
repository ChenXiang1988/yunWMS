package com.yunwms.auth.interfaces.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
        @Schema(description = "登录名，不能为空；员工账号由管理员在仓网端指定，货主账号由系统生成。全局唯一且不区分大小写")
        String loginName,

        @NotBlank(message = "密码不能为空")
        @Schema(description = "明文密码，不能为空，仅在本次请求体中出现；服务端只保存哈希，不落库明文、不写日志、不回显")
        String password) {
}

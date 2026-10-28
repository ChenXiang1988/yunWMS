package com.yunwms.auth.interfaces.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 *
 * <p>两类主体共用：请求体里没有主体类型字段，类型由后端判定。前端如果传了主体类型，
 * 说明契约被改坏了，应当被 {@code FAIL_ON_UNKNOWN_PROPERTIES} 直接拒掉。
 *
 * <p>登录名限长 64，与 {@code sys_user.login_name} 的列宽一致：登录失败要写审计，对象标识用的是
 * 用户尝试的登录名，而 {@code sys_audit_log.biz_id} 同为 {@code varchar(64)}。超长的名字不可能是
 * 已存在的账号，在入口按格式拒绝（与「登录名为空」同一口径），比让它在审计写入处撞列宽、
 * 把一个本应正常的 40101 变成 500 要好。
 *
 * @param loginName 登录名；员工由管理员指定，货主账号由系统生成
 * @param password  明文密码，仅在传输层出现，服务端不落库、不写日志
 */
public record LoginRequest(
        @NotBlank(message = "登录名不能为空")
        @Size(max = 64, message = "登录名不能超过 64 个字符")
        @Schema(description = "登录名，不能为空，最长 64 个字符；员工账号由管理员在仓网端指定，货主账号由系统生成。"
                + "全局唯一且不区分大小写")
        String loginName,

        @NotBlank(message = "密码不能为空")
        @Schema(description = "明文密码，不能为空，仅在本次请求体中出现；服务端只保存哈希，不落库明文、不写日志、不回显")
        String password) {
}

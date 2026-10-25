package com.yunwms.auth.interfaces.controller.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员重置他人密码的请求体：{@code {"newPassword": "Init@1234"}}。
 *
 * <p>只有非空这一层校验：长度与字符组合属于密码策略，由用例统一判定。在注解里再写一遍，
 * 同一份规则就会有两处定义，改一处忘一处时表现成「接口放过了、用例又拒了」。
 *
 * <p>没有用户标识字段，目标在路径里；也没有旧密码字段——本接口不验旧密码，
 * 正因如此它不能作用于操作人自己（见用例层的保护）。
 *
 * @param newPassword 新密码明文，需满足密码策略（8–20 位且含大写、小写与数字）
 */
public record ResetUserPasswordRequest(
        @NotBlank(message = "新密码不能为空")
        String newPassword) {
}

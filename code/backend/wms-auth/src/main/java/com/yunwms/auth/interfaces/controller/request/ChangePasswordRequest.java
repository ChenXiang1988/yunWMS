package com.yunwms.auth.interfaces.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 本人改密请求。
 *
 * <p>只做「两个字段都非空」这一层：长度与字符组合属于密码策略，由用例统一判定，
 * 否则同一份规则会在注解与领域对象里各写一遍，改一处忘一处。
 *
 * <p>没有用户标识字段，多余字段由 {@code FAIL_ON_UNKNOWN_PROPERTIES} 直接拒绝——
 * 前端若试图传 {@code userId} 改别人的密码，应当在协议层就被打回。
 *
 * @param oldPassword 当前密码明文
 * @param newPassword 新密码明文
 */
public record ChangePasswordRequest(
        @NotBlank(message = "当前密码不能为空")
        @Schema(description = "当前密码明文，不能为空；用于证明操作人本人，与登录共用同一套失败计数，连续错误会触发锁定")
        String oldPassword,

        @NotBlank(message = "新密码不能为空")
        @Schema(description = "新密码明文，不能为空，需满足密码策略：8–20 位且同时包含大写字母、小写字母与数字，"
                + "且不能与当前密码相同；不合规时一次返回全部违规项")
        String newPassword) {
}

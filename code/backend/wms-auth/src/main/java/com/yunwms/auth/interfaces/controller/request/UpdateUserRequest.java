package com.yunwms.auth.interfaces.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 编辑用户基本信息的请求体：{@code {"displayName": "张三", "email": "z@example.com",
 * "wecomUserId": "wx_zhangsan"}}。
 *
 * <p><b>只有这三个字段</b>，且语义是全量替换。登录名、主体类型、所属货主都不在请求体里：
 * 登录名是账号标识（改了等于换账号），主体类型与所属货主决定数据归属（改了等于把账号迁移到
 * 另一个货主名下）。它们不是「不可编辑」的界面细节，而是另一件事，需要自己的用例与确认流程。
 * 全局开了 {@code FAIL_ON_UNKNOWN_PROPERTIES}，因此前端多传 {@code loginName} 会被直接拒绝，
 * 而不是悄悄忽略后让管理员以为改名生效了。
 *
 * <p>只声明单字段约束：邮箱是否必填取决于主体类型，那条规则与 {@code ck_user_email_required}
 * 同一口径，属于用例层，写在注解里就只能被 HTTP 入口看到。
 *
 * @param displayName 展示名，必填
 * @param email       邮箱；内部员工必填，货主账号可为 {@code null}，传 {@code null} 表示清空
 * @param wecomUserId 企业微信 userid，可为 {@code null}，传 {@code null} 表示清空
 */
public record UpdateUserRequest(
        @NotBlank(message = "展示名不能为空")
        @Size(max = 64, message = "展示名不能超过 64 个字符")
        @Schema(description = "展示名，必填，最长 64 个字符。全量替换语义：提交什么就是改后的值")
        String displayName,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱不能超过 128 个字符")
        @Schema(description = "邮箱，最长 128 个字符，格式需合法。全量替换语义：传 null 表示清空；"
                + "内部员工必须保留非空邮箱，清空返回 40000；被其他账号占用返回 40903")
        String email,

        @Size(max = 64, message = "企业微信 userid 不能超过 64 个字符")
        @Schema(description = "企业微信 userid，最长 64 个字符。全量替换语义：传 null 表示清空，不传同样按 null 处理")
        String wecomUserId) {
}

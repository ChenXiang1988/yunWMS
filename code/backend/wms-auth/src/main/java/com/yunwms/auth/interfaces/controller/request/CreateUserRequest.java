package com.yunwms.auth.interfaces.controller.request;

import com.yunwms.auth.domain.model.SubjectType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 管理员建号的请求体。
 *
 * <p>这里只声明单字段约束：必填、长度、邮箱格式。主体与货主的搭配、内部员工必须有邮箱这类
 * 多字段规则不放在注解上——它们与 {@code sys_user} 的 CHECK 约束是同一口径的业务规则，
 * 建成账号的其他入口（批量导入、货主开通主账号）将来也要用，写在注解里就只能被 HTTP 入口看到。
 *
 * <p>字段名与 {@code sys_user} 的列一一对应（camelCase），多余字段由
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} 直接拒绝，避免前端以为传了某个字段就算生效。
 *
 * <p>不接收 {@code mustChangePassword} 与 {@code status}：初始密码必须首次登录修改、新账号一律启用，
 * 这是建号这一动作的固有语义，不是可选参数。
 *
 * @param loginName       登录名，员工手填；全局唯一且不区分大小写
 * @param displayName     展示名
 * @param subjectType     主体类型：{@code STAFF} 内部员工，{@code OWNER} 货主用户
 * @param ownerId         所属货主标识；货主用户必填，内部员工必须不传
 * @param email           邮箱；内部员工必填（用于自助找回密码），货主用户可为空
 * @param wecomUserId     企业微信 userid，内部员工用于应用消息推送，可为空
 * @param initialPassword 初始密码明文，需满足密码策略（8–20 位且含大写、小写与数字）
 * @param roleIds         要分配的角色标识；为空或省略表示不分配角色
 */
public record CreateUserRequest(
        @NotBlank(message = "登录名不能为空")
        @Size(max = 64, message = "登录名不能超过 64 个字符")
        @Schema(description = "登录名，必填，最长 64 个字符；全局唯一且不区分大小写，已被占用返回 40901")
        String loginName,

        @NotBlank(message = "展示名不能为空")
        @Size(max = 64, message = "展示名不能超过 64 个字符")
        @Schema(description = "展示名，必填，最长 64 个字符；界面上显示给人看的名字，可与登录名不同")
        String displayName,

        @NotNull(message = "主体类型不能为空")
        @Schema(description = "主体类型，必填：STAFF 内部员工，OWNER 货主用户。决定下面 ownerId 与 email 是否必填，"
                + "创建后不可修改")
        SubjectType subjectType,

        @Schema(description = "所属货主标识，以字符串传输：货主用户必填，内部员工必须不传。"
                + "取值来自货主列表，创建后不可修改；不是十进制整数的字符串返回 40000",
                example = "5")
        String ownerId,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱不能超过 128 个字符")
        @Schema(description = "邮箱，最长 128 个字符，格式需合法：内部员工必填（用于自助找回密码），货主用户可为空；"
                + "全局唯一，已被其他账号使用返回 40903")
        String email,

        @Size(max = 64, message = "企业微信 userid 不能超过 64 个字符")
        @Schema(description = "企业微信 userid，最长 64 个字符，内部员工用于应用消息推送，可为空")
        String wecomUserId,

        @NotBlank(message = "初始密码不能为空")
        @Schema(description = "初始密码明文，必填，需满足密码策略：8–20 位且同时包含大写字母、小写字母与数字；"
                + "账号落地时只存哈希，且被标记为首次登录必须修改")
        String initialPassword,

        @Schema(description = "要分配的角色标识，以字符串传输：数组元素是字符串形式的十进制整数，"
                + "形如 [\"2\", \"3\"]；为空或省略表示不分配角色。角色必须存在，"
                + "元素不是十进制整数的字符串返回 40000")
        List<@NotNull(message = "角色标识不能为空") String> roleIds) {
}

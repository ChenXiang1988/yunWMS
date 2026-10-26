package com.yunwms.auth.interfaces.controller.request;

import com.yunwms.auth.domain.model.NewRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 建角色的请求体。
 *
 * <p>字段名与 {@code sys_role} 的列一一对应（camelCase），多余字段由
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} 直接拒绝。
 *
 * <p><b>没有 {@code builtin}、{@code status} 与 {@code dataScopeType}</b>：接口不能创建内置角色
 * （否则等于绕开权限体系造一个超级管理员），新建角色一律启用，数据范围配置属于 2.4。
 * 前端传了这些字段会被当成未知字段拒绝，而不是「收下但忽略」——忽略会让前端以为改生效了。
 *
 * <p>编码的正则直接引用 {@link NewRole#CODE_PATTERN}：规则只有一份定义，注解与用例层判定
 * 不会各自漂移。长度上限由正则覆盖（最长 32 位，列宽 64），因此不再重复写 {@code @Size}。
 *
 * @param code    角色编码，格式见 {@link NewRole#CODE_PATTERN}
 * @param name    角色名称
 * @param remark  备注，可为空
 * @param menuIds 勾选的菜单与权限点标识；为空或省略表示不授权任何权限
 */
public record CreateRoleRequest(
        @NotBlank(message = "角色编码不能为空")
        @Pattern(regexp = NewRole.CODE_PATTERN, message = NewRole.CODE_FORMAT_HINT)
        @Schema(description = "角色编码，必填：需以大写字母开头，只能包含大写字母、数字与下划线，长度 2 到 32 位。"
                + "程序按编码引用角色，创建后不可修改；全局唯一（含已删除的角色），已被占用返回 40902")
        String code,

        @NotBlank(message = "角色名称不能为空")
        @Size(max = 64, message = "角色名称不能超过 64 个字符")
        @Schema(description = "角色名称，必填，最长 64 个字符；给人看的名字，可重名")
        String name,

        @Size(max = 255, message = "备注不能超过 255 个字符")
        @Schema(description = "备注，最长 255 个字符，可为空")
        String remark,

        @Schema(description = "勾选的菜单与权限点标识（数组元素为字符串形式的十进制整数）；为空或省略表示不授权任何权限。"
                + "元素必须存在，否则返回 40000")
        List<@NotNull(message = "菜单标识不能为空") Long> menuIds) {
}

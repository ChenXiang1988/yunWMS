package com.yunwms.auth.interfaces.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 编辑角色的请求体：{@code {"name": "客服", "remark": "...", "menuIds": ["4", "5"]}}。
 *
 * <p><b>没有 {@code code}</b>，且语义是全量替换。编码是程序引用角色的标识，与用户的登录名同一口径：
 * 改了等于换一个角色，配置里写死的编码会指向一个不存在的角色。全局开了
 * {@code FAIL_ON_UNKNOWN_PROPERTIES}，因此前端多传 {@code code} 会被直接拒绝，
 * 而不是悄悄忽略后让管理员以为改名生效了。
 *
 * <p>{@code menuIds} 声明为必传：省略字段与传空数组在 HTTP 上都能表示「没有权限点」，
 * 但省略更可能是前端没想清楚要提交什么，而它的后果是「悄悄清空了这个角色的全部授权」。
 * 空数组是明确的意图表达，因此合法。
 *
 * <p>元素标识按契约以字符串传输：{@code docs/api-convention.md} 第五节要求标识在 JSON 里是字符串，
 * 超过 2^53 的 Long 在 JS 里会丢精度。字符串是协议事实，因此请求体保持 {@code String}，
 * 由 Controller 在构造 Command 前经 {@link RequestIds} 解析为 {@code Long}，用例层只见数值标识。
 *
 * @param name    角色名称，必填
 * @param remark  备注，可为 {@code null}，传 {@code null} 表示清空
 * @param menuIds 替换后的菜单与权限点标识，字符串形式；空数组合法，表示清空该角色的全部授权
 */
public record UpdateRoleRequest(
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 64, message = "角色名称不能超过 64 个字符")
        @Schema(description = "角色名称，必填，最长 64 个字符。全量替换语义：提交什么就是改后的值")
        String name,

        @Size(max = 255, message = "备注不能超过 255 个字符")
        @Schema(description = "备注，最长 255 个字符。全量替换语义：传 null 表示清空，不传同样按 null 处理")
        String remark,

        @NotNull(message = "菜单标识集合不能省略，清空授权请传空数组")
        @Schema(description = "替换后的菜单与权限点标识集合，必传，以字符串传输：元素是字符串形式的十进制整数，"
                + "形如 [\"4\", \"5\"]；语义是整批替换而非增量，空数组合法且表示清空该角色的全部授权，"
                + "省略字段则被拒绝；元素不是十进制整数的字符串返回 40000")
        List<@NotNull(message = "菜单标识不能为空") String> menuIds) {
}

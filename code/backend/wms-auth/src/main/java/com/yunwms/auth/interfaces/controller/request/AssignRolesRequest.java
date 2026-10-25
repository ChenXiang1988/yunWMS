package com.yunwms.auth.interfaces.controller.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 分配角色的请求体：{@code {"roleIds": ["2", "3"]}}。
 *
 * <p>语义是<b>整批替换</b>（见 {@code AssignRolesCommand}），因此没有增量标识字段。
 * {@code roleIds} 声明为必传：省略字段与传空数组在 HTTP 上都能表示「没有角色」，
 * 但省略更可能是前端没想清楚要提交什么，而它的后果是「悄悄清空了这个人的全部角色」。
 * 空数组是明确的意图表达，因此合法。
 *
 * <p>元素标识按契约以字符串传输、由 Jackson 反序列化为 {@code Long}：
 * {@code docs/api-convention.md} 第五节要求标识在 JSON 里是字符串，超过 2^53 的 Long
 * 在 JS 里会丢精度。
 *
 * @param roleIds 替换后的角色标识；空数组合法，表示移除该用户的全部角色
 */
public record AssignRolesRequest(
        @NotNull(message = "角色标识集合不能省略，移除全部角色请传空数组")
        List<@NotNull(message = "角色标识不能为空") Long> roleIds) {
}

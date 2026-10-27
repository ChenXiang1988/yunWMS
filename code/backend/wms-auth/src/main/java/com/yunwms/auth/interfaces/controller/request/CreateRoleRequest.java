package com.yunwms.auth.interfaces.controller.request;

import com.yunwms.auth.domain.model.DataScopeType;
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
 * <p><b>没有 {@code builtin} 与 {@code status}</b>：接口不能创建内置角色（否则等于绕开权限体系
 * 造一个超级管理员），新建角色一律启用。前端传了这两个字段会被当成未知字段拒绝，而不是
 * 「收下但忽略」——忽略会让前端以为改生效了。
 *
 * <p><b>{@code dataScopeType} 与 {@code ownerIds} 是可选项</b>：省略时建出的角色范围是
 * {@code CUSTOM} 加空明细，即什么都看不见，管理员随后在数据范围端点里明确设置。
 * 保守默认的依据是不对称的代价：漏配一次只会让人发现「怎么看不到」，配错成 {@code ALL}
 * 可能几个月都没人注意到多看了别人的货主。
 *
 * <p>编码的正则直接引用 {@link NewRole#CODE_PATTERN}：规则只有一份定义，注解与用例层判定
 * 不会各自漂移。长度上限由正则覆盖（最长 32 位，列宽 64），因此不再重复写 {@code @Size}。
 *
 * @param code          角色编码，格式见 {@link NewRole#CODE_PATTERN}
 * @param name          角色名称
 * @param remark        备注，可为空
 * @param menuIds       勾选的菜单与权限点标识；为空或省略表示不授权任何权限
 * @param dataScopeType 数据范围类型；省略时为 {@code CUSTOM}
 * @param ownerIds      {@code CUSTOM} 时的指定货主标识；其他类型必须为空或省略
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

        @Schema(description = "勾选的菜单与权限点标识，以字符串传输：数组元素是字符串形式的十进制整数，"
                + "形如 [\"4\", \"5\"]；为空或省略表示不授权任何权限。元素必须存在，"
                + "元素不是十进制整数的字符串返回 40000")
        List<@NotNull(message = "菜单标识不能为空") String> menuIds,

        @Schema(description = "数据范围类型：ALL 全部货主，ASSIGNED 我负责的货主，CUSTOM 指定货主。"
                + "省略时为 CUSTOM（空明细，即什么都看不见）；取值非法返回 40000")
        DataScopeType dataScopeType,

        @Schema(description = "指定货主标识，以字符串传输：数组元素是字符串形式的十进制整数。"
                + "仅 dataScopeType 为 CUSTOM 时可传，其他类型传了返回 40000；"
                + "货主必须存在且启用，否则返回 40000")
        List<@NotNull(message = "货主标识不能为空") String> ownerIds) {
}

package com.yunwms.auth.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 登录成功的响应体。
 *
 * @param token              访问令牌，前端放入 {@code Authorization: Bearer <token>}
 * @param subjectType        主体类型编码：{@code STAFF} 内部员工，{@code OWNER} 货主账号
 * @param ownerId            所属货主标识；以字符串传输，内部员工为空
 * @param displayName        展示名
 * @param mustChangePassword 是否需要先改密；为 {@code true} 时前端直接进改密页，其余接口会返回 40104
 * @param roles              生效中的角色标识列表；没有角色时为空数组
 * @param accessiblePortals  可进入的前端应用：{@code console} 仓网端，{@code portal} 货主端
 */
public record LoginResponse(
        @Schema(description = "访问令牌，调用方放入请求头 Authorization: Bearer <token>。有效期由服务端配置"
                + "（默认 2 小时），过期或账号被停用后失效，需重新登录换取")
        String token,

        @Schema(description = "主体类型编码：STAFF 内部员工，OWNER 货主账号。前端据此决定界面形态")
        String subjectType,

        @Schema(description = "所属货主标识，字符串形式的十进制整数；内部员工为 null。"
                + "以字符串传输是因为超过 2^53 的整数在 JS 里会丢精度")
        String ownerId,

        @Schema(description = "展示名，登录后显示在页头")
        String displayName,

        @Schema(description = "是否需要先改密：true 表示本次登录只放行改密接口，其余业务接口返回 40104，"
                + "前端应直接跳改密页而不是首页")
        boolean mustChangePassword,

        @Schema(description = "生效中的角色编码列表（不是角色标识）；没有角色时为空数组。停用中的角色不会出现在这里")
        List<String> roles,

        @Schema(description = "可进入的前端应用编码：console 仓网端，portal 货主端；前端据此选择登录后跳转的端。"
                + "由主体类型决定，内部员工只能进仓网端，货主账号只能进货主端")
        List<String> accessiblePortals) {
}

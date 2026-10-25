package com.yunwms.auth.interfaces.controller.response;

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
        String token,
        String subjectType,
        String ownerId,
        String displayName,
        boolean mustChangePassword,
        List<String> roles,
        List<String> accessiblePortals) {
}

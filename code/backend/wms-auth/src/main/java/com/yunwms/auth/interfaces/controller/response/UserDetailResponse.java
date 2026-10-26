package com.yunwms.auth.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 用户详情：列表行的全部字段，加上只在详情里出现的联系与授权信息。
 *
 * <p>铺平成一层而不是嵌一层 {@code summary}：详情与列表行在 HTTP 上是两个并列对象，
 * 前端按同一套字段名渲染，嵌一层只会让取值路径变成两种（与 {@code RoleDetailResponse} 同一取舍）。
 *
 * <p>{@code roleIds} 与 {@code roles} 是同一批角色的两种表示：前者用于回填编辑表单的勾选状态，
 * 后者用于列表展示。两者都带出去，是为了让前端在「保存后刷新详情」时不必再发一次列表请求。
 *
 * @param id            用户标识，字符串形式
 * @param loginName     登录名
 * @param displayName   展示名
 * @param subjectType   主体类型编码
 * @param ownerId       所属货主标识，字符串形式；内部员工为 {@code null}
 * @param ownerName     所属货主名称；内部员工或货主已被逻辑删除时为 {@code null}
 * @param status        账号状态编码
 * @param roles         已分配的角色编码，含停用中的角色；没有角色时为空数组
 * @param lastLoginAt   最后登录时刻；从未登录过为 {@code null}
 * @param email         邮箱；货主账号可为 {@code null}
 * @param wecomUserId   企业微信 userid；可为 {@code null}
 * @param dataScopeType 用户级数据范围覆盖值；{@code null} 表示继承角色默认（本批只读，配置属于 2.4）
 * @param createdAt     创建时刻
 * @param roleIds       已分配的角色标识，字符串形式；没有角色时为空数组
 */
public record UserDetailResponse(
        @Schema(description = "用户标识，字符串形式的十进制整数；与列表行同名字段含义一致")
        String id,

        @Schema(description = "登录名，全局唯一且不区分大小写；不可编辑")
        String loginName,

        @Schema(description = "展示名，可编辑")
        String displayName,

        @Schema(description = "主体类型编码：STAFF 内部员工，OWNER 货主用户；决定 ownerId 与 email 的必填口径，不可编辑")
        String subjectType,

        @Schema(description = "所属货主标识，字符串形式的十进制整数；内部员工为 null，不可编辑")
        String ownerId,

        @Schema(description = "所属货主名称；内部员工，或货主已被逻辑删除时为 null")
        String ownerName,

        @Schema(description = "账号状态编码：ACTIVE 启用，DISABLED 停用")
        String status,

        @Schema(description = "已分配的角色编码列表（含停用中的角色），供只读展示；没有角色时为空数组")
        List<String> roles,

        @Schema(description = "最后登录时刻，ISO 8601 带时区（UTC）；从未登录过为 null")
        Instant lastLoginAt,

        @Schema(description = "邮箱；货主账号可为 null，内部员工必有值（用于自助找回密码）。编辑接口按全量替换处理")
        String email,

        @Schema(description = "企业微信 userid，用于应用消息推送；可为 null。编辑接口按全量替换处理")
        String wecomUserId,

        @Schema(description = "用户级数据范围覆盖值；null 表示继承角色默认。本批只读，配置入口在后续任务")
        String dataScopeType,

        @Schema(description = "账号创建时刻，ISO 8601 带时区（UTC）")
        Instant createdAt,

        @Schema(description = "已分配的角色标识列表（字符串形式的十进制整数），用于回填编辑表单的勾选状态；"
                + "没有角色时为空数组。与 roles 是同一批角色的两种表示")
        List<String> roleIds) {
}

package com.yunwms.auth.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * 用户列表的一行。
 *
 * <p>列与 {@code ui-structure.md} 第四节一致：登录名、姓名、主体类型、角色、货主范围摘要、状态、
 * 最后登录。「货主范围摘要」在本批只显示账号自身绑定的货主（{@code ownerId} / {@code ownerName}），
 * 角色的数据范围默认值与用户级覆盖值是任务 2.3/2.4 的内容；{@code ownerId} 为 {@code null}
 * 表示内部员工不绑定货主，前端据此显示「—」而不是空白。
 *
 * <p>{@code roles} 是角色<b>编码</b>而不是标识：列表上要显示人能读的标识（如 {@code CS_STAFF}），
 * 编辑表单要回填的标识在详情接口里给（见 {@code UserDetailResponse#roleIds}）。
 *
 * @param id          用户标识，字符串形式
 * @param loginName   登录名
 * @param displayName 展示名
 * @param subjectType 主体类型编码：{@code STAFF} 内部员工，{@code OWNER} 货主用户
 * @param ownerId     所属货主标识，字符串形式；内部员工为 {@code null}
 * @param ownerName   所属货主名称；内部员工或货主已被逻辑删除时为 {@code null}
 * @param status      账号状态编码：{@code ACTIVE} 启用，{@code DISABLED} 停用
 * @param roles       已分配的角色编码，含停用中的角色；没有角色时为空数组
 * @param lastLoginAt 最后登录时刻；从未登录过为 {@code null}
 */
public record UserListItemResponse(
        @Schema(description = "用户标识，字符串形式的十进制整数；详情、编辑、启停、分配角色都用它定位")
        String id,

        @Schema(description = "登录名，全局唯一且不区分大小写")
        String loginName,

        @Schema(description = "展示名，列表上显示给人看的名字")
        String displayName,

        @Schema(description = "主体类型编码：STAFF 内部员工，OWNER 货主用户")
        String subjectType,

        @Schema(description = "所属货主标识，字符串形式的十进制整数；内部员工为 null，前端据此显示「—」而不是空白")
        String ownerId,

        @Schema(description = "所属货主名称；内部员工，或货主已被逻辑删除时为 null")
        String ownerName,

        @Schema(description = "账号状态编码：ACTIVE 启用，DISABLED 停用")
        String status,

        @Schema(description = "已分配的角色编码列表（含停用中的角色）；没有角色时为空数组。"
                + "列表要显示人能读的编码，编辑表单回填用的角色标识在详情接口的 roleIds 里给")
        List<String> roles,

        @Schema(description = "最后登录时刻，ISO 8601 带时区（UTC），如 2026-10-24T08:30:00Z；从未登录过为 null")
        Instant lastLoginAt) {
}

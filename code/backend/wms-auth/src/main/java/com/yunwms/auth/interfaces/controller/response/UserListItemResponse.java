package com.yunwms.auth.interfaces.controller.response;

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
public record UserListItemResponse(String id,
                                   String loginName,
                                   String displayName,
                                   String subjectType,
                                   String ownerId,
                                   String ownerName,
                                   String status,
                                   List<String> roles,
                                   Instant lastLoginAt) {
}

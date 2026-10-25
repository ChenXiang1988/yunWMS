package com.yunwms.auth.interfaces.controller.response;

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
public record UserDetailResponse(String id,
                                 String loginName,
                                 String displayName,
                                 String subjectType,
                                 String ownerId,
                                 String ownerName,
                                 String status,
                                 List<String> roles,
                                 Instant lastLoginAt,
                                 String email,
                                 String wecomUserId,
                                 String dataScopeType,
                                 Instant createdAt,
                                 List<String> roleIds) {
}

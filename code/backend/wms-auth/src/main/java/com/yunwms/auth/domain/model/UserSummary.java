package com.yunwms.auth.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * 用户列表的一行：账号本身，加上它的角色与所属货主名称。
 *
 * <p>{@code roleCodes} 与 {@code ownerName} 必须与分页查询<b>一起</b>取回，不是逐行再查：
 * 在循环里补这两列会让列表页的 SQL 条数随页内行数线性增长（一页 20 行就是 41 次查询）。
 * 它们的来源不同——角色是本模块的表，货主名称来自 {@code wms-master} 的 {@code owner} 表
 * （见 {@link com.yunwms.auth.domain.port.OwnerNameQuery}）——所以由查询实现负责拼装，
 * 而不是在同一条 SQL 里联表。
 *
 * <p>{@code roleCodes} 含停用中的角色：管理员要看得出「这个账号挂着哪些角色」，而某个角色被停用
 * 正是他最需要发现的事。登录与鉴权走的是另一条口径（只看启用中的角色），两者不同不是笔误。
 *
 * @param id          用户标识
 * @param loginName   登录名
 * @param displayName 展示名
 * @param subjectType 主体类型：内部员工或货主用户
 * @param ownerId     所属货主标识；内部员工为 {@code null}
 * @param ownerName   所属货主名称；内部员工或货主已被逻辑删除时为 {@code null}
 * @param status      账号状态
 * @param roleCodes   已分配的角色编码，按角色排序号升序；没有角色时为空列表
 * @param lastLoginAt 最后登录时刻；从未登录过为 {@code null}
 */
public record UserSummary(long id,
                          String loginName,
                          String displayName,
                          SubjectType subjectType,
                          Long ownerId,
                          String ownerName,
                          UserStatus status,
                          List<String> roleCodes,
                          Instant lastLoginAt) {
}

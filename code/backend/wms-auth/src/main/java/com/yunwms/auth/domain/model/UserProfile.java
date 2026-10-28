package com.yunwms.auth.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * 用户详情：列表行的那份摘要，加上只在详情里出现的联系与来源信息。
 *
 * <p>用组合而不是把摘要的字段再铺一遍：详情与列表在「这个人是谁、有哪些角色」上必须是同一份口径，
 * 铺开成两个 record 之后，给摘要加一个字段就会漏掉其中一处，而漏掉的那处不会有编译错误。
 *
 * <p>{@code roleIds} 与摘要里的 {@code roleCodes} 是同一批角色的两种表示，都从这里出去：
 * 列表按编码展示，编辑表单要按标识回填勾选状态，两处都从同一次查询派生，不会出现
 * 「列表显示 A、表单勾选 B」这种只能靠比对两张表才能发现的不一致。
 *
 * @param summary       与列表行同源的摘要
 * @param email         邮箱；货主账号可为 {@code null}
 * @param wecomUserId   企业微信 userid，内部员工用于应用消息推送；可为 {@code null}
 * @param mustChangePassword 是否仍需先改密。不进列表摘要：编辑表单不展示也不提交它，列在列表里只是噪音。
 *                      放进详情是因为它是账号状态的一部分，而管理侧读 {@code sys_user.must_change_password}
 *                      只有这一处（登录链路读的是 {@link UserAccount}，那份只服务认证）
 * @param dataScopeType 用户级数据范围覆盖值；{@code null} 表示继承角色默认
 * @param ownerIds      {@code dataScopeType = CUSTOM} 时该覆盖配置里的指定货主标识，按标识升序；
 *                      其他情况为空列表。与 {@code dataScopeType} 一起构成完整的覆盖配置，
 *                      只回显类型会让编辑页无法回填已选的货主
 * @param createdAt     创建时刻
 * @param roleIds       已分配的角色标识，按角色排序号升序；没有角色时为空列表
 */
public record UserProfile(UserSummary summary,
                          String email,
                          String wecomUserId,
                          boolean mustChangePassword,
                          String dataScopeType,
                          List<Long> ownerIds,
                          Instant createdAt,
                          List<Long> roleIds) {
}

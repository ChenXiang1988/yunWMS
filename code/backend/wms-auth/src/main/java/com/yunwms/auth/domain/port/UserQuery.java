package com.yunwms.auth.domain.port;

import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.domain.model.UserSummary;

import java.util.List;
import java.util.Optional;

/**
 * 用户管理侧的读取入口：列表、详情，以及停用前的「最后一个可用超管」判定。
 *
 * <p>与 {@link UserAccountRepository} 的区别：那个端口服务于登录链路，只读「能登录的那个人」
 * （凭据、状态、锁定）；本端口服务管理界面，要带出货主名称与角色，两者的字段集与失败语义都不同。
 * 合成一个端口会让登录链路为了拿一个账号而多查两张表。
 *
 * <p>分页由调用方给出 {@code limit} / {@code offset}，而不是返回 MyBatis-Plus 的 {@code IPage}：
 * 领域端口不认识持久化框架的分页对象。
 *
 * <p>实现约定：本端口的每次调用都必须用<b>固定条数</b>的查询完成，不得在循环里逐行补数据
 * （见 {@link UserSummary} 的说明）。这条约定无法由编译器保证，因此由集成测试断言 SQL 条数。
 */
public interface UserQuery {

    /**
     * 统计满足筛选条件的用户总数。
     *
     * <p>与 {@link #findPage} 分开成两次查询而不是用窗口函数一次取回：结果为空页时窗口函数
     * 不会返回任何行，总数就没地方取，仍然要补一次计数。
     *
     * @param subjectType 主体类型；{@code null} 表示不筛选
     * @param status      账号状态；{@code null} 表示不筛选
     * @param keyword     按登录名或姓名模糊匹配的关键字；{@code null} 表示不筛选
     * @return 符合条件的用户总数
     */
    long count(SubjectType subjectType, UserStatus status, String keyword);

    /**
     * 按标识升序取一页用户。
     *
     * <p>每行的 {@code ownerName} 与 {@code roleCodes} 必须与本次查询一起取回：
     * 在循环里逐条查会让列表页的 SQL 条数随页内行数线性增长。
     *
     * @param subjectType 主体类型；{@code null} 表示不筛选
     * @param status      账号状态；{@code null} 表示不筛选
     * @param keyword     按登录名或姓名模糊匹配的关键字；{@code null} 表示不筛选
     * @param limit       本页最多返回多少行，调用方保证大于 0
     * @param offset      跳过多少行，调用方保证不小于 0
     * @return 本页用户；没有数据时为空列表
     */
    List<UserSummary> findPage(SubjectType subjectType, UserStatus status, String keyword, int limit, long offset);

    /**
     * 按标识取单个用户详情。
     *
     * <p>已逻辑删除的账号按不存在处理：它能登录的会话早已失效，
     * 而对管理界面来说「一条已删除的账号」比「不存在」更难解释。
     *
     * @param userId 用户标识
     * @return 用户详情；不存在或已逻辑删除时为空
     */
    Optional<UserProfile> findById(long userId);

    /**
     * 判断该账号是否为系统里<b>唯一</b>的可用超级管理员。
     *
     * <p>给「停用用户」的保护用：停用最后一个可用超管之后，界面上再也没有能配置权限的账号，
     * 恢复只能改库。规格没有写这一条，它防的是不可逆后果——误操作一次就没有入口了。
     *
     * <p>「可用超级管理员」= 账号 {@code ACTIVE}、未逻辑删除，且持有一个 {@code builtin = true}
     * 且启用中的角色。角色状态必须一起看：鉴权侧的内置角色动态展开只在角色启用时生效
     * （{@code UserPermissionQueryImpl} 先按 {@code status = 'ACTIVE'} 过滤角色），
     * 少判一条就会把「持有已停用内置角色的账号」算成可用超管，从而放过真正最后一次停用。
     *
     * <p>回答一次查询完成：本方法一次调用只发一条语句，不在应用层取回全部超管再逐个比对。
     *
     * @param userId 用户标识
     * @return 该账号当前是可用超管、且系统里没有另一个可用超管时返回 {@code true}；
     *         账号不存在、已停用、不持有内置角色，或另有可用超管时为 {@code false}
     */
    boolean isOnlyAvailableSuperAdmin(long userId);
}

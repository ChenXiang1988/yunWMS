package com.yunwms.auth.domain.port;

import com.yunwms.auth.domain.model.RoleIdentity;
import com.yunwms.auth.domain.model.RoleStatus;
import com.yunwms.auth.domain.model.RoleSummary;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 角色管理侧的读取入口：列表、详情，以及某个角色已勾选的菜单与权限点。
 *
 * <p>与 {@link UserRoleQuery} 的区别：那个端口回答「这个用户有哪些角色」，服务于登录与鉴权；
 * 本端口回答「系统里有哪些角色、各被多少人占用、勾了哪些权限点」，服务于角色管理界面。
 * 两者的过滤条件与口径都不同（前者只看启用中的角色，后者要能看到停用中的角色），
 * 合成一个端口会让两处口径互相牵制。
 *
 * <p>分页由调用方给出 {@code limit} / {@code offset}，而不是让本端口返回 MyBatis-Plus 的
 * {@code IPage}：领域端口不认识持久化框架的分页对象。
 *
 * @see RoleRegistrar 写入入口，以及「CASCADE 导致删除保护只能靠业务层」的说明
 */
public interface RoleQuery {

    /**
     * 统计满足筛选条件的角色总数。
     *
     * <p>与 {@link #findPage} 分开成两次查询而不是用窗口函数一次取回：结果为空页时窗口函数
     * 不会返回任何行，总数就没地方取，仍然要补一次计数。
     *
     * @param keyword 按编码或名称模糊匹配的关键字；{@code null} 表示不筛选
     * @param status  角色状态；{@code null} 表示不筛选
     * @return 符合条件的角色总数
     */
    long count(String keyword, RoleStatus status);

    /**
     * 按排序取一页角色。
     *
     * <p>每行的 {@code userCount} 与 {@code permissionCount} 必须与本次查询一起取回：
     * 在循环里逐条查会让列表页的 SQL 条数随角色数量线性增长。
     *
     * @param keyword 按编码或名称模糊匹配的关键字；{@code null} 表示不筛选
     * @param status  角色状态；{@code null} 表示不筛选
     * @param limit   本页最多返回多少行，调用方保证大于 0
     * @param offset  跳过多少行，调用方保证不小于 0
     * @return 本页角色；没有数据时为空列表
     */
    List<RoleSummary> findPage(String keyword, RoleStatus status, int limit, long offset);

    /**
     * 按标识取单个角色。
     *
     * <p>已逻辑删除的角色按不存在处理：它的编码仍被唯一索引占着，但对管理界面已经没有意义。
     *
     * @param roleId 角色标识
     * @return 角色；不存在或已逻辑删除时为空
     */
    Optional<RoleSummary> findById(long roleId);

    /**
     * 按标识批量取角色的身份信息（标识 + 是否内置）。
     *
     * <p>给「分配角色」用：那里要判定请求里的角色是否都存在，以及替换之后目标账号是否还持有
     * 内置角色。返回结果只含<b>存在且未逻辑删除</b>的角色，因此「返回条数少于去重后的请求条数」
     * 就是「有不存在的角色」——判重交给调用方，本端口只负责把库里的事实原样交出去。
     *
     * <p>为空或空集合时返回空列表，不查库：空的 {@code IN} 条件在拼装时会被省略，
     * 查询会退化成「查全部角色」，那会让一次错误的入参看起来像「所有角色都合法」。
     *
     * @param roleIds 角色标识集合
     * @return 角色身份信息，顺序不保证；没有匹配时为空列表
     */
    List<RoleIdentity> findIdentities(Collection<Long> roleIds);

    /**
     * 取某个角色已勾选的菜单与权限点标识。
     *
     * <p>返回菜单与操作两级（{@code sys_role_menu} 的全部行）：两者在规格里同属权限点，
     * 只返回操作级会让详情页的勾选树缺掉菜单节点，管理员看不出这个角色能进哪些页面。
     *
     * @param roleId 角色标识
     * @return 菜单与权限点标识，按标识升序；角色不存在或未勾选任何项时为空列表
     */
    List<Long> findMenuIds(long roleId);
}

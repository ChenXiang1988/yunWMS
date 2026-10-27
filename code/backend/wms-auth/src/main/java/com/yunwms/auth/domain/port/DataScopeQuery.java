package com.yunwms.auth.domain.port;

import com.yunwms.auth.domain.model.DataScopeSetting;
import com.yunwms.auth.domain.model.RoleDataScope;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 数据范围配置的只读入口：角色默认值、用户覆盖值，以及两者的 {@code CUSTOM} 明细。
 *
 * <p>与 {@link RoleQuery} / {@link UserQuery} 分开：那两个端口回答「角色/用户是什么」，
 * 服务于管理界面；本端口回答「谁看得见哪些货主」，服务于生效范围解析与配置回显。
 * 合成之后，登录链路上每次请求的解析都会被迫连角色摘要、占用人数一起读。
 *
 * <p><b>每次调用都必须用固定条数的查询完成</b>：生效范围是请求级计算的，一旦退化成逐角色查询，
 * 一个持有 10 个角色的人每次请求就会多 10 次往返，而这条退化不会有任何编译错误。
 */
public interface DataScopeQuery {

    /**
     * 取用户级覆盖配置。
     *
     * <p>{@code sys_user.data_scope_type} 为 {@code null} 表示「没有覆盖，继承角色默认」，
     * 此时返回空——不能在这里替它挑一个类型：那会把「没配置」说成「配置成了某一个值」，
     * 而生效范围的解析正是按「有没有覆盖」分支的。
     *
     * @param userId 用户标识
     * @return 覆盖配置；没有覆盖或账号不存在、已逻辑删除时为空
     */
    Optional<DataScopeSetting> findUserSetting(long userId);

    /**
     * 取角色级默认配置。
     *
     * @param roleId 角色标识
     * @return 角色配置；角色不存在或已逻辑删除时为空
     */
    Optional<DataScopeSetting> findRoleSetting(long roleId);

    /**
     * 取该用户<b>启用中</b>的角色及其数据范围类型。
     *
     * <p>停用与已逻辑删除的角色不出现在结果里：规格要求「角色处于停用状态时，其数据范围 MUST NOT
     * 计入并集」，而这条过滤只能落在查询上——在应用层再筛一次，等于让「停用角色也参与并集」的
     * 错误实现照样能通过单元测试。
     *
     * @param userId 用户标识
     * @return 启用中的角色配置，按角色排序号升序；没有角色时为空列表
     */
    List<RoleDataScope> findActiveRoleScopes(long userId);

    /**
     * 按角色批量取 {@code CUSTOM} 明细。
     *
     * <p>一次 {@code IN} 查询取回全部角色的明细，不在循环里逐角色查。
     *
     * @param roleIds 角色标识集合；为空时返回空映射且不发查询
     * @return 角色标识到货主标识列表的映射；没有任何明细的角色不出现在结果里
     */
    Map<Long, List<Long>> findCustomOwnerIds(Collection<Long> roleIds);

    /**
     * 取某个用户覆盖配置里的指定货主明细。
     *
     * <p>单独成一个方法而不是让调用方复用 {@link #findUserSetting}：用户详情已经从账号行读到了
     * 覆盖类型，只需要明细，再读一次账号行对同一份数据取两个来源。
     *
     * @param userId 用户标识
     * @return 货主标识列表，按标识升序；没有明细时为空列表
     */
    List<Long> findUserOwnerIds(long userId);
}

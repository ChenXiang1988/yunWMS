package com.yunwms.auth.domain.port;

import java.util.List;

/**
 * 用户功能权限点的只读查询。
 *
 * <p>只回答「这个人现在拥有哪些权限点」，不涉及数据范围：数据范围要改写查询条件（2.3），
 * 功能权限只在入口判定一次，两者的变化原因不同，合成一个端口会让任一侧的改动都碰到另一侧。
 *
 * <p>与 {@link UserRoleQuery} 分开：登录只需要角色标识来渲染菜单，不需要展开权限点；
 * 鉴权反过来要把权限点展开到底。放在一起会让登录链路顺带查出全部权限点。
 */
public interface UserPermissionQuery {

    /**
     * 查用户当前生效的权限点标识。
     *
     * <p>持有内置角色的用户返回全部权限点，且必须是动态的：该分支不读任何授权关联表，
     * 新增权限点后无需补数据即可生效。判定放在实现里而不是调用方，是为了让「谁是超管」
     * 只有一个判定点——规格禁止业务代码另行判断超管身份。
     *
     * <p>停用角色不计入：角色停用后若仍授权，管理员「停用角色」这个动作就只剩心理安慰。
     *
     * @param userId 用户标识
     * @return 权限点标识列表，按权限点定义顺序；不含 {@code null}。没有任何生效角色时返回空列表，不返回 {@code null}
     */
    List<String> findActivePermissionCodesByUserId(long userId);
}

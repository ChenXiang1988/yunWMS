package com.yunwms.auth.domain.port;

import java.util.List;

/**
 * 用户角色的只读查询。
 *
 * <p>登录只回答「这个人有哪些角色标识」，不展开权限点与数据范围（那是权限拦截的事）。
 * 单独成接口而不是塞进 {@link UserAccountRepository}，是为了让登录用例只依赖它用到的东西。
 */
public interface UserRoleQuery {

    /**
     * 查用户当前生效的角色标识。
     *
     * <p>停用的角色不返回：角色停用后若仍出现在登录结果里，前端会继续渲染它授权的菜单。
     *
     * @param userId 用户标识
     * @return 角色标识列表，按角色的排序号升序；没有角色时返回空列表，不返回 {@code null}
     */
    List<String> findActiveRoleCodesByUserId(long userId);
}

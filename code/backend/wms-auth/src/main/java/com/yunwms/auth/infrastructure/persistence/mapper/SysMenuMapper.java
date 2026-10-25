package com.yunwms.auth.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code sys_menu} 的只读投影。
 *
 * <p>只取 {@code perms} 一列，因此不声明表映射类、不继承 {@code BaseMapper}：
 * {@code sys_menu} 的增删改属于「权限点随版本发布维护」这一条线（没有界面入口），
 * 当前没有任何代码需要读写整行。等菜单 CRUD 落地时再补 {@code SysMenuEntity}，
 * 现在先建一个不用的实体只会多一份要跟着表结构同步的字段列表。
 *
 * <p>两条语句都在 {@code mapper/auth/SysMenuMapper.xml} 里：角色到权限点需要跨三张表，
 * 用 Wrapper 表达不出来，规则要求这种多表语句走 XML。
 */
public interface SysMenuMapper {

    /**
     * 取全部权限点，供内置角色动态展开使用。
     *
     * <p>不过滤 {@code status}：本查询回答的是「系统里定义了哪些权限点」，与某个权限点当前是否可见无关；
     * 过滤它会让超管在菜单被停用后跟着失去接口访问权，而停用菜单本来只是前端不渲染入口。
     *
     * @return 全部非空权限点标识，按定义顺序
     */
    List<String> selectAllPermissionCodes();

    /**
     * 取若干角色经授权关联到的权限点。
     *
     * <p>由调用方保证 {@code roleIds} 非空且都是启用中的角色：空集合在 SQL 里会退化成空结果，
     * 但调用方保证能让「为什么要短路由调用方决定」这件事只有一处。
     *
     * @param roleIds 角色标识集合，不得为空
     * @return 去重后的权限点标识
     */
    List<String> selectPermissionCodesByRoleIds(@Param("roleIds") List<Long> roleIds);
}

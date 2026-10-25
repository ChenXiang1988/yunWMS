package com.yunwms.auth.domain.port;

import com.yunwms.auth.domain.model.NewRole;

/**
 * 管理员建角色的写入入口。
 *
 * <p>不并进 {@link RoleQuery}：那个端口服务的是列表与详情读取，失败语义只有「没找到」；
 * 写入端口的失败语义是「违反库约束」，两者的错误处理路径完全不同，合成一个端口后
 * 任何能读角色的代码都能顺手写角色。
 *
 * <p>一次调用写两张表（{@code sys_role} 与 {@code sys_role_menu}），由用例包在同一个事务里。
 * 不拆成 {@code insertRole} 与 {@code insertMenus}：拆开之后「角色建了但一个权限点都没勾」
 * 会变成调用方可以制造的状态，而它与「管理员故意建一个无权限角色」在库里长得一模一样，
 * 事后无法区分。
 *
 * <p><b>关于删除（供 2.2b2 参考）：</b>{@code sys_user_role.role_id} 与
 * {@code sys_role_menu.role_id} 都是 {@code ON DELETE CASCADE}。这意味着一旦物理删除角色，
 * 用户的角色关联与角色的授权记录会被数据库静默删除、不会报任何错。
 * 因此规格里「角色仍被占用时不可删除」这条约束<b>在数据库层并不存在</b>，
 * 只能在业务层先取 {@link RoleQuery#findById} 返回的 {@code userCount} 判一次占用。
 * 判完仍要接受「判定之后、删除之前刚被分配」这一窗口：那是可接受的最后一致性，
 * 不是省略预先判定的理由——没有这一判，删除会连提示都省掉。
 *
 * <p>违反库约束（编码唯一索引 {@code ux_sys_role_code}、菜单外键
 * {@code sys_role_menu_menu_id_fkey}）时本端口不做翻译，直接向上抛数据访问异常，
 * 由用例翻成业务错误码：「哪条约束被违反」只有数据库知道，错误码属于协议层。
 */
public interface RoleRegistrar {

    /**
     * 写入新角色及其授权关联。
     *
     * <p>落库值固定为 {@code builtin = false}、{@code data_scope_type = 'CUSTOM'}、
     * {@code status = 'ACTIVE'}，见 {@link NewRole} 的说明：三者都不是创建入参。
     *
     * @param role       待落库的角色，编码与名称已通过创建期校验
     * @param operatorId 操作人标识，写入 {@code created_by} 与 {@code updated_by}
     * @return 数据库生成的角色标识
     * @throws IllegalStateException 插入未返回主键，说明写入与主键回填不一致
     */
    long register(NewRole role, long operatorId);
}

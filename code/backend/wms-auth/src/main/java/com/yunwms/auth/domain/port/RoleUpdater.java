package com.yunwms.auth.domain.port;

import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.model.RoleStatus;

import java.time.Instant;
import java.util.Collection;

/**
 * 管理员维护已有角色的写入入口：编辑基本信息与授权、启用停用、软删除。
 *
 * <p>为什么不并进 {@link RoleRegistrar}：那个端口只有「建」一个动作，且把 {@code builtin = false}、
 * {@code data_scope_type = 'CUSTOM'}、{@code status = 'ACTIVE'} 三个创建期固定值写死在实现里。
 * 扩成通用的「角色写入器」之后，编辑路径会顺手复用那三个固定值，于是「改一次名字把数据范围打回
 * CUSTOM」这类缺陷可以毫无阻碍地写出来。
 *
 * <p>为什么不并进 {@link RoleQuery}：那个端口的失败语义只有「没找到」，本端口的失败语义是
 * 「违反库约束」与「写入撞上并发删除」，两条错误处理路径完全不同。
 *
 * <p><b>删除是软删除</b>（写 {@code deleted_at}），与 {@code sys_user} 的删除模式一致：留下
 * 「这个角色曾经存在」的事实，将来审计日志（2.8）里的角色编码才对得上真实对象。副作用要注意：
 * {@code ux_sys_role_code} 是不带 {@code deleted_at} 条件的全表唯一索引，软删除之后该编码仍被占用，
 * 重建同名角色会撞唯一索引，而管理界面看不到已删除的角色——建角色侧的错误提示已把这一点写明。
 *
 * <p><b>占用校验不在本端口</b>：规格要求「角色仍被占用时不可删除」，而这条约束在数据库层并不存在
 * （{@code sys_user_role.role_id} 是 {@code ON DELETE CASCADE}，物理删除会静默带走关联记录）。
 * 判定只能由用例层先取 {@link RoleQuery#findById} 的 {@code userCount} 做一次，见
 * {@link RoleRegistrar} 关于级联的说明。
 *
 * <p>违反库约束（菜单外键 {@code sys_role_menu_menu_id_fkey}）时本端口不做翻译，直接向上抛数据
 * 访问异常，由用例翻成业务错误码：「哪条约束被违反」只有数据库知道，错误码属于协议层。
 */
public interface RoleUpdater {

    /**
     * 覆盖角色名称、备注与授权，并更新审计列。
     *
     * <p>授权是<b>替换语义</b>：先删掉该角色现有的全部关联，再插入本次提交的集合，两步在同一次调用里
     * 完成。不按差集更新——求差集要先读出现状、在应用层算增删，两个并发编辑会各自基于同一份旧快照
     * 算出「该删哪些」，后提交的那次把前一次的增量覆盖掉；「替换成这一批」是不带前置状态的意图。
     *
     * <p>不存在的菜单或权限点标识由外键拦下并整单回滚：预查只是把同一条判定做两遍，还留下
     * 「查完到插入之间该 id 被删掉」的窗口。
     *
     * @param roleId     角色标识
     * @param name       角色名称，调用方保证非空
     * @param remark     备注，{@code null} 表示清空
     * @param menuIds    替换后的菜单与权限点标识；空集合表示清空授权
     * @param operatorId 操作人标识，写入 {@code updated_by}
     * @param updatedAt  变更时刻，写入 {@code updated_at}
     * @throws IllegalStateException 角色在本次操作过程中被删除（含被并发软删除），库内没有任何一行被改动
     */
    void updateWithMenus(long roleId, String name, String remark, Collection<Long> menuIds,
                         long operatorId, Instant updatedAt);

    /**
     * 写入角色的数据范围类型，并更新审计列。
     *
     * <p>只写 {@code data_scope_type} 与审计列：{@code CUSTOM} 的明细在另一张表，由
     * {@link DataScopeOwnerRegistrar} 负责替换。两个动作由用例包在同一个事务里——只改了类型没换明细时，
     * 「类型是 {@code CUSTOM}、明细还是上个类型留下的」这种状态会真实存在一小段时间，
     * 而在那一段里范围解析读到的是谁的明细，取决于提交顺序。
     *
     * <p>与 {@link #updateWithMenus} 分开成两个方法而不是加一个可空参数：编辑资料永远不该顺带改范围
     * （那正是「资料更新漏传字段就把范围清空」的来源），配置范围也不该顺带改名称。
     *
     * @param roleId     角色标识
     * @param type       目标数据范围类型
     * @param operatorId 操作人标识，写入 {@code updated_by}
     * @param updatedAt  变更时刻，写入 {@code updated_at}
     * @throws IllegalStateException 角色在本次操作过程中被删除，库内没有任何一行被改动
     */
    void updateDataScopeType(long roleId, DataScopeType type, long operatorId, Instant updatedAt);

    /**
     * 改变角色状态。
     *
     * <p>只写 {@code status} 与审计列：停用不解除用户与角色的关联，只是让它在鉴权时不再计入。
     * 启用时被停用期间新增的权限点仍然按原有授权生效，不需要补写关联。
     *
     * @param roleId     角色标识
     * @param status     目标状态
     * @param operatorId 操作人标识，写入 {@code updated_by}
     * @param updatedAt  变更时刻，写入 {@code updated_at}
     * @throws IllegalStateException 角色在本次操作过程中被删除，库内没有任何一行被改动
     */
    void updateStatus(long roleId, RoleStatus status, long operatorId, Instant updatedAt);

    /**
     * 软删除角色：写 {@code deleted_at}，不做物理删除。
     *
     * <p>授权关联（{@code sys_role_menu}）保留不动：角色行还在，关联就仍然指向一个真实对象；
     * 鉴权侧按角色的 {@code deleted_at IS NULL} 过滤，已删除角色的权限不会再生效。
     *
     * @param roleId     角色标识
     * @param operatorId 操作人标识，写入 {@code updated_by}
     * @param deletedAt  删除时刻，同时写入 {@code deleted_at} 与 {@code updated_at}
     * @throws IllegalStateException 角色在本次操作过程中被删除，库内没有任何一行被改动
     */
    void softDelete(long roleId, long operatorId, Instant deletedAt);
}

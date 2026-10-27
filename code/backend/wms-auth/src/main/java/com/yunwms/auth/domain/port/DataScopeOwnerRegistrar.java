package com.yunwms.auth.domain.port;

import java.util.Collection;

/**
 * {@code sys_data_scope_owner} 的写入入口：按角色或用户整批替换指定货主明细。
 *
 * <p><b>替换而非差集：</b>先按主体删掉现有明细，再插入本次提交的集合，两步在同一次调用里完成。
 * 求差集要先读出现状、在应用层算增删，两个并发编辑会各自基于同一份旧快照算出「该删哪些」，
 * 后提交的那次把前一次的增量覆盖掉；「替换成这一批」是不带前置状态的意图。
 *
 * <p><b>物理删除：</b>该表没有 {@code deleted_at} 列（明细是纯关联，留着历史行会让范围解析读到
 * 已被替换掉的货主）。因此删除用 {@code DELETE}，且删除命中零行是正常结果（主体原本就没有明细），
 * 不作为失败判据。
 *
 * <p>不校验类型与明细的搭配：那个判定属于用例层（见 {@code DataScopeOwnerValidator}），
 * 因为它要查「货主是否存在且启用」，而仓储只负责把给定集合写进去。
 */
public interface DataScopeOwnerRegistrar {

    /**
     * 用给定集合替换某个角色的指定货主明细。
     *
     * <p>调用方在类型不是 {@code CUSTOM} 时传空集合：{@code ALL} 与 {@code ASSIGNED} 是规则，
     * 不落明细。传入空集合等价于清空。
     *
     * @param roleId   角色标识，调用方保证该角色存在
     * @param ownerIds 替换后的货主标识集合，可为空集合（表示清空）
     */
    void replaceRoleOwners(long roleId, Collection<Long> ownerIds);

    /**
     * 用给定集合替换某个用户的指定货主明细。
     *
     * <p>清除用户覆盖时同样调用本方法并传空集合：类型为 {@code null}（继承角色默认）与
     * 「类型是 CUSTOM 但没有明细」在明细表里都是「没有行」，两者的区别只存在于
     * {@code sys_user.data_scope_type}。
     *
     * @param userId   用户标识，调用方保证该账号存在
     * @param ownerIds 替换后的货主标识集合，可为空集合（表示清空）
     */
    void replaceUserOwners(long userId, Collection<Long> ownerIds);
}

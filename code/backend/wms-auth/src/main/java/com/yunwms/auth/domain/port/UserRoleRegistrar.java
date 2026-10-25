package com.yunwms.auth.domain.port;

import java.util.Collection;

/**
 * 用户角色关联的写入入口：整批替换某个账号的角色。
 *
 * <p>只有「替换」没有「追加 / 移除单条」：界面上是一次多选框的整体保存，追加与移除会各写一个接口，
 * 而三个入口最终都要维护同一份不变量（这个人恰好拥有勾选的那几个角色）。只留一个替换入口意味着
 * 调用方不必知道「现在是哪几个」，也就不存在「先读后写」在并发下丢掉别人刚加的角色。
 *
 * <p>一次调用写两张表（{@code sys_user_role} 与 {@code sys_user} 的审计列），由用例包在同一个事务里：
 * 关联写了而审计列没写时，事后无法回答「谁改了这个账号的权限」。
 *
 * <p>违反库约束（角色外键 {@code sys_user_role_role_id_fkey}）时本端口不做翻译，
 * 直接向上抛数据访问异常，由用例翻成业务错误码：「哪条约束被违反」只有数据库知道，
 * 错误码属于协议层。
 */
public interface UserRoleRegistrar {

    /**
     * 用给定的角色集合替换某个账号的全部角色。
     *
     * <p>空集合是合法入参，表示移除该账号的所有角色。
     *
     * @param userId     用户标识，调用方保证账号存在
     * @param roleIds    角色标识集合，调用方保证已去重且都是存在的角色
     * @param operatorId 操作人标识，写入 {@code sys_user.updated_by}
     * @throws IllegalStateException 账号在写入过程中被删除，此时不能告诉调用方「改成功了」
     */
    void replaceRoles(long userId, Collection<Long> roleIds, long operatorId);
}

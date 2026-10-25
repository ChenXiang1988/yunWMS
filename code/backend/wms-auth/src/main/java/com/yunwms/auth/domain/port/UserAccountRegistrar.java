package com.yunwms.auth.domain.port;

import com.yunwms.auth.domain.model.NewUserAccount;

import java.util.List;

/**
 * 管理员建号的写入入口。
 *
 * <p>为什么不并进 {@link UserAccountRepository}：那个端口服务的是认证链路（登录、强制改密、本人改密），
 * 它的 Javadoc 明确把「管理员建号」挡在外面。两条链路的读写对象、失败语义与权限要求都不同，
 * 合成一个端口后，任何能拿到仓储的代码都能绕过用例直接插账号。
 *
 * <p>建号一次写两张表（{@code sys_user} 与 {@code sys_user_role}），由用例包在同一个事务里；
 * 本端口因此只提供「一次调用写完整条建号」的方法，不拆成 {@code insertUser} 与 {@code insertRoles}：
 * 拆开之后「只写了账号没写角色」会变成调用方可以制造的状态。
 *
 * <p>违反库约束（登录名唯一索引、邮箱唯一索引、货主外键、角色外键、{@code ck_user_subject}）时，
 * 本端口不做翻译，直接向上抛出数据访问异常，由调用方翻译成业务错误码：
 * 「哪条约束被违反」只有数据库知道，而错误码属于协议层，不该出现在这里。
 */
public interface UserAccountRegistrar {

    /**
     * 写入新账号及其角色分配。
     *
     * @param account    待落库的账号，密码字段必须是哈希
     * @param roleIds    要分配的角色标识；为空表示不分配角色
     * @param operatorId 操作人标识，写入 {@code created_by} 与 {@code updated_by}
     * @return 数据库生成的用户标识
     * @throws IllegalStateException 插入未返回主键，说明写入与主键回填不一致
     */
    long register(NewUserAccount account, List<Long> roleIds, long operatorId);
}

package com.yunwms.auth.domain.port;

import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.model.UserStatus;

import java.time.Instant;

/**
 * 管理员维护账号的写入入口：编辑基本信息、启用停用、重置密码。
 *
 * <p>为什么不并进 {@link UserAccountRepository}：那个端口服务的是认证链路（登录、强制改密、本人改密），
 * 它的 Javadoc 已写明「管理员建号、启停用、重置密码属于用户管理，不在本接口里扩张」。
 * 两条链路的失败语义不同——认证链路按「哪一步不通过」给 401 段码，管理链路按「哪条约束被违反」给
 * 400 / 409 段码——合成一个端口后，任何能拿到它的代码都能绕过用例把账号改成停用。
 *
 * <p>为什么不并进 {@link UserAccountRegistrar}：建号与改号是两个用例，前者只有插入、后者只有更新；
 * 合成一个「账号写入器」会让「建号顺手改一次状态」变成可以写出来的代码。
 *
 * <p>本端口不做业务判定，也不翻译约束冲突：主体类型与邮箱必填、最后一个超管不能停、不能重置
 * 自己密码，这些规则在用例层；「哪条约束被违反」只有数据库知道，而错误码属于协议层。
 * 违反库约束时直接向上抛出数据访问异常，由调用方翻译。
 */
public interface UserAccountUpdater {

    /**
     * 覆盖账号的展示名、邮箱与企业微信 userid。
     *
     * <p>只碰这三列：登录名是标识（改了等于换账号），主体类型与所属货主决定数据归属
     * （改了等于把这个账号迁移到另一个货主名下）。两件事都不是「编辑基本信息」，
     * 要做也该是各自的用例，因此这里连参数都不给。
     *
     * <p>{@code null} 表示清空——货主账号可以不填邮箱与企业微信 userid。
     * 内部员工清空邮箱会被 {@code ck_user_email_required} 拦下，由调用方翻译成中文提示。
     *
     * @param userId      用户标识
     * @param displayName 展示名，调用方保证非空
     * @param email       邮箱，可为 {@code null}
     * @param wecomUserId 企业微信 userid，可为 {@code null}
     * @param operatorId  操作人标识，写入 {@code updated_by}
     * @param updatedAt   变更时刻，写入 {@code updated_at}
     * @throws IllegalStateException 账号在本次操作过程中被删除，库内没有任何一行被改动
     */
    void updateProfile(long userId, String displayName, String email, String wecomUserId,
                       long operatorId, Instant updatedAt);

    /**
     * 写入或清除账号的用户级数据范围覆盖类型。
     *
     * <p>{@code type} 为 {@code null} 表示<b>清除覆盖</b>（{@code data_scope_type = NULL}），
     * 生效范围回落到角色并集。这是列上唯一能表达「没有覆盖」的写法——写一个具体类型都是
     * 「有覆盖」，两者在解析时的分支不同。
     *
     * <p>只碰 {@code data_scope_type} 与审计列：{@code CUSTOM} 的明细在 {@code sys_data_scope_owner}，
     * 由 {@code DataScopeOwnerRegistrar} 替换；两处由用例包在同一个事务里。
     *
     * <p>不在这里判「货主账号不能配置覆盖」：那要用主体类型，而主体类型是本方法调用方刚读过的
     * 账号行里的字段，再读一次只会多一个可能不一致的来源。
     *
     * @param userId     用户标识
     * @param type       目标类型；{@code null} 表示清除覆盖
     * @param operatorId 操作人标识，写入 {@code updated_by}
     * @param updatedAt  变更时刻，写入 {@code updated_at}
     * @throws IllegalStateException 账号在本次操作过程中被删除，库内没有任何一行被改动
     */
    void updateDataScopeType(long userId, DataScopeType type, long operatorId, Instant updatedAt);

    /**
     * 改变账号状态。
     *
     * <p>只写 {@code status} 与审计列：启用不解除锁定（锁定是到期自动失效的派生状态，
     * 顺手清掉等于给了一条「不用等就能解锁」的旁路），停用不清凭据（再启用时密码仍是原样）。
     *
     * @param userId     用户标识
     * @param status     目标状态
     * @param operatorId 操作人标识，写入 {@code updated_by}
     * @param updatedAt  变更时刻，写入 {@code updated_at}
     * @throws IllegalStateException 账号在本次操作过程中被删除，库内没有任何一行被改动
     */
    void updateStatus(long userId, UserStatus status, long operatorId, Instant updatedAt);

    /**
     * 写入管理员指定的新密码，并把「需先改密」标记置为 {@code true}。
     *
     * <p>与 {@link UserAccountRepository#updatePassword} 的区别只有一处，但足以分成两个方法：
     * 本人改密是自己选的密码，改完即用，标记置 {@code false}；管理员重置出来的是一个临时口令，
     * 必须强制再改一次，标记置 {@code true}。合成一个方法靠参数区分，迟早会出现「重置后不再强制改密」。
     *
     * <p>标记与哈希在同一条 UPDATE 里落库：分两步写时，中间失败会留下「临时口令已生效、
     * 却不再强制改密」的账号，那个口令会一直有效。
     *
     * <p>不动 {@code failed_attempts} 与 {@code locked_until}：重置密码不解除锁定，
     * 锁定到期会自行失效；在这里清空等于给「连续失败 5 次锁定」开了一条未经验证的旁路。
     *
     * @param userId          用户标识
     * @param newPasswordHash 新密码哈希，由 {@code PasswordHasher#hash} 生成，不接受明文
     * @param changedAt       重置时刻，同时写入 {@code password_updated_at} 与 {@code updated_at}
     * @param operatorId      操作人标识，写入 {@code updated_by}
     * @throws IllegalStateException 账号在本次操作过程中被删除，库内没有任何一行被改动
     */
    void resetPassword(long userId, String newPasswordHash, Instant changedAt, long operatorId);
}

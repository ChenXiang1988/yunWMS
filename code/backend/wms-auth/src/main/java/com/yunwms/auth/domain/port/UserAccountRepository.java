package com.yunwms.auth.domain.port;

import com.yunwms.auth.domain.model.UserAccount;

import java.time.Instant;
import java.util.Optional;

/**
 * 登录主体的读写入口。
 *
 * <p>只暴露登录链路需要的两个动作。密码变更、启停用属于用户管理，不在本接口里扩张，
 * 否则这里会变成整张表的 CRUD 入口，谁都能绕过用例直接改库。
 */
public interface UserAccountRepository {

    /**
     * 按登录名查账号，忽略大小写（登录名唯一索引建在 {@code lower(login_name)} 上）。
     *
     * <p>软删除的账号视为不存在：删除后的账号不允许再登录。
     *
     * @param loginName 登录名，调用方需保证非空
     * @return 账号；不存在时为空
     */
    Optional<UserAccount> findByLoginName(String loginName);

    /**
     * 记录本次登录时间。
     *
     * @param userId  用户标识
     * @param loginAt 登录时刻
     * @throws IllegalStateException 账号在登录过程中已不存在，此时不允许继续签发令牌
     */
    void recordLoginTime(long userId, Instant loginAt);
}

package com.yunwms.auth.domain.port;

import com.yunwms.auth.domain.model.SessionOrigin;
import com.yunwms.auth.domain.model.UserAccount;

/**
 * 会话令牌签发。
 *
 * <p>把「签发令牌」抽象成端口，登录用例就不必认识 Sa-Token：换实现不影响登录判定，
 * 单测也不必起容器。
 */
public interface SessionTokenIssuer {

    /**
     * 为账号建立会话并签发令牌。
     *
     * <p>实现方必须把主体类型与所属货主固定写入会话：货主账号的货主归属一旦只存在于登录响应里，
     * 后续按货主过滤就变成了「信前端」，而过滤的前提恰恰是不信前端。
     *
     * @param account 已通过密码校验的账号
     * @param origin  登录时间、来源地址与客户端标识
     * @return 访问令牌，调用方负责放进响应体
     * @throws IllegalStateException 令牌签发后取不到令牌值，属于实现错误，不返回空令牌
     */
    String issue(UserAccount account, SessionOrigin origin);
}

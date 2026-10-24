package com.yunwms.auth.domain.port;

import java.util.OptionalLong;

/**
 * 当前请求所属会话的读写入口。
 *
 * <p>登录、强制改密拦截、改密三处都要读或写同一个「需先改密」标记：登录时写入、拦截时读取、
 * 改密成功后清除。三者若各自去 {@code StpUtil} 取会话，键名与语义会各写一遍，
 * 迟早出现「登录写了、拦截读的是另一个键」这种查不出原因的错。这里把它收成一个端口，
 * 顺便让上述三处都能被 Mockito 驱动，不必起容器。
 *
 * <p>用户标识只暴露 {@code OptionalLong}，不抛会话实现自己的异常：端口不认识 Sa-Token，
 * 「当前没有登录」由调用方翻译成业务错误码。
 */
public interface CurrentSession {

    /**
     * 取当前会话的登录用户标识。
     *
     * @return 用户标识；当前请求没有有效会话时为空
     */
    OptionalLong currentUserId();

    /**
     * 当前会话是否处于「需先改密」状态。
     *
     * <p>拦截链路在每个请求上都会问一次，因此这里只读会话，不回查数据库：
     * 改密成功后标记会被清掉，数据库与标记在同一次请求里对齐。
     *
     * @return 需先改密返回 {@code true}；已登录但无此标记、或根本没有会话时返回 {@code false}
     */
    boolean mustChangePassword();

    /**
     * 把当前会话标记为「需先改密」。
     *
     * <p>由登录用例在令牌签发之后调用：会话在签发时才存在，先标记会拿不到会话。
     */
    void markMustChangePassword();

    /**
     * 清除当前会话的「需先改密」标记。
     *
     * <p>由改密用例在密码落库之后调用。标记落在账号会话上，因此同一账号的其他令牌也会一起解除拦截，
     * 这正是期望行为：改的是账号的密码，不是某一个令牌的密码。
     */
    void clearMustChangePassword();
}

package com.yunwms.auth.infrastructure.security;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.yunwms.auth.domain.port.CurrentSession;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;

/**
 * 用 Sa-Token 承载会话读写。
 *
 * <p>「需先改密」标记落在 Account-Session 而不是 Token-Session：这是账号级状态，
 * 同一账号在多端登录时都该被拦；改密成功后清一次即对所有令牌生效，
 * 否则用户在一个端改完密，另一个端的会话还在被拦，只能靠重新登录解开。
 */
@Component
public class SaTokenCurrentSession implements CurrentSession {

    @Override
    public OptionalLong currentUserId() {
        try {
            return OptionalLong.of(StpUtil.getLoginIdAsLong());
        } catch (NotLoginException e) {
            // 未登录是调用方要判的普通情况（例如令牌已失效），不当作异常向上抛：
            // 由用例翻译成 40100，而不是让 Sa-Token 的异常类型漏出端口
            return OptionalLong.empty();
        }
    }

    @Override
    public boolean mustChangePassword() {
        // 先判登录：未登录时取会话本身就抛异常，而拦截链路对匿名请求只关心「是否放行」
        if (!StpUtil.isLogin()) {
            return false;
        }
        SaSession session = StpUtil.getSession();
        return Boolean.TRUE.equals(session.get(SessionAttributes.MUST_CHANGE_PASSWORD));
    }

    @Override
    public void markMustChangePassword() {
        StpUtil.getSession().set(SessionAttributes.MUST_CHANGE_PASSWORD, Boolean.TRUE);
    }

    @Override
    public void clearMustChangePassword() {
        // 删除而不是写 false：缺省即「不需要改密」，两种编码同一件事迟早有一处读错
        StpUtil.getSession().delete(SessionAttributes.MUST_CHANGE_PASSWORD);
    }
}

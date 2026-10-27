package com.yunwms.auth.infrastructure.security;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.port.CurrentSession;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * 用 Sa-Token 承载会话读写。
 *
 * <p>「需先改密」标记落在 Account-Session 而不是 Token-Session：这是账号级状态，
 * 同一账号在多端登录时都该被拦；改密成功后清一次即对所有令牌生效，
 * 否则用户在一个端改完密，另一个端的会话还在被拦，只能靠重新登录解开。
 *
 * <p>主体类型与所属货主也读自同一份 Account-Session：它们由登录用例写入
 * （见 {@code SaTokenSessionTokenIssuer}），数据范围解析每次请求都要问一次，
 * 因此这里只读会话、不回查数据库。
 */
@Component
public class SaTokenCurrentSession implements CurrentSession {

    /**
     * 取当前账号会话。
     *
     * <p>未登录时返回 {@code null} 而不是抛异常：本类的全部读取方法对匿名请求都只有
     * 「没有值」这一种回答，让每个调用方各自 try-catch 会把同一条判定写四遍。
     *
     * @return 账号会话；当前请求没有登录时为空
     */
    private static SaSession currentSessionOrNull() {
        try {
            return StpUtil.getSession();
        } catch (NotLoginException e) {
            // 未登录是调用方要判的普通情况（例如令牌已失效），不当作异常向上抛：
            // 由用例翻译成 40100，而不是让 Sa-Token 的异常类型漏出端口
            return null;
        }
    }

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
    public Optional<SubjectType> currentSubjectType() {
        SaSession session = currentSessionOrNull();
        if (session == null) {
            return Optional.empty();
        }
        Object value = session.get(SessionAttributes.SUBJECT_TYPE);
        if (!(value instanceof String code)) {
            // 缺属性按「没有」处理：历史会话由旧版本签发时不带该属性，判空比猜一个默认值安全
            return Optional.empty();
        }
        // 未知编码直接抛：会话里的主体类型决定数据范围是否强制隔离，静默回落会让货主账号按员工口径解析
        return Optional.of(SubjectType.fromCode(code));
    }

    @Override
    public OptionalLong currentOwnerId() {
        SaSession session = currentSessionOrNull();
        if (session == null) {
            return OptionalLong.empty();
        }
        Object value = session.get(SessionAttributes.OWNER_ID);
        if (!(value instanceof Number number)) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(number.longValue());
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

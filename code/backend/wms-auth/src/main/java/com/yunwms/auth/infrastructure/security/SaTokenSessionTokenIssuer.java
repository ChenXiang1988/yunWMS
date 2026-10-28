package com.yunwms.auth.infrastructure.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.yunwms.auth.domain.model.SessionOrigin;
import com.yunwms.auth.domain.model.UserAccount;
import com.yunwms.auth.domain.port.SessionTokenIssuer;
import org.springframework.stereotype.Component;

/**
 * 用 Sa-Token 建立会话并签发令牌。
 *
 * <p>两类主体共用一个 {@code StpLogic}：登录名全库唯一，同一个人不可能既是员工又是货主，
 * 因此不需要两套账号体系来隔离令牌。区分主体的是会话里的 {@code subjectType} 与 {@code ownerId}，
 * 不是两套登录逻辑——两套逻辑会带来「同一个用户两组会话」这种没人能解释的状态。
 */
@Component
public class SaTokenSessionTokenIssuer implements SessionTokenIssuer {

    @Override
    public String issue(UserAccount account, SessionOrigin origin) {
        StpUtil.login(account.id());
        SaSession session = StpUtil.getSession();
        session.set(SessionAttributes.LOGIN_AT, origin.loginAt());
        putIfPresent(session, SessionAttributes.SOURCE_ADDRESS, origin.sourceAddress());
        putIfPresent(session, SessionAttributes.CLIENT_IDENTIFIER, origin.clientIdentifier());
        // 姓名随会话一起固定：审计写入要的是「操作当时的姓名」，改名或删号都不该改写历史记录；
        // 写入点只有登录一处，漏了它审计就只能记下一个不知道是谁的操作人
        putIfPresent(session, SessionAttributes.DISPLAY_NAME, account.displayName());
        session.set(SessionAttributes.SUBJECT_TYPE, account.subjectType().name());
        if (account.ownerId() != null) {
            // 货主归属在这里固定进会话：后续按货主过滤读会话，而不是信前端传来的货主标识
            session.set(SessionAttributes.OWNER_ID, account.ownerId());
        }

        String token = StpUtil.getTokenValue();
        if (token == null) {
            // 登录刚成功却取不到令牌值，说明 Sa-Token 的配置或版本与本实现不符，不能返回空令牌
            throw new IllegalStateException("登录成功但取不到令牌值，请检查 sa-token 配置");
        }
        return token;
    }

    /** 缺失的审计项不写空串：读出来是 {@code null} 才能区分「客户端没带」与「带了个空值」。 */
    private static void putIfPresent(SaSession session, String key, String value) {
        if (value != null && !value.isBlank()) {
            session.set(key, value);
        }
    }
}

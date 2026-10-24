package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.PasswordLoginCommand;
import com.yunwms.auth.application.dto.AuthenticatedSubject;
import com.yunwms.auth.domain.model.SessionOrigin;
import com.yunwms.auth.domain.model.UserAccount;
import com.yunwms.auth.domain.port.PasswordHasher;
import com.yunwms.auth.domain.port.SessionTokenIssuer;
import com.yunwms.auth.domain.port.UserAccountRepository;
import com.yunwms.auth.domain.port.UserRoleQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 登录名加密码的登录用例。
 *
 * <p>两类主体走同一条链路，主体类型在查到账号后由 {@code sys_user.subject_type} 决定，
 * 因此不存在「走错入口就能绕开规则」的可能。
 *
 * <p>不开事务：本用例只写 {@code sys_user.last_login_at} 一处，没有跨表原子性要求；
 * 加一个空事务只会让人误以为登录做了更多持久化。
 */
@Service
public class PasswordLoginService {

    private final UserAccountRepository userAccountRepository;
    private final UserRoleQuery userRoleQuery;
    private final PasswordHasher passwordHasher;
    private final SessionTokenIssuer sessionTokenIssuer;

    public PasswordLoginService(UserAccountRepository userAccountRepository,
                                UserRoleQuery userRoleQuery,
                                PasswordHasher passwordHasher,
                                SessionTokenIssuer sessionTokenIssuer) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleQuery = userRoleQuery;
        this.passwordHasher = passwordHasher;
        this.sessionTokenIssuer = sessionTokenIssuer;
    }

    /**
     * 校验凭据并签发令牌。
     *
     * <p>判定顺序固定为「查账号 → 验密码 → 查状态 → 查锁定」，不能调换：
     * 一旦先报状态，「账号已停用」就成了账号是否存在的探针，登录框会被用来枚举账号。
     * 先验密码，状态类提示就只对拿到正确密码的人可见。
     *
     * @param command 登录入参
     * @return 令牌与主体信息
     * @throws BizException 凭据错误、账号停用或账号处于锁定期；凭据错误与账号不存在共用同一错误码与文案
     */
    public AuthenticatedSubject login(PasswordLoginCommand command) {
        Instant now = Instant.now();
        // 登录名两侧空格一律忽略；密码不处理，空格在密码里是合法字符
        UserAccount account = userAccountRepository.findByLoginName(command.loginName().strip())
                .orElseThrow(PasswordLoginService::credentialsRejected);

        if (!passwordHasher.matches(command.password(), account.passwordHash())) {
            throw credentialsRejected();
        }
        if (account.isDisabled()) {
            throw new BizException(ErrorCode.LOGIN_ACCOUNT_DISABLED);
        }
        if (account.isLockedAt(now)) {
            throw new BizException(ErrorCode.LOGIN_ACCOUNT_LOCKED);
        }

        List<String> roles = userRoleQuery.findActiveRoleCodesByUserId(account.id());
        // 先落库再签发：写失败时客户端拿不到令牌，避免出现「登录时间没记上但会话已建立」
        userAccountRepository.recordLoginTime(account.id(), now);
        String token = sessionTokenIssuer.issue(account,
                new SessionOrigin(now, command.sourceAddress(), command.clientIdentifier()));
        return new AuthenticatedSubject(token, account.subjectType(), account.ownerId(),
                account.displayName(), roles);
    }

    private static BizException credentialsRejected() {
        return new BizException(ErrorCode.LOGIN_FAILED);
    }
}

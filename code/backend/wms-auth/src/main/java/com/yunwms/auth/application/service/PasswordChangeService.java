package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.ChangePasswordCommand;
import com.yunwms.auth.domain.model.PasswordPolicy;
import com.yunwms.auth.domain.model.PasswordRuleViolation;
import com.yunwms.auth.domain.model.UserAccount;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.PasswordHasher;
import com.yunwms.auth.domain.port.UserAccountRepository;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 本人改密的用例，也是待改密状态下唯一被放行的写操作。
 *
 * <p>判定顺序固定为「取会话用户 → 查账号 → 验旧密码 → 校验新密码策略 → 新旧相同判定 → 落库 → 清标记」。
 * 旧密码先于策略校验：密码策略的提示会暴露「哪些规则不满足」，若在验明本人之前就返回，
 * 拿着一张待改密令牌的人可以先用错误旧密码刷出全部规则，等于把口令策略当公共信息送出去。
 *
 * <p>不开事务：改密只写一条 UPDATE（哈希与标记同语句），会话标记不是事务资源，
 * 加一个空事务只会让人以为这里还有第二次持久化。
 *
 * <p>已知缺口：旧密码错误目前不计入登录失败计数，因此持有待改密令牌的人可以无限次试旧密码。
 * 归入任务 1.8，需要先把 {@code PasswordLoginService} 的阈值与窗口常量共享出来，属重构，不在本次范围。
 */
@Service
public class PasswordChangeService {

    /** 密码规则无状态、不依赖容器，随用例直接持有即可；放进容器反而让一份纯规则多出装配失败的可能。 */
    private static final PasswordPolicy PASSWORD_POLICY = new PasswordPolicy();

    private final UserAccountRepository userAccountRepository;
    private final PasswordHasher passwordHasher;
    private final CurrentSession currentSession;

    public PasswordChangeService(UserAccountRepository userAccountRepository,
                                 PasswordHasher passwordHasher,
                                 CurrentSession currentSession) {
        this.userAccountRepository = userAccountRepository;
        this.passwordHasher = passwordHasher;
        this.currentSession = currentSession;
    }

    /**
     * 修改当前登录用户的密码，并解除「需先改密」状态。
     *
     * @param command 旧密码与新密码
     * @throws BizException 会话无效或账号已删除（40100）、旧密码错误（40101）、新密码不合规或与旧密码相同（40000）
     */
    public void change(ChangePasswordCommand command) {
        long userId = currentSession.currentUserId()
                // 能走到这里的请求都已通过登录校验，取不到会话说明账号在请求途中被删除或令牌已失效
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        if (!passwordHasher.matches(command.oldPassword(), account.passwordHash())) {
            // 用 40101 与登录凭据错误对齐，前端可按同一类「凭据不对」处理；
            // 文案换成「当前密码不正确」：此处用户已登录，不存在账号枚举问题，笼统的「登录名或密码不正确」只会让人困惑
            throw new BizException(ErrorCode.LOGIN_FAILED, "当前密码不正确");
        }

        List<PasswordRuleViolation> violations = PASSWORD_POLICY.check(command.newPassword());
        if (!violations.isEmpty()) {
            // 一次返回全部违规项：改一条错一条的提示等于让用户反复试
            throw new BizException(ErrorCode.BAD_REQUEST, PASSWORD_POLICY.describe(violations));
        }

        if (passwordHasher.matches(command.newPassword(), account.passwordHash())) {
            // 必须拿新明文比旧哈希：BCrypt 每次加盐，新旧哈希串永不相等，比串会永远判成「不相同」
            throw new BizException(ErrorCode.BAD_REQUEST, "新密码不能与当前密码相同");
        }

        Instant now = Instant.now();
        userAccountRepository.updatePassword(userId, passwordHasher.hash(command.newPassword()), now);
        // 先落库再清标记：写库失败时保持拦截，用户重试一次即可；顺序反过来会留下
        // 「会话已放行、库里仍是初始密码」的状态，初始密码从此不再被拦
        currentSession.clearMustChangePassword();
    }
}

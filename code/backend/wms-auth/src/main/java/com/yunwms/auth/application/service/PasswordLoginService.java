package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.PasswordLoginCommand;
import com.yunwms.auth.application.dto.AuthenticatedSubject;
import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditEntry;
import com.yunwms.auth.domain.model.SessionOrigin;
import com.yunwms.auth.domain.model.UserAccount;
import com.yunwms.auth.domain.port.AuditRecorder;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.PasswordHasher;
import com.yunwms.auth.domain.port.SessionTokenIssuer;
import com.yunwms.auth.domain.port.UserAccountRepository;
import com.yunwms.auth.domain.port.UserRoleQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 登录名加密码的登录用例。
 *
 * <p>两类主体走同一条链路，主体类型在查到账号后由 {@code sys_user.subject_type} 决定，
 * 因此不存在「走错入口就能绕开规则」的可能。
 *
 * <p>失败计数的阈值与窗口不在这里定义，统一取自 {@code LoginProtectionPolicy}：
 * 本人改密同样要按这套规则计数，两处各留一份常量就迟早会分叉。
 *
 * <p><b>登录成功与失败都留痕</b>：登录是唯一一处拿到明文凭据的入口，也是「谁在什么时候进了系统」
 * 这条链的起点。只记成功的话，暴力破解在审计里完全不可见——被锁的那个账号旁边，看不出有人试过它。
 * 审计对象是 {@code USER}（账号），动作是 {@code LOGIN}，对象标识用登录名：管理员按登录名查得到，
 * 用主键则要先知道 id。
 *
 * <p><b>对外文案与审计原因刻意不一致，请勿「统一」。</b>「账号不存在」与「密码错误」对外共用
 * {@link ErrorCode#LOGIN_FAILED} 与同一句提示，防止登录框变成账号枚举接口；审计是内部记录，
 * 必须区分真实原因——「试了个不存在的名字」与「密码打错」在应急时的处置完全不同。把两处对齐
 * 等于要么恢复账号枚举，要么让审计永远答不出「他到底试了什么」。
 *
 * <p>不开事务：失败计数与锁定截止时间在同一条 UPDATE 里完成，成功登录也只写一处，
 * 没有任何跨语句需要原子提交的状态；加一个空事务只会让人误以为登录做了更多持久化。
 *
 * <p><b>不开事务同时是「失败留痕不会被回滚」的前提。</b>审计写入用 {@code REQUIRED} 加入调用方事务，
 * 本类没有事务，它便自建事务并立即提交；随后抛出的 {@link BizException} 回滚不到那次写入。
 * 将来若给本类加上 {@code @Transactional}，失败审计会随异常一起回滚，登录取证链条上会静默缺一块——
 * 那时必须重新审视这条注释，而不是顺手加注解。
 *
 * <p>审计写失败时本类直接失败（异常透出，与 {@code AuditTrailAdapter} 同一口径）：宁可用不了，
 * 也不要一次没有留痕的登录。把异常吞掉换成「记日志后继续」，等于让审计在数据库出问题时静默失效。
 */
@Service
public class PasswordLoginService {

    /**
     * 登录失败摘要里对来源端的说明。
     *
     * <p>两端共用 {@code /api/auth/login}，失败时又没有会话可供回退判定，来源端只能留空。
     * 留空而不说明，事后无法区分「没认出来」与「这次操作没有端的概念」。
     */
    private static final String UNKNOWN_PORTAL_NOTE = "（来源端未知）";

    private final UserAccountRepository userAccountRepository;
    private final UserRoleQuery userRoleQuery;
    private final PasswordHasher passwordHasher;
    private final SessionTokenIssuer sessionTokenIssuer;
    private final CurrentSession currentSession;
    private final FailedAttemptRecorder failedAttemptRecorder;
    private final AuditRecorder auditRecorder;

    public PasswordLoginService(UserAccountRepository userAccountRepository,
                                UserRoleQuery userRoleQuery,
                                PasswordHasher passwordHasher,
                                SessionTokenIssuer sessionTokenIssuer,
                                CurrentSession currentSession,
                                FailedAttemptRecorder failedAttemptRecorder,
                                AuditRecorder auditRecorder) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleQuery = userRoleQuery;
        this.passwordHasher = passwordHasher;
        this.sessionTokenIssuer = sessionTokenIssuer;
        this.currentSession = currentSession;
        this.failedAttemptRecorder = failedAttemptRecorder;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 校验凭据并签发令牌。
     *
     * <p>判定顺序固定为「查账号 → 清已过期的锁定 → 验密码 → 查状态 → 查锁定」，不能调换：
     * 一旦先报状态，「账号已停用」「账号已锁定」就成了账号是否存在的探针，登录框会被用来枚举账号。
     * 先验密码，状态类提示就只对拿到正确密码的人可见——锁定期内提交错误密码同样只得到凭据错误。
     *
     * <p>四个失败出口都在抛出之前写一条审计：写入自建事务并先行提交，异常回滚不到它（见类注释）。
     *
     * @param command 登录入参
     * @return 令牌与主体信息
     * @throws BizException 凭据错误、账号停用或账号处于锁定期；凭据错误与账号不存在共用同一错误码与文案
     */
    public AuthenticatedSubject login(PasswordLoginCommand command) {
        Instant now = Instant.now();
        // 登录名两侧空格一律忽略；密码不处理，空格在密码里是合法字符
        String loginName = command.loginName().strip();
        Optional<UserAccount> found = userAccountRepository.findByLoginName(loginName);
        if (found.isEmpty()) {
            // 审计记「账号不存在」，对外仍是与密码错误同一句提示：这条差异正是防账号枚举的代价，不要抹平
            recordLoginFailure(loginName, "账号不存在");
            throw credentialsRejected();
        }
        UserAccount account = found.get();

        if (account.lockedUntil() != null && !account.isLockedAt(now)) {
            // 上一轮锁定已过期：先归零再校验。不清零的话，用户熬过 30 分钟后只要再错一次就立刻又被锁 30 分钟，
            // 惩罚没有停止条件；规格的「连续 5 次」应随这一次锁定结束而重新开始。
            // 归零同时清掉失败窗口起点，下一轮的 30 分钟从这里重新算。
            userAccountRepository.clearExpiredLock(account.id(), now);
        }

        if (!passwordHasher.matches(command.password(), account.passwordHash())) {
            // 锁定期内不再累加：这一轮已经在计数，此刻的失败不属于任何新一轮，累加只会把锁定无限延长
            if (!account.isLockedAt(now)) {
                failedAttemptRecorder.record(account.id(), now);
            }
            // 记真实原因：锁定期内提交错误密码同样记「密码错误」，不记成「锁定中」——后者会掩盖
            // 「有人在锁定期内继续试密码」这一事实
            recordLoginFailure(loginName, "密码错误");
            throw credentialsRejected();
        }
        if (account.isDisabled()) {
            recordLoginFailure(loginName, "账号已停用");
            throw new BizException(ErrorCode.LOGIN_ACCOUNT_DISABLED);
        }
        if (account.isLockedAt(now)) {
            recordLoginFailure(loginName, "账号锁定中");
            throw new BizException(ErrorCode.LOGIN_ACCOUNT_LOCKED);
        }

        List<String> roles = userRoleQuery.findActiveRoleCodesByUserId(account.id());
        // 先落库再签发：写失败时客户端拿不到令牌，避免出现「登录时间没记上但会话已建立」
        userAccountRepository.recordSuccessfulLogin(account.id(), now);
        String token = sessionTokenIssuer.issue(account,
                new SessionOrigin(now, command.sourceAddress(), command.clientIdentifier()));
        // 审计必须在签发之后：来源端判不出时会回退读会话里的主体类型（见 AuditRecorderImpl），
        // 会话是签发时才建立的，先记的话来源端只能落空
        recordLoginSuccess(account, roles);
        if (account.mustChangePassword()) {
            // 标记必须在签发之后写入：会话是签发时建立的，先标记拿不到会话。
            // 令牌照发而不是直接拒绝：规格要求「仅允许访问改密界面」，用户需要这个令牌才能去改密。
            currentSession.markMustChangePassword();
        }
        return new AuthenticatedSubject(token, account.subjectType(), account.ownerId(),
                account.displayName(), account.mustChangePassword(), roles);
    }

    /**
     * 写一条登录成功审计。
     *
     * <p>{@code after} 只放「谁、以什么身份、带着哪些角色、是否还需改密」：密码、密码哈希、
     * 密码长度与首字符一类信息一律不写——审计只追加，写进去就撤不回来，而这些信息能缩小搜索空间。
     *
     * @param account 登录成功的账号
     * @param roles   该账号当前生效的角色编码，登录响应里的同一份
     */
    private void recordLoginSuccess(UserAccount account, List<String> roles) {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("loginName", account.loginName());
        after.put("subjectType", account.subjectType().name());
        after.put("roles", roles);
        after.put("mustChangePassword", account.mustChangePassword());
        auditRecorder.record(new AuditEntry(AuditBizType.USER, account.loginName(), AuditAction.LOGIN,
                "登录成功 " + account.loginName(), null, after));
    }

    /**
     * 写一条登录失败审计。
     *
     * <p>对象标识用用户尝试的登录名而不是账号标识：账号不存在时根本没有标识，而「有人试过这个名字」
     * 本身就是要留下的事实。
     *
     * <p>摘要里没有密码，也没有长度、首字符之类的间接信息，理由同 {@link #recordLoginSuccess}。
     *
     * @param loginName 用户尝试的登录名，已去两侧空白
     * @param reason    真实失败原因，可与对外文案不同（见类注释）
     */
    private void recordLoginFailure(String loginName, String reason) {
        auditRecorder.record(new AuditEntry(AuditBizType.USER, loginName, AuditAction.LOGIN,
                "登录失败：" + reason + UNKNOWN_PORTAL_NOTE, null, null));
    }

    private static BizException credentialsRejected() {
        return new BizException(ErrorCode.LOGIN_FAILED);
    }
}

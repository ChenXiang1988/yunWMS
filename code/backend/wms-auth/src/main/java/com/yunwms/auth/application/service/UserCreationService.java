package com.yunwms.auth.application.service;

import com.yunwms.auth.application.audit.UserAuditSnapshots;
import com.yunwms.auth.application.command.CreateUserCommand;
import com.yunwms.auth.application.dto.CreatedUser;
import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditEntry;
import com.yunwms.auth.domain.model.NewUserAccount;
import com.yunwms.auth.domain.model.PasswordPolicy;
import com.yunwms.auth.domain.model.PasswordRuleViolation;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.port.AuditRecorder;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.PasswordHasher;
import com.yunwms.auth.domain.port.UserAccountRegistrar;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理员建号的用例。
 *
 * <p>校验顺序固定为「取操作人 → 主体与货主 → 邮箱 → 初始密码策略」，落库最后做。三项前置判定
 * 按「错得越早、改起来越省事」排列：主体选错会连带决定后两项是否必填，所以先判；
 * 邮箱缺失是漏填，密码不合规是选得不够强，改密码比补邮箱费神。
 *
 * <p>登录名与邮箱的唯一性<b>不在这里判</b>：唯一约束建在 {@code lower(login_name)} 与
 * {@code lower(email)} 的表达式唯一索引上，服务层「先查再插」在并发下无效——两个请求会同时查到
 * 「不存在」，然后一个成功一个拿到数据库异常，与不查的痛苦程度相同，却多了一次往返。
 * 唯一冲突只在 {@code INSERT} 时由数据库暴露，这里捕获后转成 {@link ErrorCode#LOGIN_NAME_TAKEN}：
 * 前端要的是「登录名这个字段被占了」，而不是「数据冲突」。
 *
 * <p>建号写两张表（{@code sys_user} 与 {@code sys_user_role}），必须同一个事务：
 * 只写进账号没写角色时，管理员看到「建成功」而权限少了，且没有任何入口能看出缺哪一步。
 *
 * <p><b>留痕与两张表同一个事务</b>：审计对象是 {@code USER}，对象标识用登录名——管理端按登录名
 * 核对账号，用主键则要先知道一个界面上不出现的数字。审计写在这两条写入之后、同一个事务里：
 * 唯一冲突抛出时整单回滚，那条「建了但没建成」的日志不会留下。
 *
 * <p><b>快照里没有密码。</b>不含哈希，也不含明文、长度、首字符：审计表只追加、不可修改，
 * 凭据一旦写进去就撤不回来。见 {@link UserAuditSnapshots} 的白名单。
 */
@Service
public class UserCreationService {

    /** 密码规则无状态，随用例直接持有；放进容器只会让一份纯规则多出装配失败的可能。 */
    private static final PasswordPolicy PASSWORD_POLICY = new PasswordPolicy();

    private final UserAccountRegistrar userAccountRegistrar;
    private final PasswordHasher passwordHasher;
    private final CurrentSession currentSession;
    private final AuditRecorder auditRecorder;

    public UserCreationService(UserAccountRegistrar userAccountRegistrar,
                              PasswordHasher passwordHasher,
                              CurrentSession currentSession,
                              AuditRecorder auditRecorder) {
        this.userAccountRegistrar = userAccountRegistrar;
        this.passwordHasher = passwordHasher;
        this.currentSession = currentSession;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 创建一个用户账号，初始密码只够换一次登录。
     *
     * <p>落库值固定为 {@code must_change_password = true} 与 {@code status = 'ACTIVE'}：
     * 规格要求「初始密码需首次登录修改」，建号时若允许选择，管理员就能建出永不过期的初始口令；
     * 新建账号一律启用，停用是后续操作而非创建时状态。
     *
     * @param command 建号入参；{@code subjectType} 必须非空，{@code roleIds} 中的元素必须非空
     *                （两者都由请求体校验拦在前面的）
     * @return 新账号的标识与登录名
     * @throws BizException 当前会话无效（40100）；主体与货主不匹配、内部员工缺邮箱、
     *                      密码不合规、邮箱已被占用、货主或角色不存在（40000）；
     *                      登录名已被占用（40901）
     */
    @Transactional
    public CreatedUser create(CreateUserCommand command) {
        long operatorId = currentSession.currentUserId()
                // 鉴权过滤器已拦过未登录请求，走到这里取不到会话只能是账号在请求途中被删除或令牌已失效；
                // 此时不能继续：created_by / updated_by 会写成空值，事后无从定位这条账号是谁建的
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        SubjectType subjectType = command.subjectType();
        Long ownerId = command.ownerId();
        String loginName = stripToNull(command.loginName());
        String displayName = stripToNull(command.displayName());
        String email = stripToNull(command.email());
        String wecomUserId = stripToNull(command.wecomUserId());

        // 与 ck_user_subject 同一口径：库约束是最后一道防线，但它的报错只有约束名，
        // 变不成「货主为必填项」这样的提示，所以建号时先判一次
        if (subjectType == SubjectType.OWNER && ownerId == null) {
            throw BizException.badRequest("货主用户必须指定所属货主");
        }
        if (subjectType == SubjectType.STAFF && ownerId != null) {
            // 员工带 ownerId 会把「员工」与「货主账号」的边界搅浑：数据范围应通过角色配置，而不是建号时绑一个货主
            throw BizException.badRequest("内部员工不得绑定所属货主");
        }
        if (subjectType == SubjectType.STAFF && email == null) {
            // 与 ck_user_email_required 同一口径：员工靠邮箱自助找回密码，缺了就只能由管理员重置
            throw BizException.badRequest("内部员工必须填写邮箱");
        }

        List<PasswordRuleViolation> violations = PASSWORD_POLICY.check(command.initialPassword());
        if (!violations.isEmpty()) {
            // 一次返回全部违规项：改一条错一条的提示等于让管理员反复试
            throw BizException.badRequest(PASSWORD_POLICY.describe(violations));
        }

        NewUserAccount account = new NewUserAccount(loginName, displayName, subjectType, ownerId, email, wecomUserId,
                // 明文到此为止：跨层传递的只有哈希，日志与异常里都不会出现初始密码
                passwordHasher.hash(command.initialPassword()));
        List<Long> roleIds = command.roleIds() == null ? List.of() : command.roleIds();
        long userId;
        try {
            userId = userAccountRegistrar.register(account, roleIds, operatorId);
        } catch (DataIntegrityViolationException e) {
            // 冲突时一行都没留下，也不留审计：这次建号没有发生
            throw translateConstraintViolation(e);
        }
        auditRecorder.record(new AuditEntry(AuditBizType.USER, loginName, AuditAction.CREATE,
                "新建用户 " + loginName, null, UserAuditSnapshots.ofCreated(account)));
        // 回 loginName 而不是 auth 的 displayName：出问题时管理员要靠它去登录框里核对这个账号
        return new CreatedUser(userId, loginName);
    }

    /**
     * 把库约束冲突翻译成可展示的业务错误。
     *
     * <p>按约束名匹配而不是按 SQLState：违反唯一索引、外键、CHECK 的 SQLState 都是 23xxx，
     * 区分不了是哪一处；约束名不会随 {@code lc_messages} 或其他区域设置改变，是唯一稳定的标识。
     * 换数据库或加了改写异常的信息中间层时，这里需要重新核对。
     *
     * <p>不认识的约束原样抛出：那是本用例的假设被打破了（例如新加了 CHECK），
     * 猜一个错误码返回给用户，等于把「我们不知道自己违反了哪条规则」伪装成一次正常的拒绝。
     *
     * @param e 数据库抛出的完整性约束冲突
     * @return 已翻译的业务异常；约束未知时为原始异常，由全局异常处理归到 50000
     */
    private static RuntimeException translateConstraintViolation(DataIntegrityViolationException e) {
        String cause = e.getMostSpecificCause().getMessage();
        if (cause == null) {
            return e;
        }
        if (cause.contains("ux_sys_user_login_name")) {
            // 用 40901 而不是泛化的 40900：前端要能把这个提示挂在登录名字段上
            return new BizException(ErrorCode.LOGIN_NAME_TAKEN);
        }
        if (cause.contains("ux_sys_user_email")) {
            return BizException.badRequest("邮箱已被其他账号使用");
        }
        if (cause.contains("fk_sys_user_owner")) {
            // 用 40000 而不是 409xx：冲突指的是「与已有数据撞上」，而这里是入参引用了一个不存在的货主，
            // 属于参数问题，管理员改一个 id 就能过；409 段位留给「重名」「状态不允许」这类需要先处理既有数据的场景
            return BizException.badRequest("指定的货主不存在");
        }
        if (cause.contains("sys_user_role_role_id_fkey")) {
            // 与货主同一类：传了一个不存在的角色标识，属于入参问题
            return BizException.badRequest("指定的角色不存在");
        }
        return e;
    }

    /**
     * 去掉两侧空白，空串按未填处理。
     *
     * <p>登录名两侧空格会让「看起来一样」的两个账号并存（唯一索引按 {@code lower(login_name)} 判等，
     * 不裁掉空格就会建出 {@code " zhangsan"} 与 {@code "zhangsan"}）；空串一律落成 NULL，
     * 否则 {@code email = ''} 会绕过 {@code email IS NOT NULL}，让员工在没有邮箱的情况下建号成功。
     *
     * @param value 原始值，可为 {@code null}
     * @return 裁剪后的值；{@code null}、空串或全空白时为 {@code null}
     */
    private static String stripToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}

package com.yunwms.auth.application.service;

import com.yunwms.auth.application.audit.UserAuditSnapshots;
import com.yunwms.auth.application.command.AssignRolesCommand;
import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditEntry;
import com.yunwms.auth.domain.model.RoleIdentity;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.port.AuditRecorder;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.RoleQuery;
import com.yunwms.auth.domain.port.UserQuery;
import com.yunwms.auth.domain.port.UserRoleRegistrar;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 给用户分配角色的用例：整批替换某个账号的角色。
 *
 * <p>这是本批唯一会改变「谁能做什么」的入口。校验顺序固定为「取操作人 → 账号存在 →
 * 角色存在 → 不把自己最后的初始角色换掉 → 落库」，按「错得越早、改起来越省事」排列：
 * 账号标识写错与角色标识写错都是改入参，而最后一项被拒绝时，要改的是这次操作本身。
 *
 * <p><b>为什么先判角色是否存在</b>，而建号那边交给外键：建号是插入新行，外键拦下之后整单回滚，
 * 库里什么都不会留下；分配角色是<b>先删后插</b>，靠外键发现「角色不存在」时删除已经执行过，
 * 虽然同一个事务会回滚，但把「入参有错」和「已经动过数据」两步搅在一起，会让后续加审计、
 * 加通知时难以判断哪些语句已经跑过。预查一次只需一条语句。
 *
 * <p><b>不能把自己最后的内置角色换掉</b>：内置角色（超级管理员）是「动态展开为全部权限点」的
 * 唯一来源，管理员在替换自己的角色时若把它去掉，系统里可能再没有任何能配置权限的账号，
 * 而恢复只能改库。规格没有要求这一条，它防的是不可逆后果：误操作一次，界面上再也没有入口。
 * 拒绝用 {@code ErrorCode#CONFLICT}（409 段位）而不是 40000：入参本身合法，被拒绝的是它造成的状态；
 * 本批不给它单独的码，因为 {@code ErrorCode} 不在改动白名单内，新增码需要另行放行。
 *
 * <p>替换（先删后插）而不是求差集：求差集要先读出现有角色、在应用层算增删，两个并发请求
 * 会各自基于同一份旧快照算出「该删哪些」，后提交的那次把前一次的增量覆盖掉。
 * 而「替换成这一批」是不带前置状态的意图，谁最后提交，结果就是谁提交的那一批。
 *
 * <p><b>留痕动作是 {@code PERMISSION_CHANGE} 而不是 {@code UPDATE}：</b>这是本批唯一会改变
 * 「谁能做什么」的入口，按 {@code UPDATE} 记会与「改个展示名」落在同一个动作下，事后按动作筛选
 * 权限变更时两者分不开。摘要写「分配角色 {登录名}」，对象标识用登录名（管理端按登录名追溯）。
 *
 * <p><b>两份快照记的是「改成了哪些角色」</b>，用 {@link UserAuditSnapshots#ofRoleAssignment} 这份
 * 专用形状（只含 {@code roleCodes}）：{@code before} 是下面那次详情查询已经读回来的角色编码，
 * {@code after} 是本次提交并校验通过的那一批。账号快照在这里不适用——它的白名单里没有角色集合，
 * 两侧都套用会得到两份逐字相同的值，日志便答不出这条权限变更到底改了什么。
 *
 * <p><b>改前改后完全一致时也照常留痕</b>：分配动作本身就是一次显式的权限确认，
 * 「这些角色被确认过」是可追溯的事实，而两份快照相等恰好是「这次确认没有改变结果」的证据。
 */
@Service
public class UserRoleAssignmentService {

    private final UserQuery userQuery;
    private final RoleQuery roleQuery;
    private final UserRoleRegistrar userRoleRegistrar;
    private final CurrentSession currentSession;
    private final AuditRecorder auditRecorder;

    public UserRoleAssignmentService(UserQuery userQuery,
                                     RoleQuery roleQuery,
                                     UserRoleRegistrar userRoleRegistrar,
                                     CurrentSession currentSession,
                                     AuditRecorder auditRecorder) {
        this.userQuery = userQuery;
        this.roleQuery = roleQuery;
        this.userRoleRegistrar = userRoleRegistrar;
        this.currentSession = currentSession;
        this.auditRecorder = auditRecorder;
    }

    /**
     * 用给定的角色集合替换某个账号的全部角色。
     *
     * @param command 分配入参；{@code roleIds} 为空列表表示移除全部角色
     * @throws BizException 当前会话无效（40100）；用户不存在（40400）；
     *                      请求里有不存在或已删除的角色（40000）；
     *                      目标账号是操作人自己且替换后不再持有内置角色（40900）
     */
    @Transactional
    public void assign(AssignRolesCommand command) {
        long operatorId = currentSession.currentUserId()
                // 鉴权过滤器已拦过未登录请求，走到这里取不到会话只能是账号在请求途中被删除或令牌已失效；
                // 此时不能继续：updated_by 会写成空值，事后无从定位这条授权是谁改的
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        long targetUserId = command.userId();
        // 不存在的账号直接按 404 处理：否则写入会被外键拦下，变成与「入参写错」无关的 500。
        // 读详情而不是只判存在性：审计的对象标识要用登录名，变更前的角色编码也来自这次查询
        // （它已经把该账号的授权行一起读出来），所以审计不必再查一次
        UserProfile current = userQuery.findById(targetUserId)
                .orElseThrow(() -> BizException.notFound("用户不存在"));

        // 角色分配是集合语义：同一标识传两次不该变成主键冲突，也不该让「存在性」判定多数出一个
        Set<Long> roleIds = command.roleIds() == null
                ? Set.of()
                : new LinkedHashSet<>(command.roleIds());
        List<RoleIdentity> identities = roleIds.isEmpty() ? List.of() : roleQuery.findIdentities(roleIds);
        if (identities.size() != roleIds.size()) {
            // 已逻辑删除的角色同样落在这里：它对管理界面已经不存在，不能再被分配
            throw BizException.badRequest("指定的角色不存在");
        }

        if (targetUserId == operatorId && identities.stream().noneMatch(RoleIdentity::builtin)) {
            throw BizException.conflict("不能移除自己最后的内置角色");
        }

        try {
            userRoleRegistrar.replaceRoles(targetUserId, roleIds, operatorId);
        } catch (DataIntegrityViolationException e) {
            throw translateConstraintViolation(e);
        }

        String loginName = current.summary().loginName();
        // 本方法有事务，审计随之一起提交或回滚：失败的分配不会留下一条「改了权限」的记录。
        // 两侧的角色编码都有现成来源，不为审计额外发查询：改前来自详情查询，改后来自刚校验过的那批角色身份
        auditRecorder.record(new AuditEntry(AuditBizType.USER, loginName, AuditAction.PERMISSION_CHANGE,
                "分配角色 " + loginName,
                UserAuditSnapshots.ofRoleAssignment(current.summary().roleCodes()),
                UserAuditSnapshots.ofRoleAssignment(identities.stream().map(RoleIdentity::code).toList())));
    }

    /**
     * 把库约束冲突翻译成可展示的业务错误。
     *
     * <p>按约束名匹配而不是按 SQLState：违反唯一索引、外键、CHECK 的 SQLState 都是 23xxx，
     * 区分不了是哪一处；约束名不会随 {@code lc_messages} 或其他区域设置改变。
     *
     * <p>能走到这里的只有一种情形：角色在上面那次存在性判定之后、插入之前被物理删除。
     * 概率极低但并非不可能，翻译成「指定的角色不存在」比暴露一个 500 更接近事实。
     *
     * @param e 数据库抛出的完整性约束冲突
     * @return 已翻译的业务异常；约束未知时为原始异常，由全局异常处理归到 50000
     */
    private static RuntimeException translateConstraintViolation(DataIntegrityViolationException e) {
        String cause = e.getMostSpecificCause().getMessage();
        if (cause != null && cause.contains("sys_user_role_role_id_fkey")) {
            return BizException.badRequest("指定的角色不存在");
        }
        return e;
    }
}

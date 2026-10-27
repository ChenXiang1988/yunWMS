package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.ChangeRoleStatusCommand;
import com.yunwms.auth.application.command.ConfigureRoleDataScopeCommand;
import com.yunwms.auth.application.command.UpdateRoleCommand;
import com.yunwms.auth.domain.model.RoleSummary;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.DataScopeOwnerRegistrar;
import com.yunwms.auth.domain.port.RoleQuery;
import com.yunwms.auth.domain.port.RoleUpdater;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/**
 * 管理员维护已有角色的用例：编辑基本信息与授权、配置数据范围、启用停用、删除。
 *
 * <p>四个用例放在同一个类里，是因为它们共用同一条前置规则：<b>内置角色一律拒绝</b>。
 * 这条规则的依据是它的失效后果不可逆——超级管理员的权限在鉴权时动态展开为全部权限点，
 * 一旦被停用或删除，系统里再没有任何账号能配置权限，而界面上没有恢复入口（改库除外）。
 * 把它拆到三个类里各写一遍，改一处忘一处时就会出现「能停用、也能删除」的漏网路径。
 *
 * <p>校验顺序固定为「取操作人 → 角色存在且非内置 → 该用例自己的约束 → 落库」，
 * 按「错得越早、改起来越省事」排列：前两项是改入参，后面被拒绝时要改的是这次操作本身。
 *
 * <p>与建角色分开成两个类：{@link RoleCreationService} 写的是「从无到有」，要把 {@code builtin}、
 * {@code data_scope_type}、{@code status} 三个创建期固定值显式落库；本类只碰管理员真正能改的字段，
 * 混在一起会让「编辑顺手把数据范围写回 CUSTOM」这种缺陷有地方可写。
 *
 * <p>不写审计日志（{@code sys_audit_log}）：那属于审计能力（2.8），本类只保证
 * {@code updated_by} / {@code updated_at} 记下操作人与时刻。
 */
@Service
public class RoleModificationService {

    private final RoleQuery roleQuery;
    private final RoleUpdater roleUpdater;
    private final DataScopeOwnerRegistrar dataScopeOwnerRegistrar;
    private final DataScopeOwnerValidator dataScopeOwnerValidator;
    private final CurrentSession currentSession;

    public RoleModificationService(RoleQuery roleQuery,
                                   RoleUpdater roleUpdater,
                                   DataScopeOwnerRegistrar dataScopeOwnerRegistrar,
                                   DataScopeOwnerValidator dataScopeOwnerValidator,
                                   CurrentSession currentSession) {
        this.roleQuery = roleQuery;
        this.roleUpdater = roleUpdater;
        this.dataScopeOwnerRegistrar = dataScopeOwnerRegistrar;
        this.dataScopeOwnerValidator = dataScopeOwnerValidator;
        this.currentSession = currentSession;
    }

    /**
     * 用入参<b>整批替换</b>角色的数据范围：类型与明细一起换。
     *
     * <p>独立成一条用例而不是并入 {@link #update}：编辑资料与配置范围是两件事，合在一起之后
     * 「只改名称」的请求必须把范围也提交，漏传就等于清空。
     *
     * <p>两处写入（{@code sys_role.data_scope_type} 与 {@code sys_data_scope_owner}）在同一个事务里：
     * 只改了类型时，{@code CUSTOM} 的明细仍是上一个类型留下的旧数据，那一段中间态里的范围解析
     * 读到的是谁的数据取决于提交顺序。
     *
     * <p>停用中的角色同样允许配置：管理员通常先配好范围再启用，禁止只会逼他先启用一个没配好的角色。
     *
     * @param command 配置入参；{@code ownerIds} 在非 {@code CUSTOM} 时必须为空
     * @throws BizException 当前会话无效（40100）；角色不存在（40400）；内置角色（40900）；
     *                      类型为空、类型与明细矛盾、明细里有不存在或未启用的货主（40000）
     */
    @Transactional
    public void configureDataScope(ConfigureRoleDataScopeCommand command) {
        long operatorId = requireOperatorId();
        RoleSummary role = requireModifiableRole(command.roleId(), "配置数据范围");

        Set<Long> ownerIds = command.ownerIds() == null ? Set.of() : Set.copyOf(command.ownerIds());
        dataScopeOwnerValidator.requireConfigurable(command.dataScopeType(), ownerIds);

        roleUpdater.updateDataScopeType(role.id(), command.dataScopeType(), operatorId, Instant.now());
        // 非 CUSTOM 时传空集合：ALL 与 ASSIGNED 是规则而不是名单，留着旧明细会让「这条明细算不算数」
        // 变成只有读过解析逻辑才能回答的问题
        dataScopeOwnerRegistrar.replaceRoleOwners(role.id(),
                command.dataScopeType().usesOwnerDetails() ? ownerIds : Set.of());
    }

    /**
     * 用入参覆盖角色的名称、备注与授权。
     *
     * <p>编码不在入参里：它是程序引用角色的标识，与用户的登录名同一口径，改了等于换一个角色。
     *
     * <p>授权是替换语义，与角色行的更新在同一个事务里完成：只改名不换授权、或换了授权没改名，
     * 都不能对管理员可见。不存在的菜单或权限点标识由外键拦下并整单回滚——那一判「改一个 id 就能过」，
     * 属于参数错误，而不是状态冲突。
     *
     * @param command 编辑入参；{@code remark} 为 {@code null} 表示清空备注，
     *                {@code menuIds} 为空集合表示清空全部授权
     * @throws BizException 当前会话无效（40100）；角色不存在（40400）；名称为空、
     *                      菜单或权限点不存在（40000）；内置角色（40900）
     */
    @Transactional
    public void update(UpdateRoleCommand command) {
        long operatorId = requireOperatorId();
        RoleSummary role = requireModifiableRole(command.roleId(), "编辑");

        String name = stripToNull(command.name());
        if (name == null) {
            // 库里的 NOT NULL 只能给出约束名，变不成「角色名称不能为空」这样的提示；
            // 请求体上的 @NotBlank 是同一口径的前置拦截，这里兜住非 HTTP 入口
            throw BizException.badRequest("角色名称不能为空");
        }

        // 空集合是合法状态（清空该角色的全部授权）；请求体层已拒绝「省略 menuIds」，
        // 这里把 null 按空集合处理，与建角色对 menuIds 的口径一致
        Set<Long> menuIds = command.menuIds() == null ? Set.of() : Set.copyOf(command.menuIds());
        try {
            roleUpdater.updateWithMenus(role.id(), name, stripToNull(command.remark()), menuIds,
                    operatorId, Instant.now());
        } catch (DataIntegrityViolationException e) {
            throw RoleConstraintTranslator.translate(e);
        }
    }

    /**
     * 把角色改成目标状态。
     *
     * <p>目标状态与当前状态相同时直接返回成功，不写库：状态流转是幂等的，重复提交不该报错；
     * 而这里也没有「顺带要做的事」需要重放（与停用账号不同，那边还要失效会话）。
     *
     * <p>停用立即生效：鉴权侧每次请求都按 {@code status = 'ACTIVE'} 与 {@code deleted_at IS NULL}
     * 过滤角色，持有该角色的用户下一次请求就不再拥有它带来的权限点，不需要等会话过期，
     * 因此本用例不额外处理已登录的会话。
     *
     * @param command 启停入参
     * @throws BizException 当前会话无效（40100）；角色不存在（40400）；内置角色（40900）
     */
    public void changeStatus(ChangeRoleStatusCommand command) {
        long operatorId = requireOperatorId();
        RoleSummary role = requireModifiableRole(command.roleId(), "启停");
        if (role.status() == command.status()) {
            return;
        }
        roleUpdater.updateStatus(role.id(), command.status(), operatorId, Instant.now());
    }

    /**
     * 软删除角色，前提是没有任何用户仍持有它。
     *
     * <p><b>占用校验必须在删除之前</b>：规格要求「拒绝删除并提示占用人数」。这也是唯一一道校验——
     * {@code sys_user_role.role_id} 是 {@code ON DELETE CASCADE}，数据库不会因为角色仍有用户而拒绝
     * 任何删除动作；若将来有人把这里换成物理删除，少了这一判，删除会静默带走关联记录、不报任何错，
     * 那些用户静默失去这个角色带来的权限。判定之后、删除之前仍有一个「刚被分配」的窗口
     * （{@link com.yunwms.auth.domain.port.RoleRegistrar} 已说明这是可接受的最后一致性）：
     * 软删除不触发级联，那次分配留下的关联行仍指向一个已删除的角色，鉴权时按 {@code deleted_at}
     * 过滤后不生效。
     *
     * @param roleId 角色标识
     * @throws BizException 当前会话无效（40100）；角色不存在（40400）；内置角色（40900）；
     *                      仍有用户持有该角色（40900，提示里带占用人数）
     */
    public void delete(long roleId) {
        long operatorId = requireOperatorId();
        RoleSummary role = requireModifiableRole(roleId, "删除");
        if (role.userCount() > 0) {
            // 带上人数：只说「角色被占用」时，管理员还得自己去找是谁在用
            throw BizException.conflict("该角色仍被 " + role.userCount() + " 个用户使用，请先解除分配");
        }
        roleUpdater.softDelete(role.id(), operatorId, Instant.now());
    }

    /**
     * 取角色并确认它可被维护。
     *
     * <p>内置角色不可编辑、不可启停、不可删除：它的权限在鉴权时动态展开为全部权限点，
     * 编辑它既没有意义，也会让管理员误以为「少勾一个权限点就没有那个权限」；停用或删除它则会让
     * 系统失去唯一的权限配置入口。
     *
     * @param roleId 角色标识
     * @param action 本次操作的中文名称，用于拼出「内置角色不可 X」的提示
     * @return 角色摘要
     * @throws BizException 角色不存在或已逻辑删除（40400）；内置角色（40900）
     */
    private RoleSummary requireModifiableRole(long roleId, String action) {
        RoleSummary role = roleQuery.findById(roleId)
                .orElseThrow(() -> BizException.notFound("角色不存在"));
        if (role.builtin()) {
            throw BizException.conflict("内置角色不可" + action);
        }
        return role;
    }

    /**
     * 取当前操作人标识。
     *
     * @return 操作人标识
     * @throws BizException 当前会话没有有效登录用户（40100）
     */
    private long requireOperatorId() {
        return currentSession.currentUserId()
                // 鉴权过滤器已拦过未登录请求，走到这里取不到会话只能是账号在请求途中被删除或令牌已失效；
                // 此时不能继续：updated_by 会写成空值，事后无从定位这条角色是谁改的
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }

    /**
     * 去掉两侧空白，空串按未填处理。
     *
     * <p>回显与对比都按裁剪后的值：{@code remark = ''} 与「没填备注」对使用者没有区别，
     * 名称两侧的空格则会让列表里两个「客服」看起来一模一样。
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

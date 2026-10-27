package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.CreateRoleCommand;
import com.yunwms.auth.application.dto.CreatedRole;
import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.model.NewRole;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.RoleRegistrar;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 管理员建角色的用例。
 *
 * <p>校验顺序固定为「取操作人 → 编码必填 → 编码格式 → 名称必填 → 落库」。编码排在名称之前，
 * 是因为编码是程序引用的标识：填错了将来要在配置里改，比改一个展示名费事得多。
 *
 * <p>编码的<b>唯一性不在这里判</b>：唯一约束建在 {@code ux_sys_role_code} 上，服务层
 * 「先查再插」在并发下无效——两个请求会同时查到「不存在」，然后一个成功一个拿到数据库异常，
 * 与不查的痛苦程度相同，却多一次往返。唯一冲突只在 {@code INSERT} 时由数据库暴露，
 * 这里捕获后交给 {@link RoleConstraintTranslator} 转成 40902：前端要的是「编码这个字段被占了」，
 * 而不是「数据冲突」。{@code ux_sys_role_code} 不带 {@code deleted_at} 条件，因此已软删除的角色
 * 仍占着它的编码，提示语必须写明这一点（见 {@code ErrorCode#ROLE_CODE_TAKEN}）。
 *
 * <p>勾选的菜单与权限点<b>也不预查</b>，理由同上且更明显：{@code sys_role_menu.menu_id} 上已有
 * 外键，插入时数据库会拦下不存在的 id；预查只是把同一条判定做两遍，还留下「查完到插入之间
 * 该 id 被删掉」的窗口。外键冲突同样由 {@link RoleConstraintTranslator} 翻成参数错误。
 *
 * <p>写三张表（{@code sys_role}、{@code sys_role_menu} 与 {@code sys_data_scope_owner}）必须同一个事务：
 * 只写进角色没写授权或范围时，管理员看到「建成功」而角色一个权限、一个货主都看不到，
 * 与「故意建一个无权限角色」在库里无法区分。
 *
 * <p>不写审计日志（{@code sys_audit_log}）：那属于审计能力（2.8），本用例只保证
 * {@code created_by} / {@code updated_by} 记下操作人。
 */
@Service
public class RoleCreationService {

    /** 未指定数据范围时的默认类型：保守方向，空明细即无权。 */
    private static final DataScopeType DEFAULT_DATA_SCOPE_TYPE = DataScopeType.CUSTOM;

    private final RoleRegistrar roleRegistrar;
    private final CurrentSession currentSession;
    private final DataScopeOwnerValidator dataScopeOwnerValidator;

    public RoleCreationService(RoleRegistrar roleRegistrar,
                               CurrentSession currentSession,
                               DataScopeOwnerValidator dataScopeOwnerValidator) {
        this.roleRegistrar = roleRegistrar;
        this.currentSession = currentSession;
        this.dataScopeOwnerValidator = dataScopeOwnerValidator;
    }

    /**
     * 创建一个角色，落库为启用、非内置。
     *
     * <p>数据范围可选：未指定类型时取 {@link DataScopeType#CUSTOM} 加空明细（保守方向，空集即无权）。
     * 明细里的货主必须是存在且启用中的货主，判定委托给 {@link DataScopeOwnerValidator}——
     * 那条规则三个入口共用，写在这里会让「建角色」与「改范围」两处迟早判得不一样。
     *
     * @param command 建角色入参
     * @return 新角色的标识与编码
     * @throws BizException 当前会话无效（40100）；编码或名称为空、编码格式不合规、
     *                      数据范围类型与明细矛盾、明细里有不存在或未启用的货主、
     *                      勾选的菜单或权限点不存在（40000）；编码已被占用（40902）
     */
    @Transactional
    public CreatedRole create(CreateRoleCommand command) {
        long operatorId = currentSession.currentUserId()
                // 鉴权过滤器已拦过未登录请求，走到这里取不到会话只能是账号在请求途中被删除或令牌已失效；
                // 此时不能继续：created_by / updated_by 会写成空值，事后无从定位这条角色是谁建的
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        String code = stripToNull(command.code());
        if (code == null) {
            // 与 sys_role.code 的 NOT NULL 同一口径：库约束的报错只有约束名，变不成一句提示
            throw BizException.badRequest("角色编码不能为空");
        }
        if (!NewRole.isWellFormedCode(code)) {
            throw BizException.badRequest(NewRole.CODE_FORMAT_HINT);
        }

        String name = stripToNull(command.name());
        if (name == null) {
            throw BizException.badRequest("角色名称不能为空");
        }

        // 空集合是合法状态：建一个没有任何权限的角色，用于「先建角色再逐个补授权」的配置流程
        Set<Long> menuIds = command.menuIds() == null
                ? Set.of()
                : Set.copyOf(command.menuIds());

        DataScopeType dataScopeType = command.dataScopeType() == null
                ? DEFAULT_DATA_SCOPE_TYPE
                : command.dataScopeType();
        Set<Long> ownerIds = command.ownerIds() == null ? Set.of() : Set.copyOf(command.ownerIds());
        dataScopeOwnerValidator.requireConfigurable(dataScopeType, ownerIds);

        NewRole role = new NewRole(code, name, stripToNull(command.remark()), menuIds, dataScopeType, ownerIds);
        try {
            return new CreatedRole(roleRegistrar.register(role, operatorId), code);
        } catch (DataIntegrityViolationException e) {
            throw RoleConstraintTranslator.translate(e);
        }
    }

    /**
     * 去掉两侧空白，空串按未填处理。
     *
     * <p>编码两侧的空格会让「看起来一样」的两个编码并存（唯一索引按原值判等，不裁掉空格就建得出
     * {@code " CS_STAFF"}）；空串一律落成 NULL，否则 {@code remark = ''} 与「没填备注」会在库里
     * 变成两种状态，而它们对使用者没有区别。
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

package com.yunwms.auth.application.service;

import com.yunwms.auth.application.command.ConfigureUserDataScopeCommand;
import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.DataScopeOwnerRegistrar;
import com.yunwms.auth.domain.port.UserAccountUpdater;
import com.yunwms.auth.domain.port.UserQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/**
 * 配置或清除用户级数据范围覆盖的用例。
 *
 * <p>校验顺序固定为「取操作人 → 账号存在 → 主体类型允许覆盖 → 类型与明细搭配 → 落库」。
 * 主体类型排在明细之前：货主账号的范围由所属货主强制决定，无论提交什么明细都不会生效，
 * 先判它能省掉一次跨模块查询，也让错误提示说在点子上。
 *
 * <p><b>两种「没有范围」必须分开：</b>
 * <ul>
 *   <li>{@code dataScopeType = null}：清除覆盖，生效范围回落到角色并集；</li>
 *   <li>{@code dataScopeType = CUSTOM} 且明细为空：<b>有</b>覆盖，结果是无权。</li>
 * </ul>
 * 合成一种会让「管理员清不掉某个人的权限」或「清除覆盖没生效」二者必居其一。因此类型为
 * {@code null} 时明细必须为空——带着明细却要求清除，是自相矛盾的请求。
 *
 * <p>两处写入（{@code sys_user.data_scope_type} 与 {@code sys_data_scope_owner}）在同一个事务里：
 * 只改了类型时，库里的明细还是上一次配置留下的，那一段中间态的范围解析会读到不属于当前配置的货主。
 *
 * <p>不写审计日志（{@code sys_audit_log}）：那属于审计能力（2.8），本用例只保证
 * {@code sys_user.updated_by} 记下操作人。
 */
@Service
public class UserDataScopeConfigurationService {

    private final UserQuery userQuery;
    private final UserAccountUpdater userAccountUpdater;
    private final DataScopeOwnerRegistrar dataScopeOwnerRegistrar;
    private final DataScopeOwnerValidator dataScopeOwnerValidator;
    private final CurrentSession currentSession;

    public UserDataScopeConfigurationService(UserQuery userQuery,
                                             UserAccountUpdater userAccountUpdater,
                                             DataScopeOwnerRegistrar dataScopeOwnerRegistrar,
                                             DataScopeOwnerValidator dataScopeOwnerValidator,
                                             CurrentSession currentSession) {
        this.userQuery = userQuery;
        this.userAccountUpdater = userAccountUpdater;
        this.dataScopeOwnerRegistrar = dataScopeOwnerRegistrar;
        this.dataScopeOwnerValidator = dataScopeOwnerValidator;
        this.currentSession = currentSession;
    }

    /**
     * 整批替换某个账号的用户级数据范围覆盖。
     *
     * @param command 配置入参；{@code dataScopeType} 为 {@code null} 表示清除覆盖
     * @throws BizException 当前会话无效（40100）；账号不存在（40400）；
     *                      账号是货主账号、类型与明细矛盾、明细里有不存在或未启用的货主（40000）
     */
    @Transactional
    public void configure(ConfigureUserDataScopeCommand command) {
        long operatorId = currentSession.currentUserId()
                // 鉴权过滤器已拦过未登录请求，走到这里取不到会话只能是账号在请求途中被删除或令牌已失效；
                // 此时不能继续：updated_by 会写成空值，事后无从定位这条范围是谁配的
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));

        UserProfile user = userQuery.findById(command.userId())
                .orElseThrow(() -> BizException.notFound("用户不存在"));
        if (user.summary().subjectType() == SubjectType.OWNER) {
            // 货主账号的范围由所属货主强制决定，不经配置也不可绕过（规格「货主账号强制隔离」）。
            // 允许写入只会产生一份永远不生效的配置，让管理员以为自己改了范围
            throw BizException.badRequest("货主账号的数据范围由所属货主固定，不能配置用户级覆盖");
        }

        DataScopeType type = command.dataScopeType();
        Set<Long> ownerIds = command.ownerIds() == null ? Set.of() : Set.copyOf(command.ownerIds());
        if (type == null) {
            if (!ownerIds.isEmpty()) {
                // 「清除覆盖」与「带明细」是自相矛盾的请求：按前者执行会静默丢弃明细，
                // 按后者执行则清除没发生。两种猜测都可能不是调用方想要的，因此拒绝
                throw BizException.badRequest("清除数据范围覆盖时不能提交货主明细");
            }
        } else {
            dataScopeOwnerValidator.requireConfigurable(type, ownerIds);
        }

        userAccountUpdater.updateDataScopeType(command.userId(), type, operatorId, Instant.now());
        // 清除覆盖与非 CUSTOM 都写空集合：明细表里「没有行」既表示没配过也表示已清空，
        // 两者的区别只存在于 sys_user.data_scope_type
        dataScopeOwnerRegistrar.replaceUserOwners(command.userId(),
                type != null && type.usesOwnerDetails() ? ownerIds : Set.of());
    }
}

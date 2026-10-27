package com.yunwms.auth.application.service;

import com.yunwms.auth.domain.model.DataScope;
import com.yunwms.auth.domain.model.DataScopeSetting;
import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.model.RoleDataScope;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.DataScopeQuery;
import com.yunwms.auth.domain.port.OwnerAssigneeQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * 解析当前请求的生效数据范围。
 *
 * <p>规则按优先级排列，与规格逐条对应：
 * <ol>
 *   <li><b>货主账号</b>：恒为「所属货主」一个值，忽略角色与用户配置。这条不经配置、不可绕过，
 *       所以放在最前面——先看配置再看主体类型，会让「货主账号配了 ALL 就真能看全部」成为可能；</li>
 *   <li><b>用户覆盖</b>：有覆盖就只按覆盖算，<b>不回退角色</b>。回退看起来更「友好」，
 *       但它让「我把这个人的范围改成空」这件事失效：管理员清不掉权限，只能改为给一个假货主；</li>
 *   <li><b>角色并集</b>：取启用中的角色，{@code ALL} 吞并其他取值，{@code ASSIGNED} 实时推导，
 *       {@code CUSTOM} 读明细且空明细不贡献；</li>
 *   <li><b>空集即无权</b>：没有启用角色、或并集为空，结果是 {@link DataScope.None}，不回退到「全部」。</li>
 * </ol>
 *
 * <p><b>不缓存</b>：角色停用、角色移除、用户覆盖清除都必须在下一个请求生效（规格要求「立即」）。
 * 请求内的重复调用由 {@code DataScopeContext} 缓存，两者分工明确——这里每次调用都问库。
 *
 * <p><b>查询条数固定</b>：「用户覆盖」最多两条（账号行 + 明细），「角色并集」最多四条
 * （角色关联 + 角色行 + {@code CUSTOM} 明细 + 负责人），与角色数量无关。
 */
@Service
public class DataScopeResolver {

    private final CurrentSession currentSession;
    private final DataScopeQuery dataScopeQuery;
    private final OwnerAssigneeQuery ownerAssigneeQuery;

    public DataScopeResolver(CurrentSession currentSession,
                             DataScopeQuery dataScopeQuery,
                             OwnerAssigneeQuery ownerAssigneeQuery) {
        this.currentSession = currentSession;
        this.dataScopeQuery = dataScopeQuery;
        this.ownerAssigneeQuery = ownerAssigneeQuery;
    }

    /**
     * 解析当前请求的生效数据范围。
     *
     * @return 生效范围，恒不为 {@code null}；没有登录上下文时为 {@link DataScope.None}
     */
    public DataScope resolve() {
        OptionalLong userId = currentSession.currentUserId();
        if (userId.isEmpty()) {
            // 无登录上下文（匿名请求、将来的系统任务）一律无权：一个「不知道是谁」的请求没有理由看得见货主
            return DataScope.none();
        }
        Optional<SubjectType> subjectType = currentSession.currentSubjectType();
        if (subjectType.isEmpty()) {
            // 会话里没有主体类型说明会话不完整（旧版本签发或写入被破坏）：
            // 猜 STAFF 会让货主账号有机会按角色解析，猜 OWNER 会产生一个指向空货主的范围，两边都不如无从
            return DataScope.none();
        }
        if (subjectType.get() == SubjectType.OWNER) {
            return ownerScope();
        }
        return staffScope(userId.getAsLong());
    }

    /**
     * 货主账号的范围：只能是它自己所属的那一个货主。
     *
     * @return 所属货主的单元素范围；会话里没有货主标识时为 {@link DataScope.None}
     */
    private DataScope ownerScope() {
        OptionalLong ownerId = currentSession.currentOwnerId();
        if (ownerId.isEmpty()) {
            // 货主账号不带所属货主说明会话不完整。此处绝不能滑到「按角色解析」——那正是规格禁止的绕过路径
            return DataScope.none();
        }
        return DataScope.restricted(Set.of(ownerId.getAsLong()));
    }

    /**
     * 内部员工的范围：用户覆盖优先，否则取启用角色并集。
     *
     * @param userId 用户标识
     * @return 生效范围；无覆盖且无启用角色时为 {@link DataScope.None}
     */
    private DataScope staffScope(long userId) {
        Optional<DataScopeSetting> override = dataScopeQuery.findUserSetting(userId);
        if (override.isPresent()) {
            DataScopeSetting setting = override.get();
            return expand(setting.type(), setting.ownerIds(), userId);
        }

        List<RoleDataScope> roles = dataScopeQuery.findActiveRoleScopes(userId);
        if (roles.isEmpty()) {
            // 没有角色（或全部停用）就是无权：规格「全部角色停用视为无权限」
            return DataScope.none();
        }

        // 先判 ALL：它吞并其他取值，一旦命中，后面的明细与负责人查询都是白做
        for (RoleDataScope role : roles) {
            if (role.type() == DataScopeType.ALL) {
                return DataScope.all();
            }
        }

        Map<Long, List<Long>> customOwnerIds = customOwnerIds(roles);
        List<DataScope> scopes = new ArrayList<>(roles.size());
        // 负责人只查一次：同一请求里有多个 ASSIGNED 角色时，答案一定相同，重复查库换不来任何正确性
        Set<Long> assignedOwnerIds = null;
        for (RoleDataScope role : roles) {
            switch (role.type()) {
                case ASSIGNED -> {
                    if (assignedOwnerIds == null) {
                        assignedOwnerIds = ownerAssigneeQuery.findAssignedOwnerIds(userId);
                    }
                    scopes.add(DataScope.restricted(assignedOwnerIds));
                }
                case CUSTOM -> scopes.add(DataScope.restricted(
                        customOwnerIds.getOrDefault(role.roleId(), List.of())));
                case ALL -> throw new IllegalStateException("ALL 应在前面的预判中返回");
            }
        }
        return DataScope.union(scopes);
    }

    /**
     * 把一份配置展开成生效范围。
     *
     * @param type          数据范围类型
     * @param customOwnerIds {@code CUSTOM} 时的明细
     * @param userId        用户标识，{@code ASSIGNED} 时用来推导负责的货主
     * @return 展开后的范围，恒不为 {@code null}
     */
    private DataScope expand(DataScopeType type, Set<Long> customOwnerIds, long userId) {
        return switch (type) {
            case ALL -> DataScope.all();
            case ASSIGNED -> DataScope.restricted(ownerAssigneeQuery.findAssignedOwnerIds(userId));
            case CUSTOM -> DataScope.restricted(customOwnerIds);
        };
    }

    /**
     * 批量取本批角色里的 {@code CUSTOM} 明细。
     *
     * <p>先判有没有 {@code CUSTOM} 角色：全是 {@code ALL}/{@code ASSIGNED} 时不该为一次空查询付往返。
     *
     * @param roles 启用中的角色
     * @return 角色标识到明细的映射；没有 {@code CUSTOM} 角色时为空映射
     */
    private Map<Long, List<Long>> customOwnerIds(List<RoleDataScope> roles) {
        List<Long> customRoleIds = roles.stream()
                .filter(role -> role.type().usesOwnerDetails())
                .map(RoleDataScope::roleId)
                .toList();
        if (customRoleIds.isEmpty()) {
            return new HashMap<>();
        }
        return dataScopeQuery.findCustomOwnerIds(customRoleIds);
    }
}

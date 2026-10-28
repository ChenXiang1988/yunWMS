package com.yunwms.auth.application.audit;

import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.model.NewRole;
import com.yunwms.auth.domain.model.RoleStatus;
import com.yunwms.auth.domain.model.RoleSummary;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色审计快照的构造处：把角色投影成写进 {@code sys_audit_log.before_value / after_value} 的字段集。
 *
 * <p><b>白名单，不是「{@code Map} 整个角色」。</b>理由与 {@link UserAuditSnapshots} 相同：
 * 审计表只追加、不可修改，跟着实体走的那天，新加的敏感字段会在无人察觉时开始进审计表。
 *
 * <p><b>{@code menuIds} 排序后写入。</b>授权是集合语义，同一批菜单以不同顺序提交不该产生
 * 「有变更」的假象——编辑用例的「一个字段都没变就不留痕」判定要能得出正确结论，
 * 而事后比对两份快照的人也应当看到同样的答案。
 *
 * <p><b>取值为 {@code null} 的字段保留键并写 {@code null}，取值为标量或枚举名。</b>两条规则的理由同
 * {@link UserAuditSnapshots}：键缺失与空白是两段不同的历史，领域对象直接塞进去会让键集随模型变形。
 *
 * <p><b>标识按字符串写</b>（{@code menuIds} 的每个元素也是）：与接口层「标识一律以字符串传输」同口径，
 * 一条日志里同一个标识不该有时是数字、有时是字符串。
 *
 * <p><b>建档、编辑、启停、配置范围共用同一份快照形状</b>，于是一条角色的日志可以按时间竖着读。
 */
public final class RoleAuditSnapshots {

    private RoleAuditSnapshots() {
    }

    /**
     * 建档用的变更后快照。
     *
     * <p>{@code builtin} 与 {@code status} 取建档期固定值（见 {@link NewRole}）：它们不是管理员的可选项，
     * 由写入口显式落库。{@code dataScopeType} 取本次落库的类型，含省略时补的默认值。
     *
     * @param role 待落库的角色
     * @return 快照，必定包含全部白名单键
     */
    public static Map<String, Object> ofCreated(NewRole role) {
        return snapshot(role.code(), role.name(), NewRole.INITIAL_BUILTIN, NewRole.INITIAL_STATUS,
                role.dataScopeType(), role.remark(), role.menuIds());
    }

    /**
     * 已落库角色在给定状态下的快照。
     *
     * <p>既是编辑、启停、删除的变更前值，也是「配置数据范围」的变更后值——那条用例改的正是
     * {@code dataScopeType}，把入参的类型传进来就得到变更后的那一份。
     *
     * @param summary       角色摘要，提供编码、名称、是否内置、状态与备注
     * @param dataScopeType 数据范围类型
     * @param menuIds       已勾选的菜单与权限点标识，顺序不参与判定
     * @return 快照，必定包含全部白名单键
     */
    public static Map<String, Object> of(RoleSummary summary, DataScopeType dataScopeType,
                                         Collection<Long> menuIds) {
        return snapshot(summary.code(), summary.name(), summary.builtin(), summary.status(), dataScopeType,
                summary.remark(), menuIds);
    }

    /**
     * 编辑成功的变更后快照：只有名称、备注与授权会变，其余字段取变更前的既成事实。
     *
     * <p>取「本次提交并落库的值」而不是再查一次库，理由同 {@link UserAuditSnapshots#ofUpdated}：
     * 回查只可能读到别人刚改的值。
     *
     * @param summary       被编辑的角色，提供编码、是否内置、状态与数据范围类型
     * @param dataScopeType 数据范围类型，编辑不改它，取变更前的值
     * @param name          本次提交并落库的名称
     * @param remark        本次提交并落库的备注，{@code null} 表示清空
     * @param menuIds       本次提交并落库的菜单与权限点标识
     * @return 快照，必定包含全部白名单键
     */
    public static Map<String, Object> ofUpdated(RoleSummary summary, DataScopeType dataScopeType,
                                                String name, String remark, Collection<Long> menuIds) {
        return snapshot(summary.code(), name, summary.builtin(), summary.status(), dataScopeType, remark, menuIds);
    }

    /**
     * 启停的变更后快照：只换状态，其余字段取流转前的既成事实。
     *
     * @param summary       流转前的角色
     * @param dataScopeType 数据范围类型
     * @param status        本次落库的目标状态
     * @param menuIds       已勾选的菜单与权限点标识
     * @return 快照，必定包含全部白名单键
     */
    public static Map<String, Object> ofStatusChanged(RoleSummary summary, DataScopeType dataScopeType,
                                                      RoleStatus status, Collection<Long> menuIds) {
        return snapshot(summary.code(), summary.name(), summary.builtin(), status, dataScopeType,
                summary.remark(), menuIds);
    }

    /**
     * 按固定的键顺序装配快照。
     *
     * <p>键列表只出现在这里：四个入口若各拼一份，加字段时必然只改到其中几处，
     * 而「同一条角色的两条日志键集不同」在读日志时表现为某个字段忽然消失，很难看出是代码问题。
     *
     * @param code          角色编码
     * @param name          角色名称
     * @param builtin       是否内置角色
     * @param status        角色状态
     * @param dataScopeType 数据范围类型
     * @param remark        备注，可为 {@code null}
     * @param menuIds       已勾选的菜单与权限点标识，{@code null} 按空集合处理
     * @return 快照
     */
    private static Map<String, Object> snapshot(String code, String name, boolean builtin, RoleStatus status,
                                                DataScopeType dataScopeType, String remark,
                                                Collection<Long> menuIds) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", code);
        snapshot.put("name", name);
        snapshot.put("builtin", builtin);
        // 状态与范围类型存枚举名：与库列、接口契约同一口径，存序号会在枚举顺序调整后静默错位
        snapshot.put("status", status.name());
        snapshot.put("dataScopeType", dataScopeType.name());
        snapshot.put("remark", remark);
        // 先按数值排序再转字符串：字符串排序会把 10 排在 9 前面，两份「同一批授权」的快照就会不等
        snapshot.put("menuIds", menuIds == null
                ? List.of()
                : menuIds.stream().distinct().sorted().map(String::valueOf).toList());
        return snapshot;
    }
}

package com.yunwms.auth.application.audit;

import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.model.NewUserAccount;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserProfile;
import com.yunwms.auth.domain.model.UserStatus;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户审计快照的构造处：把账号投影成写进 {@code sys_audit_log.before_value / after_value} 的字段集。
 *
 * <p><b>白名单，不是「{@code Map} 整个账号」。</b>逐个字段列举，新增字段时快照不会自动带上它——
 * 这正是要的效果：审计表只追加、不可修改，一旦跟着实体走，将来加进来的敏感字段会在无人察觉的情况下
 * 开始进审计表，发现时已经删不掉了。要追溯一个新字段，是显式往 {@link #snapshot} 里加一行，
 * 并在测试里同步改一份键集断言（那份断言会立刻红给你看）。
 *
 * <p><b>密码相关的一切都不进来。</b>不写哈希、不写明文，也不写长度、首字符、是否含数字这类间接信息：
 * 审计表长期保留，而任何能缩小口令搜索空间的信息一旦落库就撤不回来。重置密码那条日志因此
 * 两个快照都是 {@code null}，只说「谁重置了谁的密码」。
 *
 * <p><b>取值为 {@code null} 的字段保留键并写 {@code null}。</b>{@code "wecomUserId": null} 表示
 * 「当时没有这个值」，键缺失表示「这个字段当时还不存在」，两者在事后追溯里含义不同。
 *
 * <p><b>只放标量与枚举名，不放领域对象。</b>{@link UserStatus}、{@link SubjectType} 直接塞进去时，
 * Jackson 按 Java 结构展开，快照的键集就不再固定，读取方也开始依赖领域模型的内部形状。
 *
 * <p><b>标识按字符串写。</b>与接口层「标识一律以字符串传输」同口径，也与
 * {@code wms-master} 的开号快照一致：一条日志里同一个标识不该有时是数字、有时是字符串。
 *
 * <p><b>建档、编辑、状态流转、范围配置共用同一份快照形状</b>，于是一条账号的日志可以按时间竖着读：
 * 每一行都是同一种键集，差异只在值。
 *
 * <p><b>「分配角色」是唯一的例外</b>，它有自己的形状（{@link #ofRoleAssignment}）。那份快照回答的是
 * 「改成了哪些角色」，而本类的白名单里没有角色集合；把角色集合塞进白名单会让上面四处各多一次查询。
 */
public final class UserAuditSnapshots {

    private UserAuditSnapshots() {
    }

    /**
     * 建档用的变更后快照。
     *
     * <p>{@code status} 与 {@code mustChangePassword} 取建档期固定值（见 {@link NewUserAccount}）：
     * 它们由写入口落库，不由入参决定。{@code dataScopeType} 恒为 {@code null}——新建账号没有
     * 用户级覆盖，范围继承角色默认。
     *
     * @param account 待落库的账号；其 {@code passwordHash} 刻意不被读取
     * @return 快照，必定包含全部白名单键
     */
    public static Map<String, Object> ofCreated(NewUserAccount account) {
        return snapshot(account.loginName(), account.displayName(), account.subjectType(), account.ownerId(),
                account.email(), account.wecomUserId(), NewUserAccount.INITIAL_STATUS, null,
                NewUserAccount.INITIAL_MUST_CHANGE_PASSWORD);
    }

    /**
     * 已落库账号的当前快照：既是编辑、状态流转、权限变更的变更前值。
     *
     * @param profile 账号详情
     * @return 快照，必定包含全部白名单键
     */
    public static Map<String, Object> of(UserProfile profile) {
        return snapshot(profile.summary().loginName(), profile.summary().displayName(),
                profile.summary().subjectType(), profile.summary().ownerId(), profile.email(),
                profile.wecomUserId(), profile.summary().status(), profile.dataScopeType(),
                profile.mustChangePassword());
    }

    /**
     * 编辑成功的变更后快照：只有展示名、邮箱与企业微信 userid 会变，其余字段取变更前的既成事实。
     *
     * <p>取「本次提交并落库的值」而不是再查一次库：落库的就是这份值，回查只会多一次往返，
     * 并且允许「查到的是别人改后的值」这种与本次写入无关的状态混进本次快照。
     *
     * @param profile     被编辑的账号，提供其余字段
     * @param displayName 本次提交并落库的展示名
     * @param email       本次提交并落库的邮箱，{@code null} 表示清空
     * @param wecomUserId 本次提交并落库的企业微信 userid，{@code null} 表示清空
     * @return 快照，必定包含全部白名单键
     */
    public static Map<String, Object> ofUpdated(UserProfile profile, String displayName, String email,
                                                String wecomUserId) {
        return snapshot(profile.summary().loginName(), displayName, profile.summary().subjectType(),
                profile.summary().ownerId(), email, wecomUserId, profile.summary().status(),
                profile.dataScopeType(), profile.mustChangePassword());
    }

    /**
     * 状态流转的变更后快照：只换状态，其余字段取流转前的既成事实。
     *
     * @param profile 流转前的账号
     * @param status  本次落库的目标状态
     * @return 快照，必定包含全部白名单键
     */
    public static Map<String, Object> ofStatusChanged(UserProfile profile, UserStatus status) {
        return snapshot(profile.summary().loginName(), profile.summary().displayName(),
                profile.summary().subjectType(), profile.summary().ownerId(), profile.email(),
                profile.wecomUserId(), status, profile.dataScopeType(), profile.mustChangePassword());
    }

    /**
     * 用户级数据范围配置的变更后快照：只换覆盖类型，明细不在白名单内（它是一份名单，
     * 不是「这个账号是什么」的一部分）。
     *
     * @param profile 配置前的账号
     * @param type    本次落库的覆盖类型；{@code null} 表示清除了覆盖、回落到角色默认
     * @return 快照，必定包含全部白名单键
     */
    public static Map<String, Object> ofDataScopeChanged(UserProfile profile, DataScopeType type) {
        return snapshot(profile.summary().loginName(), profile.summary().displayName(),
                profile.summary().subjectType(), profile.summary().ownerId(), profile.email(),
                profile.wecomUserId(), profile.summary().status(),
                type == null ? null : type.name(), profile.mustChangePassword());
    }

    /**
     * 「分配角色」专用的快照：该账号在某一刻挂着的角色编码集合。
     *
     * <p><b>为什么不复用账号快照。</b>角色集合不在 {@link #of} 的白名单里（它是一条条关联行，
     * 不是账号自身的字段），两侧都套账号快照时 {@code before} 与 {@code after} 逐字相同，
     * 日志只能回答「谁在什么时候动了某人的角色」，答不出「改成了哪些角色」——而对权限变更来说，
     * 后者才是这条日志的全部价值。
     *
     * <p><b>为什么不干脆把 {@code roleCodes} 加进账号快照。</b>那份快照被建档、编辑、状态流转、
     * 范围配置共用，加一个键等于给那四处各加一次角色查询，而它们都不需要这个答案。
     *
     * <p><b>编码排序后写入，空集合写 {@code []}。</b>角色是集合语义，同一批角色以不同顺序提交
     * 不该产生「有变更」的假象，事后比对两份快照的人也应当看到同样的答案（与
     * {@link RoleAuditSnapshots} 处理 {@code menuIds} 同口径）。而「一个角色都没有」是一个明确的
     * 状态，写成 {@code null} 会让读取方分不清它与「这份快照里没有角色这个概念」——键缺失才是后者。
     *
     * @param roleCodes 变更前或变更后的角色编码，顺序不参与判定；调用方保证非 {@code null}
     * @return 快照，键集恒为 {@code roleCodes}
     */
    public static Map<String, Object> ofRoleAssignment(Collection<String> roleCodes) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        // 先 distinct 再排序：集合语义下同一个编码出现两次不该落成两个元素
        snapshot.put("roleCodes", roleCodes.stream().distinct().sorted().toList());
        return snapshot;
    }

    /**
     * 按固定的键顺序装配快照。
     *
     * <p>键列表只出现在这里：五个入口若各拼一份，加字段时必然只改到其中几处，
     * 而「同一条账号的两条日志键集不同」在读日志时表现为某个字段忽然消失，很难看出是代码问题。
     *
     * @param loginName         登录名
     * @param displayName       展示名
     * @param subjectType       主体类型
     * @param ownerId           所属货主标识，内部员工为 {@code null}
     * @param email             邮箱，可为 {@code null}
     * @param wecomUserId       企业微信 userid，可为 {@code null}
     * @param status            账号状态
     * @param dataScopeType     用户级数据范围覆盖类型编码；{@code null} 表示继承角色默认
     * @param mustChangePassword 是否需先改密
     * @return 快照
     */
    private static Map<String, Object> snapshot(String loginName, String displayName, SubjectType subjectType,
                                                Long ownerId, String email, String wecomUserId, UserStatus status,
                                                String dataScopeType, boolean mustChangePassword) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("loginName", loginName);
        snapshot.put("displayName", displayName);
        // 主体类型与状态存枚举名：与库列、接口契约同一口径，存序号会在枚举顺序调整后静默错位
        snapshot.put("subjectType", subjectType.name());
        snapshot.put("ownerId", ownerId == null ? null : Long.toString(ownerId));
        snapshot.put("email", email);
        snapshot.put("wecomUserId", wecomUserId);
        snapshot.put("status", status.name());
        snapshot.put("dataScopeType", dataScopeType);
        snapshot.put("mustChangePassword", mustChangePassword);
        return snapshot;
    }
}

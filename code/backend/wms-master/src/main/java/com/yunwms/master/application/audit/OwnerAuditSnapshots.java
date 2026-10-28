package com.yunwms.master.application.audit;

import com.yunwms.master.domain.model.NewOwner;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerContact;
import com.yunwms.master.domain.model.OwnerProfile;
import com.yunwms.master.domain.model.OwnerStatus;
import com.yunwms.master.domain.model.SkuPrefix;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 货主审计快照的构造处：把领域对象投影成写进 {@code sys_audit_log.before_value / after_value} 的字段集。
 *
 * <p><b>白名单，不是「{@code Map} 整个实体」。</b>逐个字段列举，新增字段时快照不会自动带上它——
 * 这正是要的效果：审计会把当时的数据副本长期保存，一旦跟着实体走，将来某天加进来的敏感字段
 * （证件号、银行账号、联系电话）会在无人察觉的情况下开始进审计表，而审计表只追加、不可修改，
 * 发现时已经删不掉了。要追溯一个新字段，是显式往 {@link #snapshot} 里加一行，并在这里留一句为什么。
 *
 * <p><b>取值为 {@code null} 的字段保留键并写 {@code null}。</b>{@code "remark": null} 表示「当时没有备注」，
 * 键缺失表示「这个字段当时还不存在」，两者在事后追溯里含义不同：前者是一次被记录下来的空白，
 * 后者是一段没有被记录的字段历史。{@code jsonb} 能表达这个区别，先用 {@code Map} 把它弄丢就没有第二次机会。
 *
 * <p><b>只放标量，不放领域对象。</b>值一律取 {@code String} 或 {@code name()}：{@link SkuPrefix}、
 * {@link OwnerStatus} 这类值对象/枚举直接塞进去时，Jackson 按记录与枚举的 Java 结构展开
 * （{@code {"value":"AMZ2"}}），快照的键集就不再固定，读取方也开始依赖领域模型的内部形状。
 * 将来若要记时间字段，取 {@code toString()}（ISO-8601 文本）而不是 {@code Instant}：
 * 前者在任何序列化配置下都稳定，后者取决于 {@code ObjectMapper} 的日期开关。
 *
 * <p><b>建档、编辑、删除、流转共用同一份快照形状</b>（开号除外，它记的是账号），
 * 于是一条货主的日志可以按时间竖着读：每一行都是同一种键集，差异只在值。
 */
public final class OwnerAuditSnapshots {

    private OwnerAuditSnapshots() {
    }

    /**
     * 建档用的变更后快照：编码与初始状态在建档过程中才产生，因此由调用方传入。
     *
     * @param owner  待落库的货主
     * @param code   由取号端口生成的系统编码
     * @param status 落库的初始状态
     * @return 快照，必定包含全部白名单键
     */
    public static Map<String, Object> of(NewOwner owner, String code, OwnerStatus status) {
        return snapshot(code, owner.skuPrefix(), status, OwnerProfile.of(owner));
    }

    /**
     * 已落库货主的当前快照：既是编辑、删除的变更前值，也是状态流转的变更前值。
     *
     * @param owner 已落库的货主
     * @return 快照，必定包含全部白名单键
     */
    public static Map<String, Object> of(Owner owner) {
        return snapshot(owner.code(), owner.skuPrefix(), owner.status(), OwnerProfile.of(owner));
    }

    /**
     * 编辑成功的变更后快照：编码、SKU 前缀与状态不变，档案部分取本次提交并落库的值。
     *
     * <p>取 {@code profile} 而不是「再查一次库」：落库的就是这份值，回查只会多一次往返，
     * 并且允许「查到的是别人改后的值」这种与本次写入无关的状态混进本次快照。
     *
     * @param owner   被编辑的货主，提供编码、前缀与状态
     * @param profile 本次提交并落库的档案属性
     * @return 快照，必定包含全部白名单键
     */
    public static Map<String, Object> ofUpdated(Owner owner, OwnerProfile profile) {
        return snapshot(owner.code(), owner.skuPrefix(), owner.status(), profile);
    }

    /**
     * 状态流转的变更后快照：只换状态，其余字段取流转前的既成事实。
     *
     * @param owner  流转前的货主
     * @param status 本次落库的目标状态
     * @return 快照，必定包含全部白名单键
     */
    public static Map<String, Object> ofStatusChanged(Owner owner, OwnerStatus status) {
        return snapshot(owner.code(), owner.skuPrefix(), status, OwnerProfile.of(owner));
    }

    /**
     * 开号用的变更后快照：这次变更的对象是账号，不是货主档案。
     *
     * <p>只有两个键：登录名与账号标识。密码不在其中——它连哈希都不会进审计表，审计是只追加的，
     * 任何凭据一旦写进去就无法撤回。标识按字符串记录，与接口层「标识一律以字符串传输」同口径。
     *
     * @param loginName 新账号的登录名，等于货主系统编码
     * @param accountId 新账号的标识
     * @return 快照，键为 {@code loginName} 与 {@code accountId}
     */
    public static Map<String, Object> ofAccountOpened(String loginName, long accountId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("loginName", loginName);
        snapshot.put("accountId", String.valueOf(accountId));
        return snapshot;
    }

    /**
     * 联系人的当前快照：既是新增的变更后值，也是编辑、删除的变更前值。
     *
     * <p><b>白名单里包含 {@code phone} 与 {@code email}，这与本类注释里「敏感字段不进快照」的告警存在张力，
     * 是一次明确的取舍：</b>联系人的全部意义就是「出事时找谁」，不记电话的审计等于一次
     * 「有记录、但看不出改了什么」的留痕——事后既追不到上一版号码，也说不清这次到底动的是电话还是备注，
     * 那条日志就只剩「有人动过」这一点信息。类注释里的告警真正针对的是「跟着实体走、无人察觉地自动进表」，
     * 而这里是把六个键逐个列举，并把决定与代价留在此处，正好是那条告警要求的做法。
     *
     * <p><b>边界：</b>进入快照的是 B 端客户在商务往来中主动提供的工作联系方式，不是个人身份类数据；
     * 号码写错可以在下一次往来中更正，而身份类字段一旦进了只追加的审计表就无法撤回。
     * 若将来联系人上出现证件号、银行账号、影像件这类字段，仍按本类的白名单逐个评估，
     * 不因为「电话已经记了」而顺延放行。
     *
     * <p>{@code isPrimary} 记 {@link Boolean} 而不是字符串：它是一个布尔事实，事后按 {@code true} 检索
     * 「这个客户什么时候换过主联系人」是这一位唯一的用途。
     *
     * <p>不含联系人的标识与所属货主：标识不进白名单的理由与货主快照一致（它由 {@code bizId} 之外的
     * 上下文承担），归属则由审计的 {@code bizId}（货主编码）给出。
     *
     * @param contact 已落库或即将落库的联系人
     * @return 快照，键为 {@code name} / {@code roleType} / {@code phone} / {@code email} /
     *         {@code isPrimary} / {@code remark}；{@code null} 值保留键
     */
    public static Map<String, Object> ofContact(OwnerContact contact) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", contact.name());
        snapshot.put("roleType", contact.roleType());
        snapshot.put("phone", contact.phone());
        snapshot.put("email", contact.email());
        snapshot.put("isPrimary", contact.isPrimary());
        snapshot.put("remark", contact.remark());
        return snapshot;
    }

    /**
     * 按固定的键顺序装配快照。
     *
     * <p>键列表只出现在这里：五个入口若各拼一份，加字段时必然只改到其中几处，
     * 而「同一条货主的两条日志键集不同」在读日志时表现为某个字段忽然消失，很难看出是代码问题。
     *
     * @param code       系统编码
     * @param skuPrefix  SKU 前缀
     * @param status     状态
     * @param profile    档案属性，提供名称、类型、结算主体、地址与备注
     * @return 快照
     */
    private static Map<String, Object> snapshot(String code, SkuPrefix skuPrefix, OwnerStatus status,
                                                OwnerProfile profile) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", code);
        snapshot.put("name", profile.name());
        snapshot.put("nameEn", profile.nameEn());
        snapshot.put("shortName", profile.shortName());
        snapshot.put("externalCode", profile.externalCode());
        snapshot.put("skuPrefix", skuPrefix.value());
        snapshot.put("ownerType", profile.ownerType());
        snapshot.put("settlementEntity", profile.settlementEntity());
        // 状态存枚举名：与库列、接口契约同一口径，存序号会在枚举顺序调整后静默错位
        snapshot.put("status", status.name());
        snapshot.put("country", profile.address().country());
        snapshot.put("province", profile.address().province());
        snapshot.put("city", profile.address().city());
        snapshot.put("district", profile.address().district());
        snapshot.put("postalCode", profile.address().postalCode());
        snapshot.put("addressLine", profile.address().addressLine());
        snapshot.put("remark", profile.remark());
        return snapshot;
    }
}

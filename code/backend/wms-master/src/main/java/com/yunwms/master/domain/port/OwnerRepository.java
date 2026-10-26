package com.yunwms.master.domain.port;

import com.yunwms.master.domain.model.NewOwner;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerProfile;
import com.yunwms.master.domain.model.OwnerStatus;

import java.util.List;
import java.util.Optional;

/**
 * 货主的读写入口。
 *
 * <p><b>为什么读与写放在同一个端口：</b>{@code wms-auth} 把「管理员建号」与「登录链路读取」拆成两个端口，
 * 是因为那两条链路的权限要求、失败语义与调用方都不同，合并会让任何拿到仓储的代码都能绕过用例建号。
 * 货主这边不满足这个前提：建档、列表、详情都由同一个管理端用例族消费，权限点分别是
 * {@code system:owner:create} 与 {@code system:owner:list}，读侧与写侧没有需要靠类型隔开的分歧。
 * 等状态流转（3.2）带来「只有特定流转能写」的约束时，再按那时的实际分歧拆。
 *
 * <p><b>唯一性判定只是提前提示，不是判重依据：</b>{@link #existsBySkuPrefix} 与
 * {@link #existsByExternalCode} 在同一瞬间的两次调用之间可能失效——先查再插在并发下拦不住重复。
 * 真正的判重由 {@code ux_owner_sku_prefix} 与 {@code ux_owner_external_code} 给出，
 * 这两个方法只用来在提交前给出「指出占用方编码」这类友好提示。
 *
 * <p>本端口不翻译数据库约束违反：哪条约束被违反只有数据库知道，而错误码属于协议层。
 */
public interface OwnerRepository {

    /**
     * 写入新货主。
     *
     * <p>编码由调用方先经 {@link OwnerCodeGenerator} 取得后传入，而不是在本方法内部取号：
     * 取号是一次独立提交的副作用（{@link OwnerCodeGenerator} 的契约），把两件事写在一行调用里，
     * 「号已经用掉了但货主没建成」这件事在代码里就看不见了。
     *
     * <p><b>初始状态由调用方给出，不在本方法里写死：</b>建档有「保存草稿」与「直接提交」两条入口
     * （3.1b），前者落 {@link OwnerStatus#DRAFT}、后者落 {@link OwnerStatus#ACTIVE}；
     * 落哪个状态是用例的判断，写进端口就成了「所有入口都从同一个状态开始」。
     * 若靠列默认值 {@code 'DRAFT'} 兜底，另一条入口必须补一次更新，而那次更新在审计里
     * 看起来像一次真实的状态流转。
     *
     * @param owner         待落库的货主，属性已由 {@link NewOwner} 校验
     * @param code          系统编码，由 {@link OwnerCodeGenerator} 取得
     * @param initialStatus 初始状态，由建档用例决定；仅 {@code DRAFT} 与 {@code ACTIVE} 是当前入口会传的值，
     *                      本方法不额外限制取值，状态机（3.2）负责流转约束
     * @param operatorId    操作人标识，写入 {@code created_by} 与 {@code updated_by}
     * @return 数据库生成的货主标识
     * @throws IllegalArgumentException 初始状态为 {@code null}
     * @throws IllegalStateException    插入未返回主键，说明写入与主键回填不一致
     * @throws RuntimeException         违反库约束（编码或前缀唯一、外键、CHECK）时由实现原样上抛，
     *                                  由调用方翻译成业务错误码
     */
    long insert(NewOwner owner, String code, OwnerStatus initialStatus, long operatorId);

    /**
     * 覆盖货主档案里可编辑的部分，写入 {@code updated_by} 与 {@code updated_at}。
     *
     * <p><b>为什么只收 {@link OwnerProfile} 而不是整个 {@code Owner}：</b>可编辑范围由类型决定。
     * 编码、SKU 前缀与状态不在 {@link OwnerProfile} 里，调用方因此没有机会把这三个值传到写语句上——
     * 靠「记得别写那三列」的注释约束不住下一次顺手加字段。
     *
     * <p><b>没有「货主不存在」的返回值：</b>调用方先用 {@link #findById} 判过，命中零行只可能是
     * 这一行在两次调用之间被软删除。那不是一个需要纠正的失败，也不该被当成并发冲突；
     * 影响行数因此不作为成功与否的判据（见 {@code docs/coding.md}「持久化与副作用」）。
     *
     * @param id         货主标识，调用方保证该货主存在
     * @param profile    覆盖后的档案属性，已由 {@link OwnerProfile} 校验
     * @param operatorId 操作人标识，写入 {@code updated_by}
     * @throws RuntimeException 违反库约束（外部码唯一、CHECK、外键）时由实现原样上抛，
     *                          由调用方翻译成业务错误码
     */
    void update(long id, OwnerProfile profile, long operatorId);

    /**
     * 按标识读取货主。
     *
     * <p>已逻辑删除的货主与「不存在」同口径：管理端看不到它，接口因此必须返回同一个结果。
     *
     * @param id 货主标识
     * @return 货主；不存在或已删除时为空
     * @throws IllegalArgumentException 库里的状态编码未知，说明数据已损坏
     */
    Optional<Owner> findById(long id);

    /**
     * 按筛选条件取一页货主，按标识升序。
     *
     * <p>排序只用标识：它是唯一的，翻页不会漏行或重复行；按创建时间排需要再补一个唯一列兜底，
     * 而同一毫秒建的两个货主谁先谁后对使用者没有意义。
     *
     * @param status    状态筛选；{@code null} 表示不限
     * @param ownerType 客户类型筛选，取 {@code OWNER_TYPE} 字典的 value；{@code null} 表示不限
     * @param keyword   按编码、名称或外部码模糊匹配的关键字；{@code null} 表示不筛（空串须由调用方归一为 {@code null}）
     * @param limit     本页最多返回多少行
     * @param offset    跳过多少行
     * @return 本页货主；没有数据时为空列表
     */
    List<Owner> findPage(OwnerStatus status, String ownerType, String keyword, int limit, long offset);

    /**
     * 统计满足筛选条件的货主数，筛选口径与 {@link #findPage} 完全一致。
     *
     * @param status    状态筛选；{@code null} 表示不限
     * @param ownerType 客户类型筛选；{@code null} 表示不限
     * @param keyword   按编码、名称或外部码模糊匹配的关键字；{@code null} 表示不筛
     * @return 货主总数
     */
    long count(OwnerStatus status, String ownerType, String keyword);

    /**
     * 判断 SKU 前缀是否已被占用。
     *
     * <p>口径是<b>全表</b>，含已逻辑删除的货主：{@code ux_owner_sku_prefix} 是全表唯一索引
     * （不像外部码那样带 {@code deleted_at} 条件）。把已删除的排除在外会让界面说「这个前缀可以用」，
     * 而保存时撞上唯一索引——用户看到的是一句无法自查的数据库错误。
     *
     * @param skuPrefix SKU 前缀，调用方保证已通过 {@code SkuPrefix} 校验
     * @return 已被占用返回 {@code true}
     */
    boolean existsBySkuPrefix(String skuPrefix);

    /**
     * 判断外部码是否已被占用。
     *
     * <p>口径与 {@code ux_owner_external_code} 一致：只数未删除的货主。软删除之后外部码可以被
     * 新货主使用，这是建表时就定下的规则。
     *
     * @param externalCode 外部系统编码，可为 {@code null}；为 {@code null} 时本方法返回 {@code false}
     *                     （外部码本就允许为空，空与空不算冲突）
     * @return 已被占用返回 {@code true}
     */
    boolean existsByExternalCode(String externalCode);

    /**
     * 按 SKU 前缀反查占用方的系统编码，用于在冲突提示里指出「被谁占着」。
     *
     * <p><b>口径与 {@link #existsBySkuPrefix} 完全一致，同样是全表、刻意不带 {@code deleted_at} 条件：</b>
     * {@code ux_owner_sku_prefix} 是全表唯一索引，已软删除的货主仍占着前缀。若这里按「未删除」反查，
     * 就会出现「数据库说前缀被占用，反查却说没人占」——那时提示只能退回原文案，
     * 用户看到的是一句查不到占用方的拒绝，而那条已删除的货主在管理界面上根本不可见。
     *
     * <p><b>它不是判重手段：</b>调用时机是「插入已经撞上唯一索引之后」，只为把提示写清楚；
     * 判重仍然只有数据库能做（见本接口的类注释）。
     *
     * @param skuPrefix SKU 前缀，调用方保证已通过 {@code SkuPrefix} 校验
     * @return 占用方的系统编码；前缀未被占用时为空
     */
    Optional<String> findCodeBySkuPrefix(String skuPrefix);
}

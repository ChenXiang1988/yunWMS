package com.yunwms.master.domain.port;

import com.yunwms.master.domain.model.OwnerContact;
import com.yunwms.master.domain.model.OwnerContactDraft;

import java.util.List;
import java.util.Optional;

/**
 * 货主联系人的读写入口。
 *
 * <p><b>为什么与 {@link OwnerRepository} 分开成两个端口：</b>联系人是货主的子资源，但它有自己的
 * 权限点（{@code system:owner:contact:edit}，见 {@code V040}）、写入频率与并发口径（主联系人唯一）。
 * 塞进 {@link OwnerRepository} 会让「改档案」与「改联系人」共用一个入口，而这两类操作的用例、
 * 审计粒度与失败语义都不同：档案编辑覆盖一批字段并整条比较差异，联系人编辑只动一行、
 * 还会连带清掉另一行的主标记。按子资源拆端口，权限点与用例的边界才能落在类型上，
 * 而不是靠每个方法自己记得。
 *
 * <p><b>为什么读与写仍放在同一个端口：</b>与 {@link OwnerRepository} 同理由——联系人的列表、
 * 详情、编辑、删除由同一族管理端用例消费，读侧与写侧没有需要靠类型隔开的分歧（没有「管理员建号
 * 与登录链路读」那种权限与失败语义的差异）。
 *
 * <p><b>主联系人的唯一性由数据库保证，本端口不判：</b>{@code ux_owner_contact_primary}
 * （{@code owner_id} 上、{@code WHERE is_primary AND deleted_at IS NULL} 的部分唯一索引）
 * 是唯一权威。{@link #insert} / {@link #update} <b>都不隐式清掉旧的主联系人</b>：
 * 「设新主之前先清旧主」由用例按固定顺序调一次 {@link #clearPrimary} 再写，
 * 规则因此只有一处落点。隐式清除看似贴心，实际会把「只改了一行电话」的编辑顺带改成
 * 「另一行的主标记也变了」，而审计里只有一条记录、看不出这层连带修改。
 *
 * <p><b>为什么没有 {@code findByIdForUpdate}（货主端口有）：</b>货主的状态流转是
 * 「读当前状态 → 判流转表 → 写回」，两次调用之间必须锁住那一行，否则两个并发的流转各自基于旧状态
 * 判定。联系人没有状态机：切换主联系人是「先清旧主 → 再写新主」，并发下两笔请求若都越过了清除步骤，
 * 后写的一笔会撞上 {@code ux_owner_contact_primary}，那是一次能被翻译成业务错误的正常失败；
 * 加行锁只会让并发的联系人编辑无谓串行化，而不减少任何一类冲突。
 *
 * <p><b>为什么不接分页与排序参数：</b>单个货主的联系人是个位数到几十条，
 * {@link #findByOwnerId} 一次取全量，排序固定 {@code id} 升序（唯一且稳定，翻页不存在的场景下
 * 也不需要为「同一毫秒建的两条谁在前」定规则）。加 {@code limit}/{@code offset} 会让调用方
 * 以为「不传就取全部」，而真正的分页问题（一个货主有几百个联系人）在业务上不该发生——那是把
 * 联系人当通讯录用，届时应改的是业务规则，不是给这个端口加参数。
 *
 * <p><b>字典校验不在这里：</b>{@code role_type} 是否属于 {@code CONTACT_ROLE} 字典的启用项，
 * 由服务层用 {@link DictLookup} 判。仓储只认「传进来的字符串」，在这里再判一次，规则就有了
 * 两个落点，而两个落点对「停用项算不算存在」迟早会判得不一样。
 *
 * <p><b>{@code phone} 的口径分歧在类型上，不在本端口：</b>列可空而模型必填，
 * 理由与代价见 {@link OwnerContactDraft} 的类注释。本端口的 {@link #findByOwnerId} 与
 * {@link #findById} 因此可能因为库里存在 {@code phone IS NULL} 的历史行而抛
 * {@link IllegalArgumentException}——这是刻意的失败，不在这里降级成「跳过该行」：
 * 悄悄少返回一条联系人，比报错更难被发现。
 *
 * <p><b>数据范围不在这里判：</b>联系人的可见性随货主。用例先经
 * {@link OwnerRepository#findVisibleById} 判过货主对当前请求可见，再调本端口；
 * 让端口再接收一遍范围参数，等于把同一条数据权限规则写到第二个地方，而漏写的那一处就是越权。
 *
 * <p>本端口不翻译数据库约束违反：哪条约束被违反只有数据库知道，而错误码属于协议层
 * （与 {@link OwnerRepository} 同一处理）。
 */
public interface OwnerContactRepository {

    /**
     * 取某货主名下全部未删除的联系人，按 {@code id} 升序。
     *
     * <p>不返回「货主是否存在」的信号：空列表只表示这个货主没有联系人，不代表货主不存在。
     * 货主是否存在由 {@link OwnerRepository#findById} / {@link OwnerRepository#findVisibleById}
     * 回答，本方法再返回一个布尔或包装类型，就为同一个问题造了第二个答案。
     *
     * <p>排序固定而不作为参数：{@code id} 升序等价于登记先后，是联系人页签唯一有意义的顺序；
     * 表里没有 {@code sort_no}，也没有可拖拽排序的需求，传一个排序字段进来只会多出
     * 「按姓名排时中文怎么排」这类与仓储无关的决定。
     *
     * @param ownerId 货主标识
     * @return 该货主的未删除联系人；没有时返回空列表
     * @throws IllegalArgumentException 库里有联系人的必填字段为空或超出列宽，说明数据已损坏，
     *                                  详见 {@link OwnerContact} 的构造器
     */
    List<OwnerContact> findByOwnerId(long ownerId);

    /**
     * 按标识读取联系人。
     *
     * <p>已逻辑删除的联系人与「不存在」同口径：管理端看不到它，接口因此必须返回同一个结果。
     * 若读取时把已删除的行排除在类型之外却让 {@link #softDelete} 之外的路径仍能拿到它，
     * 调用方就会把一条已删除的联系人当作有效记录去编辑，而所有写语句都带
     * {@code deleted_at IS NULL}——那次编辑既不生效也不报错，是最难排查的一类静默失败。
     *
     * @param id 联系人标识
     * @return 联系人；不存在或已删除时为空
     * @throws IllegalArgumentException 该行的必填字段为空或超出列宽，说明数据已损坏
     */
    Optional<OwnerContact> findById(long id);

    /**
     * 写入新联系人，返回数据库生成的标识。
     *
     * <p><b>不在这里清旧主联系人：</b>见本接口的类注释。调用方要设主联系人时，必须先调
     * {@link #clearPrimary}，否则本次插入会撞上 {@code ux_owner_contact_primary}。
     *
     * <p>审计列：{@code created_by} 与 {@code updated_by} 都写 {@code operatorId}，
     * {@code deleted_at} 留 {@code NULL}（走列默认值，不在插入语句里显式写）。
     *
     * @param ownerId    所属货主标识，调用方保证该货主存在且未删除
     * @param draft      待落库的字段值，已由 {@link OwnerContactDraft} 归一并校验
     * @param operatorId 操作人标识，写入 {@code created_by} 与 {@code updated_by}
     * @return 数据库生成的联系人标识
     * @throws IllegalStateException 插入未返回主键，说明写入与主键回填不一致
     * @throws RuntimeException      违反库约束（{@code ux_owner_contact_primary}、外键）时
     *                               由实现原样上抛，由调用方翻译成业务错误码
     */
    long insert(long ownerId, OwnerContactDraft draft, long operatorId);

    /**
     * 覆盖联系人的全部可编辑字段，写入 {@code updated_by} 与 {@code updated_at}。
     *
     * <p><b>为什么只收 {@link OwnerContactDraft}：</b>可编辑范围由类型决定。{@code id} 与
     * {@code ownerId} 不在 draft 里，调用方因此没有机会把「把联系人搬到另一个货主名下」这件事
     * 传到写语句上——靠「记得别写 {@code owner_id}」的注释约束不住下一次顺手加字段。
     *
     * <p><b>为什么没有「联系人不存在」的返回值：</b>调用方先用 {@link #findById} 判过，命中零行
     * 只可能是这一行在两次调用之间被软删除。那不是一个需要纠正的失败，也不该被当成并发冲突；
     * 影响行数因此不作为成功与否的判据（见 {@code docs/coding.md}「持久化与副作用」）。
     *
     * <p>把 {@code isPrimary} 置 {@code true} 而该货主已有另一个主联系人时，会撞上
     * {@code ux_owner_contact_primary}。端口不替你挑「该清掉哪一个」——那是用例的决定，
     * 而本方法连另一个主联系人的标识都没接收。
     *
     * @param id         联系人标识，调用方保证该联系人存在且未删除
     * @param draft      覆盖后的字段值，已由 {@link OwnerContactDraft} 归一并校验
     * @param operatorId 操作人标识，写入 {@code updated_by}
     * @throws RuntimeException 违反库约束（{@code ux_owner_contact_primary}、CHECK）时
     *                          由实现原样上抛，由调用方翻译成业务错误码
     */
    void update(long id, OwnerContactDraft draft, long operatorId);

    /**
     * 软删除联系人：置 {@code deleted_at}，同时更新 {@code updated_by} 与 {@code updated_at}。
     *
     * <p><b>为什么返回布尔值，而 {@link #update} 不返回：</b>口径与
     * {@link OwnerRepository#softDelete} 一致。删除是「这条记录从此不该再存在」的断言，
     * 而调用方刚刚读到过它——命中零行说明它在读到之后被另一个请求删掉了。那正是删除语义下
     * 唯一需要向调用方如实交代的情况，也是 {@code docs/coding.md} 要求非插入写入校验实际结果的落点。
     * {@link #update} 是「改成一堆新值」，命中零行可能只是内容本来一样，不代表用户可见的差异。
     *
     * <p>软删除会释放该货主的「主联系人」名额：部分唯一索引带 {@code deleted_at IS NULL}，
     * 删掉主联系人之后可以再设一个新的，不需要先调 {@link #clearPrimary}。
     *
     * @param id         联系人标识
     * @param operatorId 操作人标识，写入 {@code updated_by}
     * @return 实际写入了一行返回 {@code true}；该联系人已被删除（或本就不存在）返回 {@code false}
     */
    boolean softDelete(long id, long operatorId);

    /**
     * 把该货主当前的主联系人置为非主，写入 {@code updated_by} 与 {@code updated_at}。
     *
     * <p><b>为什么单独成一个动作，而不是让 {@link #insert} / {@link #update} 顺手清掉：</b>
     * 见本接口的类注释——「先清旧主、再设新主」的顺序与范围是用例的决定，两个写方法各自隐式执行
     * 一半，读代码的人就无法从调用处看出「这次操作动了别人的记录」。
     *
     * <p><b>没有主联系人时是无操作，不报错：</b>「没有主联系人」正是本方法的正常输入之一
     * （第一个主联系人也得先清一次才写，否则用例里要分叉成「有旧主」与「没有旧主」两条路径）。
     * 因此返回 {@code void} 而不是布尔：命中零行在这里既不是失败也不是需要交代的情况，
     * 与 {@link #softDelete} 的判据相反——那边零行意味着「记录已经不在了」。
     *
     * <p>不做「按 {@code ownerId} 批量清」的变体：本方法的语句本身就是一条按
     * {@code owner_id} 定位的更新，命中行数由唯一索引保证至多一行。
     *
     * @param ownerId    货主标识
     * @param operatorId 操作人标识，写入 {@code updated_by}
     */
    void clearPrimary(long ownerId, long operatorId);

    /**
     * 统计某货主名下未删除的联系人条数，口径与 {@link #findByOwnerId} 完全一致。
     *
     * <p>为什么单独给计数而不让调用方取全量再 {@code size()}：详情页签上要显示条数，
     * 为一个数字把整行（含备注）读出来是白读；与 {@link OwnerRepository#count} 和
     * {@code findPage} 的分工同理。计数必须在同一次返回里与列表一致，因此两者的筛选条件只能是
     * 「{@code owner_id} + 未删除」，不允许任一侧单独加条件。
     *
     * @param ownerId 货主标识
     * @return 未删除的联系人条数；没有时返回 {@code 0}
     */
    long countByOwnerId(long ownerId);

    /**
     * 判断该货主名下是否已有某角色的未删除联系人。
     *
     * <p><b>用途固定：</b>规格的「缺少业务对接人」场景——提交货主档案前确认至少有一条
     * {@code BUSINESS} 角色的联系人。它不是通用的「按角色查联系人」入口，不返回记录本身，
     * 因为调用方只需要一个是/否。
     *
     * <p><b>结果是提前提示，不是判重依据：</b>同一瞬间的两次调用之间可能失效（两个请求同时判断
     * 「还没有业务对接人」然后各插一条），而这里说的是「至少一条」，重复不会造成数据错误，
     * 所以没有对应的唯一索引兜底——这也是它与 {@link #findByOwnerId} 返回值形状不同的原因。
     * 反过来，「至多一条主联系人」有 {@code ux_owner_contact_primary} 兜底，那是数据库的事。
     *
     * @param ownerId  货主标识
     * @param roleType 角色值，取 {@code CONTACT_ROLE} 字典的 value，如 {@code BUSINESS}；
     *                 调用方保证非空白（空白串已在 {@link OwnerContactDraft} 被拒）。
     *                 传 {@code null} 或空白时实现可直接返回 {@code false}，不必查库：
     *                 空角色不可能是合法取值，也就不可能有命中行
     * @return 存在该角色的未删除联系人返回 {@code true}
     */
    boolean existsByRole(long ownerId, String roleType);
}

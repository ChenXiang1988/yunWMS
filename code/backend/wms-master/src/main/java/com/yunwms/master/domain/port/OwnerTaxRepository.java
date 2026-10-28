package com.yunwms.master.domain.port;

import com.yunwms.master.domain.model.OwnerTaxDraft;
import com.yunwms.master.domain.model.OwnerTaxRegistration;

import java.util.List;
import java.util.Optional;

/**
 * 货主税务登记的读写入口。
 *
 * <p><b>为什么与 {@link OwnerRepository} 分开成两个端口：</b>税号是货主的子资源，但它有自己的
 * 权限点（{@code system:owner:tax:edit}，见 {@code V042}）、自己的唯一性口径（同一国家同一州省的
 * 同一类型至多一条）与自己的审计形态（子表变更留完整集合快照）。塞进 {@link OwnerRepository}
 * 会让「改档案」与「改税号」共用一个入口，而两者的用例、审计粒度与失败语义都不同：档案编辑覆盖
 * 一批字段并整条比较差异，税号编辑只动一行、还会连带清掉同国另一行的默认标记。
 *
 * <p><b>为什么读与写仍放在同一个端口：</b>与 {@link OwnerContactRepository} 同理由——列表、新增、
 * 编辑、删除、换默认由同一族管理端用例消费，读侧与写侧没有需要靠类型隔开的分歧。
 *
 * <p><b>默认税号的唯一性由数据库保证，本端口不判：</b>{@code ux_owner_tax_default}
 * （{@code owner_id, country} 上、{@code WHERE is_default AND deleted_at IS NULL} 的部分唯一索引）
 * 是唯一权威。{@link #insert} 与 {@link #update} <b>都不隐式清掉旧的默认税号</b>：「设新默认之前
 * 先清旧默认」由用例按固定顺序调一次 {@link #clearDefault} 再写，规则因此只有一处落点。隐式清除
 * 看似贴心，实际会把「只改了一行号码」的编辑顺带改成「同国另一行的默认标记也变了」。
 *
 * <p><b>「同一国家同一州省同一类型至多一条」同样不在这里判：</b>那是 {@code ux_owner_tax_unique}
 * 的事。端口把它原样上抛，由调用方翻译成业务错误码——哪条约束被违反只有数据库知道，
 * 而错误码属于协议层（与 {@link OwnerContactRepository} 同一处理）。
 *
 * <p><b>为什么没有 {@code findByIdForUpdate}（货主端口有）：</b>税号没有状态机，换默认是
 * 「先清旧默认 → 再写新默认」；并发下两笔请求若都越过了清除步骤，后写的一笔会撞上
 * {@code ux_owner_tax_default}，那是一次能被翻译成业务错误的正常失败。加行锁只会让并发的税号
 * 编辑无谓串行化，而不减少任何一类冲突。
 *
 * <p><b>为什么不接分页、排序与计数参数：</b>单个货主的税号是个位数量级，
 * {@link #findByOwnerId} 一次取全量。加 {@code limit}/{@code offset} 会让调用方以为
 * 「不传就取全部」，而真正的分页问题（一个货主上百条税号）在业务上不该发生。
 * 也不给计数方法：需要条数时调用方取列表长度即可，多一条计数语句就多一个必须与列表同步的条件。
 *
 * <p><b>字典校验不在这里：</b>{@code tax_type} 是否属于 {@code TAX_ID_TYPE} 字典的启用项、
 * 且其 {@code ext.countries} 适用该国家，由用例层用 {@link DictLookup#isItemApplicableTo} 判。
 * 仓储再判一次，规则就有了两个落点，而两个落点对「停用项算不算存在」迟早会判得不一样。
 * 税号本身的格式（18 位等）同理，随类型语义而定。
 *
 * <p><b>数据范围不在这里判：</b>税号的可见性随货主。用例先经
 * {@link OwnerRepository#findVisibleById} 判过货主对当前请求可见，再调本端口；让端口再接收一遍
 * 范围参数，等于把同一条数据权限规则写到第二个地方，而漏写的那一处就是越权。
 */
public interface OwnerTaxRepository {

    /**
     * 取某货主名下全部未删除的税号，按 {@code country}、{@code province}、{@code tax_type} 升序。
     *
     * <p>不返回「货主是否存在」的信号：空列表只表示这个货主没有税号，不代表货主不存在。
     * 货主是否存在由 {@link OwnerRepository#findById} / {@link OwnerRepository#findVisibleById} 回答。
     *
     * <p>排序固定而不作为参数：表里没有 {@code sort_no}，也没有可拖拽排序的需求，而顺序必须
     * 可预期——同一份数据两次打开得到两种顺序，页签上的行号就失去意义。三列的顺序不是任选的：
     * 先国家再州省再类型，正好是「哪个国家的哪种税号」的阅读顺序。不补 {@code id} 兜底：
     * {@code ux_owner_tax_unique} 保证同一货主下这三列（州省按空串归一）的组合唯一。
     *
     * <p>州省为空的记录排在末尾：PostgreSQL 的 {@code ASC} 默认 {@code NULLS LAST}，语句不另写子句。
     *
     * @param ownerId 货主标识
     * @return 该货主的未删除税号；没有时返回空列表
     * @throws IllegalArgumentException 库里有税号的国家码或必填字段不合法、或超出列宽，
     *                                  说明数据已损坏，详见 {@link OwnerTaxRegistration} 的构造器
     */
    List<OwnerTaxRegistration> findByOwnerId(long ownerId);

    /**
     * 按标识读取税号。
     *
     * <p>已逻辑删除的税号与「不存在」同口径：管理端看不到它，接口因此必须返回同一个结果。
     * 若读取时把已删除的行排除在类型之外，却让别的路径仍能拿到它，调用方就会把一条已删除的
     * 税号当作有效记录去编辑，而所有写语句都带 {@code deleted_at IS NULL}——那次编辑既不生效
     * 也不报错，是最难排查的一类静默失败。
     *
     * @param id 税号标识
     * @return 税号；不存在或已删除时为空
     * @throws IllegalArgumentException 该行的国家码或必填字段不合法、或超出列宽，说明数据已损坏
     */
    Optional<OwnerTaxRegistration> findById(long id);

    /**
     * 写入新税号，返回数据库生成的标识。
     *
     * <p><b>不在这里清旧的默认税号：</b>见本接口的类注释。调用方要把新行设为默认时，必须先调
     * {@link #clearDefault}，否则本次插入会撞上 {@code ux_owner_tax_default}。
     *
     * <p>审计列：{@code created_by} 与 {@code updated_by} 都写 {@code operatorId}，
     * {@code deleted_at} 留 {@code NULL}（走列默认值，不在插入语句里显式写）。
     *
     * @param ownerId    所属货主标识，调用方保证该货主存在且未删除
     * @param draft      待落库的字段值，已由 {@link OwnerTaxDraft} 归一并校验
     * @param operatorId 操作人标识，写入 {@code created_by} 与 {@code updated_by}
     * @return 数据库生成的税号标识
     * @throws IllegalStateException 插入未返回主键，说明写入与主键回填不一致
     * @throws RuntimeException      违反库约束（{@code ux_owner_tax_unique}、{@code ux_owner_tax_default}、
     *                               外键、{@code ck_tax_country}）时由实现原样上抛，由调用方翻译成业务错误码
     */
    long insert(long ownerId, OwnerTaxDraft draft, long operatorId);

    /**
     * 覆盖税号的可编辑字段，写入 {@code updated_by} 与 {@code updated_at}。
     *
     * <p><b>可编辑的只有三项：</b>{@code province}、{@code taxNumber}、{@code isDefault}。
     * {@code ownerId} 不在 {@link OwnerTaxDraft} 里，{@code country} 与 {@code taxType} 虽在 draft 里、
     * 却不在更新语句的 SET 列表中——这三列是这一行的身份（哪个货主、哪一国的哪种税号）。
     * 改归属或改「哪国哪种税号」在业务上是另一条登记记录，应删旧登新；把选择写在类型与语句上，
     * 比依赖「记得别写那一列」的注释可靠。
     *
     * <p><b>为什么没有「税号不存在」的返回值：</b>调用方先用 {@link #findById} 判过，命中零行只可能
     * 是这一行在两次调用之间被软删除。那不是需要纠正的失败，也不该被当成并发冲突；影响行数因此
     * 不作为成功与否的判据（见 {@code docs/coding.md}「持久化与副作用」）。
     *
     * <p>把 {@code isDefault} 置 {@code true} 而该货主在该国家已有另一个默认税号时，会撞上
     * {@code ux_owner_tax_default}。端口不替你挑「该清掉哪一个」——本方法连它的标识都没接收。
     *
     * @param id         税号标识，调用方保证该税号存在且未删除
     * @param draft      覆盖后的字段值，已由 {@link OwnerTaxDraft} 归一并校验；
     *                   其中 {@code country} 与 {@code taxType} 不落库
     * @param operatorId 操作人标识，写入 {@code updated_by}
     * @throws RuntimeException 违反库约束（{@code ux_owner_tax_unique}、{@code ux_owner_tax_default}）时
     *                          由实现原样上抛，由调用方翻译成业务错误码
     */
    void update(long id, OwnerTaxDraft draft, long operatorId);

    /**
     * 软删除税号：置 {@code deleted_at}，同时更新 {@code updated_by} 与 {@code updated_at}。
     *
     * <p><b>为什么返回布尔值，而 {@link #update} 不返回：</b>口径与 {@link OwnerRepository#softDelete}
     * 一致。删除是「这条记录从此不该再存在」的断言，而调用方刚刚读到过它——命中零行说明它在读到
     * 之后被另一个请求删掉了，那正是删除语义下唯一需要如实交代的情况，也是 {@code docs/coding.md}
     * 要求非插入写入校验实际结果的落点。{@link #update} 命中零行可能只是内容本来一样。
     *
     * <p>软删除会释放该货主在该国家下的「默认税号」名额（部分唯一索引带 {@code deleted_at IS NULL}），
     * 删掉默认税号之后可以直接再设一个新的，不需要先调 {@link #clearDefault}。
     *
     * @param id         税号标识
     * @param operatorId 操作人标识，写入 {@code updated_by}
     * @return 实际写入了一行返回 {@code true}；该税号已被删除（或本就不存在）返回 {@code false}
     */
    boolean softDelete(long id, long operatorId);

    /**
     * 把该货主在该国家下当前的默认税号置为非默认，写入 {@code updated_by} 与 {@code updated_at}。
     *
     * <p><b>为什么单独成一个动作，而不是让 {@link #insert} / {@link #update} 顺手清掉：</b>
     * 见本接口的类注释——「先清旧默认、再设新默认」的顺序与范围是用例的决定，两个写方法各自
     * 隐式执行一半，读代码的人就无法从调用处看出「这次操作动了别人的记录」。
     *
     * <p><b>按国家而不是全货主清：</b>默认税号是「每国一个」（{@code ux_owner_tax_default} 的键是
     * {@code owner_id, country}），换美国的默认税号不该顺手把中国的默认标记也清掉。
     *
     * <p><b>没有默认税号时是无操作，不报错：</b>「该国家下还没有默认」正是本方法的正常输入之一
     * （第一个默认税号也得先清一次才写，否则用例里要分叉成「有旧默认」与「没有旧默认」两条路径）。
     * 因此返回 {@code void} 而不是布尔：命中零行在这里既不是失败也不是需要交代的情况，
     * 与 {@link #softDelete} 的判据相反——那边零行意味着「记录已经不在了」。
     *
     * @param ownerId    货主标识
     * @param country    国家，两位大写字母；调用方保证非空，且与待写入行的 {@code country} 同值
     *                   （{@link OwnerTaxDraft} 已保证这一形态）
     * @param operatorId 操作人标识，写入 {@code updated_by}
     */
    void clearDefault(long ownerId, String country, long operatorId);
}

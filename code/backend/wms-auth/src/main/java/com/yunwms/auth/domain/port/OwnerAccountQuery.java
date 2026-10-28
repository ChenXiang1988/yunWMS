package com.yunwms.auth.domain.port;

import java.util.OptionalLong;

/**
 * 货主与账号之间的两个归属查询：某货主有几个账号、某个登录名归哪个货主。
 *
 * <p><b>为什么要开这个端口：</b>{@code sys_user} 属于本模块，「某货主名下还有几条账号记录」这个结论
 * 只有本模块能回答。此前 {@code wms-master} 的 {@code OwnerAccountCounter} 是由 {@code wms-app}
 * 的适配器直接拿本模块的表映射与查询条件拼出来的——它能跑，但让装配层同时依赖了本模块的持久化实现，
 * 于是「账号怎么算仍存在」这条口径散在两个模块里，改一次要记得改两处。把这个结论收成一个窄端口后，
 * 装配层只需要知道「问谁」，不需要知道「问的是哪张表、哪一列」。
 *
 * <p><b>口径：</b>
 * <ul>
 *   <li><b>已逻辑删除的账号不计入。</b>删除即「已不存在」，与商品计数同一口径。</li>
 *   <li><b>停用的账号仍计入。</b>规格说的是「仍存在的账号」。{@code status = 'DISABLED'} 的账号
 *       仍在库里、仍挂在货主名下，终止之后它仍是一条要处理的记录（是否停用、是否移交）。
 *       只数启用中的账号会让终止影响清单少数，管理员会以为终止没有后果。</li>
 *   <li><b>不按 {@code subject_type} 过滤。</b>{@code V002__sys_user.sql} 的
 *       {@code ck_user_subject} 约束已经保证「{@code owner_id} 非空」与「{@code subject_type = 'OWNER'}」
 *       互为充要条件，再加一道 {@code subject_type = 'OWNER'} 不会缩小结果集，只会把同一条规则
 *       写成第二处定义——这类重复在约束被放宽的那天不会有人记得同步。</li>
 * </ul>
 *
 * <p>不做缓存：一次计数就是一次库往返，走 {@code ix_sys_user_owner} 索引；缓存会让「刚给货主开了
 * 一个账号，影响清单上的数字却没变」变成只能说「等一会儿」的问题，而这份清单正是用来让人判断
 * 「终止之后还有没有活要收尾」的。
 */
public interface OwnerAccountQuery {

    /**
     * 某货主名下仍存在的账号数。
     *
     * @param ownerId 货主标识
     * @return 账号数量；一条都没有时返回 0
     */
    long countByOwnerId(long ownerId);

    /**
     * 按登录名反查账号归属的货主。
     *
     * <p><b>为什么要这个查询：</b>{@code wms-master} 要为货主开通主账号，登录名由货主系统编码生成。
     * 「这个货主是不是已经有主账号」与「这个登录名是不是被别的货主占了」是开号前必须分开回答的两个问题，
     * 而答案只存在 {@code sys_user} 里。把它收成端口，装配层与 master 都不需要认识那张表。
     *
     * <p><b>大小写不敏感：</b>唯一索引建在 {@code lower(login_name)} 上（{@code ux_sys_user_login_name}），
     * 按原值比较既用不到索引，也查不到大小写不同的同一账号；实现必须把入参转小写再比较。
     *
     * <p><b>登录名被内部员工占用时返回 empty：</b>员工的 {@code owner_id} 为空，没有归属货主可答。
     * 这不是「没人占用」——那次开号会在 {@code INSERT} 时撞上唯一索引，由建号用例翻译成 40901。
     * 本方法不替调用方判断「这个登录名能不能用」，它只回答「归属哪个货主」。
     *
     * <p><b>已逻辑删除的账号不计入：</b>与 {@link #countByOwnerId} 同一口径——删除即「已不存在」。
     * 只排除已删除账号的成本是：若某个货主的主账号被删除，该登录名在库里仍被占用（唯一索引不带
     * {@code deleted_at} 条件），此时开号由唯一索引拦成 40901，而不是被本方法误报成「已有主账号」。
     *
     * <p>不做缓存：一次反查就是一次索引等值查询；缓存会让「另一个请求刚建了同名账号」在本请求里看不见，
     * 而本方法的存在意义正是把冲突提前说清。
     *
     * @param loginName 登录名，调用方保证非空（登录名在 {@code sys_user} 上是 {@code NOT NULL}）
     * @return 归属货主标识；登录名不存在、已逻辑删除或该账号不归属任何货主时为 empty
     */
    OptionalLong findOwnerIdByLoginName(String loginName);
}

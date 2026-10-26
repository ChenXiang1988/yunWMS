package com.yunwms.auth.domain.port;

/**
 * 按货主统计账号数。
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
}

package com.yunwms.master.domain.port;

/**
 * 按货主统计仍存在的账号数，供「终止影响清单」使用。
 *
 * <p><b>为什么本模块只声明端口、不实现：</b>账号落在 {@code sys_user}，它属于 {@code wms-auth}；
 * {@code auth} 与 {@code master} 互不引用（{@code docs/coding-project.md} 第二节，由
 * {@code ModuleBoundaryTest} 拦截）。只有 {@code wms-app} 的运行期 classpath 同时包含两边，
 * 所以实现在 {@code wms-app} 的 {@code bridge} 包里，由它委托给 {@code wms-auth} 的
 * {@code sys_user} 映射，与 {@link DictLookup} / {@link OwnerCodeGenerator} 同一路子。
 *
 * <p><b>停用的账号也计入：</b>规格说的是「仍存在的账号数」。{@code sys_user.status = 'DISABLED'}
 * 的账号仍在库里、仍挂在货主名下，终止之后它仍是一条要处理的记录（是否停用、是否移交）。
 * 只数启用中的账号会让影响清单少数，管理员会以为终止没有后果。
 *
 * <p><b>已逻辑删除的账号不计入：</b>那是「已不存在」，与商品计数同一口径。
 */
public interface OwnerAccountCounter {

    /**
     * 统计该货主下仍存在（未逻辑删除）的账号数，含已停用的账号。
     *
     * @param ownerId 货主标识
     * @return 账号数量；一个都没有时返回 0
     */
    long countByOwnerId(long ownerId);
}

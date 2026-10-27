package com.yunwms.master.domain.port;

import java.util.Set;

/**
 * 按用户读取他负责（主或副）的货主标识。
 *
 * <p>给 {@code ASSIGNED}（我负责的货主）数据范围用：规格要求这个范围实时推导，
 * 不存静态名单——存了之后每新增一个货主都要回写所有客服，漏一个就少看一个货主。
 *
 * <p>主副负责人都算：规格的可见范围是「当前担任主或副负责人的货主之并集」。
 *
 * <p>不按货主状态过滤：本端口回答的是关系，不是「这个货主现在还有没有业务」。
 */
public interface OwnerAssigneeReader {

    /**
     * 取某个用户负责的全部货主标识。
     *
     * @param userId 用户标识
     * @return 货主标识集合；没有负责任何货主时为空集合
     */
    Set<Long> findOwnerIdsByUserId(long userId);
}

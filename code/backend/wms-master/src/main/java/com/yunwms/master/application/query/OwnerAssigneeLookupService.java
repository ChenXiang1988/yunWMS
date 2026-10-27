package com.yunwms.master.application.query;

import com.yunwms.master.domain.port.OwnerAssigneeReader;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * {@link OwnerAssigneeLookup} 的实现：直接转给仓储，不做二次加工。
 *
 * <p><b>为什么不加 {@code @Transactional(readOnly = true)}：</b>只有一次只读查询，没有需要原子提交的
 * 第二处写入；加注解不会让结果更一致，只会多一层事务包装。
 *
 * <p>不缓存：这条查询服务于「角色停用、负责人变更立即生效」这条规格要求，
 * 缓存要放在调用方（请求级），放在这里会让「改了负责人却要等一会儿才生效」成为需要解释的现象。
 */
@Service
public class OwnerAssigneeLookupService implements OwnerAssigneeLookup {

    private final OwnerAssigneeReader ownerAssigneeReader;

    public OwnerAssigneeLookupService(OwnerAssigneeReader ownerAssigneeReader) {
        this.ownerAssigneeReader = ownerAssigneeReader;
    }

    @Override
    public Set<Long> findOwnerIdsByUserId(long userId) {
        return ownerAssigneeReader.findOwnerIdsByUserId(userId);
    }
}

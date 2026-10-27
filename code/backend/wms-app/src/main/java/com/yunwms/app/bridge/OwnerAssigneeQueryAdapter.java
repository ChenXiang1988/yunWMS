package com.yunwms.app.bridge;

import com.yunwms.auth.domain.port.OwnerAssigneeQuery;
import com.yunwms.master.application.query.OwnerAssigneeLookup;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 把 {@code wms-master} 的「某人负责哪些货主」接到 {@code wms-auth} 的 {@link OwnerAssigneeQuery} 上。
 *
 * <p><b>为什么这个类在 {@code wms-app}：</b>{@code owner_assignee} 是货主侧事实（谁负责哪个货主），
 * 而 {@code ASSIGNED} 数据范围的推导在 {@code wms-auth}。两个业务模块互不引用
 * （{@code docs/coding-project.md} 第二节，由 {@code ModuleBoundaryTest} 与
 * {@code MapperTableAccessTest} 拦截），只有 {@code wms-app} 的运行期 classpath 同时包含两边。
 *
 * <p>不在这一层做缓存：请求级缓存由 {@code wms-auth} 的 {@code DataScopeContext} 负责，
 * 这里缓存会让「换了负责人多久生效」变成需要等过期才解释得清的问题。
 *
 * <p>不改写口径（主副都算、不按货主状态过滤）：那是 {@link OwnerAssigneeLookup} 的契约，
 * 适配器只做翻译。
 */
@Component
public class OwnerAssigneeQueryAdapter implements OwnerAssigneeQuery {

    private final OwnerAssigneeLookup ownerAssigneeLookup;

    public OwnerAssigneeQueryAdapter(OwnerAssigneeLookup ownerAssigneeLookup) {
        this.ownerAssigneeLookup = ownerAssigneeLookup;
    }

    @Override
    public Set<Long> findAssignedOwnerIds(long userId) {
        return ownerAssigneeLookup.findOwnerIdsByUserId(userId);
    }
}

package com.yunwms.app.bridge;

import com.yunwms.auth.domain.port.OwnerAccountQuery;
import com.yunwms.master.domain.port.OwnerAccountCounter;
import org.springframework.stereotype.Component;

/**
 * 把 {@code wms-master} 的账号计数窄端口接到 {@code wms-auth} 的账号计数查询上。
 *
 * <p><b>为什么这个类在 {@code wms-app}：</b>{@code sys_user} 属于 {@code wms-auth}，而
 * {@code auth} 与 {@code master} 互不引用（{@code docs/coding-project.md} 第二节，由
 * {@code ModuleBoundaryTest} 拦截）。只有 {@code wms-app} 的运行期 classpath 同时包含两边，
 * 与 {@link DictLookupAdapter} / {@link OwnerCodeGeneratorAdapter} 同一路子。
 *
 * <p><b>为什么委托到 {@link OwnerAccountQuery} 而不是自己查：</b>「{@code sys_user} 里怎么算一条
 * 仍存在的账号」这件事的唯一所有者是 {@code wms-auth}——软删除的表示、停用是否计入、要不要按主体
 * 类型过滤，答案都在那张表所在的模块里。委托之后本类只管一件事：把「统计某货主的账号数」翻译成
 * 「问 {@code wms-auth} 同一个问题」，本模块（装配层）不持有任何计数口径。
 *
 * <p>这同时修掉了此前的一处依赖违规：本类原先直接使用 {@code wms-auth} 的持久化类型与
 * mybatis-plus 的查询条件构造器，使 {@code wms-app} 既等着未声明的传递依赖，也把耦合面伸进了
 * 别的模块的内部包。改为只依赖对端的 domain 端口后，{@code wms-app} 的 classpath 依赖与它的
 * import 重新对得上，跨模块只经端口交互（{@code docs/coding-java.md}「模块 MUST 声明实际使用的
 * 直接依赖」）。
 *
 * <p>不做缓存、不做兜底：一次计数就是一次库往返；缓存会让「新增了一个账号，弹窗上的数字却没变」
 * 变成只能说「等一会儿」的问题。
 */
@Component
public class OwnerAccountCounterAdapter implements OwnerAccountCounter {

    private final OwnerAccountQuery ownerAccountQuery;

    public OwnerAccountCounterAdapter(OwnerAccountQuery ownerAccountQuery) {
        this.ownerAccountQuery = ownerAccountQuery;
    }

    /**
     * {@inheritDoc}
     *
     * <p>口径（排除已删除、停用仍计入）由对端端口定义，本类不重述也不改写结果。
     */
    @Override
    public long countByOwnerId(long ownerId) {
        return ownerAccountQuery.countByOwnerId(ownerId);
    }
}

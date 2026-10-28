package com.yunwms.app.bridge;

import com.yunwms.auth.domain.port.OwnerAccountQuery;
import com.yunwms.master.domain.port.OwnerAccountLookup;
import org.springframework.stereotype.Component;

import java.util.OptionalLong;

/**
 * 把 {@code wms-master} 的「登录名归哪个货主」窄端口接到 {@code wms-auth} 的反查上。
 *
 * <p><b>为什么这个类在 {@code wms-app}：</b>登录名与归属落在 {@code sys_user}，它属于 {@code wms-auth}，
 * 而 {@code auth} 与 {@code master} 互不引用（{@code docs/coding-project.md} 第二节，由
 * {@code ModuleBoundaryTest} 拦截）。只有 {@code wms-app} 的运行期 classpath 同时包含两边，
 * 与 {@link OwnerAccountCounterAdapter} 同一路子。
 *
 * <p><b>为什么委托到 {@link OwnerAccountQuery} 而不是自己查：</b>「登录名怎么比大小写」「已删除的账号
 * 算不算存在」这两个口径的唯一所有者是 {@code wms-auth}——它们由 {@code sys_user} 上的
 * {@code ux_sys_user_login_name} 与逻辑删除配置决定。在这里再拼一次查询条件，等于把同一份口径
 * 复制到装配层，而装配层恰恰是最没有理由知道表结构的地方。
 *
 * <p>不缓存、不兜底：一次反查就是一次索引等值查询。缓存会让「另一个请求刚建了同名账号」在本请求里
 * 看不见，而这次反查的全部意义就是把冲突提前说清；捕获异常改返回 empty 则会把「查不出来」伪装成
 * 「没人占用」，于是一次数据库故障会表现为建号时的 40901。
 */
@Component
public class OwnerAccountLookupAdapter implements OwnerAccountLookup {

    private final OwnerAccountQuery ownerAccountQuery;

    public OwnerAccountLookupAdapter(OwnerAccountQuery ownerAccountQuery) {
        this.ownerAccountQuery = ownerAccountQuery;
    }

    /**
     * {@inheritDoc}
     *
     * <p>大小写不敏感的匹配与「已删除视为不存在」由对端端口定义，本类不重述也不改写结果。
     */
    @Override
    public OptionalLong findOwnerIdByLoginName(String loginName) {
        return ownerAccountQuery.findOwnerIdByLoginName(loginName);
    }
}

package com.yunwms.app.bridge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysUserMapper;
import com.yunwms.master.domain.port.OwnerAccountCounter;
import org.springframework.stereotype.Component;

/**
 * 把 {@code wms-master} 的账号计数窄端口接到 {@code wms-auth} 的 {@code sys_user} 上。
 *
 * <p><b>为什么这个类在 {@code wms-app}：</b>{@code sys_user} 属于 {@code wms-auth}，而
 * {@code auth} 与 {@code master} 互不引用（{@code docs/coding-project.md} 第二节，由
 * {@code ModuleBoundaryTest} 拦截）。只有 {@code wms-app} 的运行期 classpath 同时包含两边，
 * 与 {@link DictLookupAdapter} / {@link OwnerCodeGeneratorAdapter} 同一路子。
 *
 * <p><b>为什么委托到 {@code SysUserMapper} 而不是在 {@code wms-app} 里写一条 SQL：</b>
 * 「{@code sys_user} 怎么算一条仍存在的账号」这件事已经有唯一所有者——{@code SysUserEntity}
 * 的 {@code deleted_at} 配合 {@code application.yml} 的 {@code logic-delete-field}。
 * 在本类里拼 {@code deleted_at IS NULL}，等于把这条口径复制到第二个模块：哪一天逻辑删除的表示
 * 变了（比如改用 {@code deleted} 布尔列），auth 侧改一处、这里漏改，影响清单会开始少算或多算
 * 账号，而没有任何东西会报错。委托之后，本类只管一件事：把 {@code owner_id} 的等值条件交给它。
 *
 * <p><b>口径：</b>不按 {@code status} 过滤——停用的账号仍然存在，仍在货主名下，终止之后仍是
 * 一条要处理的记录。只排除已逻辑删除的账号，这一条由 MyBatis-Plus 的逻辑删除自动带上，
 * 本类刻意不再写一遍。
 *
 * <p>不做缓存、不做兜底：一次计数就是一次库往返，命中 {@code sys_user.owner_id} 上的既有索引；
 * 缓存会让「新增了一个账号，弹窗上的数字却没变」变成只能说「等一会儿」的问题。
 */
@Component
public class OwnerAccountCounterAdapter implements OwnerAccountCounter {

    private final SysUserMapper sysUserMapper;

    public OwnerAccountCounterAdapter(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>用 {@code selectCount} 而不是「查出一页再数长度」：后者在账号数增长后会变成一次大查询，
     * 而本方法只需要一个 {@code count(*)}。
     *
     * @throws IllegalStateException 计数没有返回结果，说明映射或语句被改坏；不返回 0 兜底，
     *                               那会让影响清单少算整批账号而不报错
     */
    @Override
    public long countByOwnerId(long ownerId) {
        Long count = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUserEntity>().eq(SysUserEntity::getOwnerId, ownerId));
        if (count == null) {
            throw new IllegalStateException("账号计数未返回结果，货主 id=" + ownerId);
        }
        return count;
    }
}

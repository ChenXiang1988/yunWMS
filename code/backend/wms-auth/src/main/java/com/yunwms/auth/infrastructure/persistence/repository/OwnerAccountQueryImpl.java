package com.yunwms.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yunwms.auth.domain.port.OwnerAccountQuery;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysUserMapper;
import org.springframework.stereotype.Repository;

/**
 * {@link OwnerAccountQuery} 的实现：{@code sys_user} 上「某货主还剩几条账号」的唯一一处定义。
 *
 * <p>用 {@code BaseMapper} 的计数语句加一个 {@code owner_id} 等值条件，不上 XML：
 * 这是单表等值计数（{@code docs/coding-java.md} 要求这类查询优先走 Wrapper），
 * 写成 XML 只会多一个要同步维护的文件。
 *
 * <p>语句里不写 {@code deleted_at IS NULL}：{@code application.yml} 配置了全局逻辑删除
 * （{@code logic-delete-field: deletedAt}），MyBatis-Plus 会给计数语句自动补上该条件。
 * 在这里再写一遍，等于让「已删除怎么表示」同时存在两处定义——哪天软删除改用别的列，
 * 全局配置改一处就够，而手写的那一份会被漏掉，计数从此把已删除的账号也数进去。
 * 同理不写 {@code status} 条件：停用的账号仍计入（口径见 {@link OwnerAccountQuery}）。
 *
 * <p>计数为空时抛异常而不是返回 0：{@code count(*)} 一定有结果行，读不到说明语句或结果映射
 * 被改坏了；此时返回 0 会让终止影响清单少算整批账号，且没有任何东西会报错。
 */
@Repository
public class OwnerAccountQueryImpl implements OwnerAccountQuery {

    private final SysUserMapper sysUserMapper;

    public OwnerAccountQueryImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>用计数语句而不是「查出一页再数长度」：后者在账号数增长后会变成一次大查询，
     * 而这里只需要一个 {@code count(*)}。
     *
     * @throws IllegalStateException 计数没有返回结果，说明语句或结果映射被改坏；不返回 0 兜底
     */
    @Override
    public long countByOwnerId(long ownerId) {
        Long count = sysUserMapper.selectCount(Wrappers.<SysUserEntity>lambdaQuery()
                .eq(SysUserEntity::getOwnerId, ownerId));
        if (count == null) {
            throw new IllegalStateException("账号计数未返回结果，货主 id=" + ownerId);
        }
        return count;
    }
}

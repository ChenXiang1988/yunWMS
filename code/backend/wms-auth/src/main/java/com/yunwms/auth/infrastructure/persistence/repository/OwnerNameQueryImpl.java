package com.yunwms.auth.infrastructure.persistence.repository;

import com.yunwms.auth.domain.port.OwnerNameQuery;
import com.yunwms.auth.infrastructure.persistence.entity.OwnerNameRow;
import com.yunwms.auth.infrastructure.persistence.mapper.OwnerNameMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@link OwnerNameQuery} 的实现：<b>本模块唯一一处直接读 {@code owner} 表的代码</b>。
 *
 * <p>这是跨模块读取的临时实现。用户列表要显示「这个账号绑的是哪个货主」，而 {@code wms-master}
 * 尚未提供货主查询能力；把越界收在这一个类里，等到 {@code wms-master} 能回答这个问题时，
 * 只需删除本类与 {@code OwnerNameMapper}，改为注入其 Application 接口。
 *
 * <p>为什么不把这条 SQL 并进用户列表的语句：那样越界就散进了所有用到货主名称的查询，
 * 替换时没人找得到全部副本（理由详见 {@link OwnerNameQuery}）。
 */
@Repository
public class OwnerNameQueryImpl implements OwnerNameQuery {

    private final OwnerNameMapper ownerNameMapper;

    public OwnerNameQueryImpl(OwnerNameMapper ownerNameMapper) {
        this.ownerNameMapper = ownerNameMapper;
    }

    @Override
    public Map<Long, String> findNamesByIds(Collection<Long> ownerIds) {
        if (ownerIds.isEmpty()) {
            // 空的 IN 条件在拼装时会被省略，查询会退化成「查全部货主」；
            // 调用方虽然已被约定为非空入参，这里仍短路一次，避免一处遗漏变成全表扫描
            return Map.of();
        }
        return ownerNameMapper.selectNamesByIds(ownerIds).stream()
                // id 是主键，不会出现同名键；用 toMap 而不是 groupingBy，
                // 是为了让「一个标识对应一个名称」这件事在类型上就成立
                .collect(Collectors.toMap(OwnerNameRow::getId, OwnerNameRow::getName));
    }
}

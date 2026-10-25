package com.yunwms.auth.infrastructure.persistence.mapper;

import com.yunwms.auth.infrastructure.persistence.entity.OwnerNameRow;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 跨模块读取 {@code owner} 表名称的映射接口——<b>本模块唯一一处读自己以外的表</b>。
 *
 * <p>为什么不在用户列表的 SQL 里 join：见 {@link com.yunwms.auth.domain.port.OwnerNameQuery}。
 * 越界读取一旦散进多条 SQL 就再也收不回来，因此这里只有「按标识取名称」这一种查询，
 * 并且刻意<b>不</b>提供任何按货主读其它列或写入的方法：多一个方法就多一分「本模块也能管货主」的错觉。
 *
 * <p>{@code wms-master} 具备货主查询能力后，删除本接口、本 XML 与
 * {@code OwnerNameQueryImpl}，改为注入其 Application 接口。
 */
public interface OwnerNameMapper {

    /**
     * 按标识批量取货主名称。
     *
     * @param ownerIds 货主标识集合；调用方保证非空（空集合会退化成查全表）
     * @return 货主标识与名称；查不到的标识不出现在结果里
     */
    List<OwnerNameRow> selectNamesByIds(@Param("ownerIds") Collection<Long> ownerIds);
}

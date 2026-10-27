package com.yunwms.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunwms.auth.infrastructure.persistence.entity.SysDataScopeOwnerEntity;

/**
 * {@code sys_data_scope_owner} 的映射接口。
 *
 * <p>全部是单表操作（按角色或用户批量读、按角色或用户整批删、逐行插入），
 * {@link BaseMapper} 加 Lambda Wrapper 就能表达，不需要 XML。
 *
 * <p>删除语句必须带 {@code role_id} 或 {@code user_id} 条件：本表没有主键映射
 * （见 {@link SysDataScopeOwnerEntity}），任何「按主键删」的写法都删不到行，
 * 而按空条件删会清空整张表——两种失败都不会报错。
 */
public interface SysDataScopeOwnerMapper extends BaseMapper<SysDataScopeOwnerEntity> {
}

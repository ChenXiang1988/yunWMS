package com.yunwms.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserEntity;

/**
 * {@code sys_user} 的映射接口。
 *
 * <p>不写自定义 SQL：登录需要的查询是单表等值条件（大小写不敏感的登录名比较用 Wrapper 的
 * 参数化片段表达），没有多表或数据库特有语法。
 */
public interface SysUserMapper extends BaseMapper<SysUserEntity> {
}

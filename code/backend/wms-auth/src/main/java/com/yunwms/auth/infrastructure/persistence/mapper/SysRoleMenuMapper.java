package com.yunwms.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunwms.auth.infrastructure.persistence.entity.SysRoleMenuEntity;

/**
 * {@code sys_role_menu} 的映射接口。
 *
 * <p>两条用途都是单表、条件简单（按角色读写），用 {@code BaseMapper} 表达即可，不需要 XML。
 */
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenuEntity> {
}

package com.yunwms.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserRoleEntity;
import com.yunwms.auth.infrastructure.persistence.entity.UserRoleGrantRow;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * {@code sys_user_role} 的映射接口。
 *
 * <p>按用户取关联行、按用户整批删除关联都用 {@code BaseMapper} / Wrapper 表达；
 * 只有「一次取回多个用户的角色编码」走 XML：它要连接 {@code sys_role} 取编码，
 * 且入参是集合，放在 XML 里能一眼看出这是「一页一次」而不是「一行一次」。
 */
public interface SysUserRoleMapper extends BaseMapper<SysUserRoleEntity> {

    /**
     * 批量取多个用户的角色授权，含角色编码。
     *
     * <p>存在的理由就是避免 N+1：用户列表一页 20 行，逐行查角色就是 20 次往返。
     *
     * @param userIds 用户标识集合；调用方保证非空（空集合会退化成查全部关联）
     * @return 授权行，按用户与角色的排序号升序；没有关联时为空列表
     */
    List<UserRoleGrantRow> selectGrantsByUserIds(@Param("userIds") Collection<Long> userIds);
}

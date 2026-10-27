package com.yunwms.auth.application.dto;

import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.model.RoleSummary;

import java.util.List;

/**
 * 角色详情：列表行的那份摘要，加上该角色已勾选的菜单与权限点、以及它的数据范围配置。
 *
 * <p>用组合而不是把摘要的字段再铺一遍：详情与列表在「角色是什么、占用多少」上必须是同一份口径，
 * 铺开成两个 record 之后，给摘要加一个字段就会漏掉其中一处，而漏掉的那处不会有编译错误。
 *
 * <p>数据范围只出现在详情里，不进 {@link RoleSummary}：列表页不展示也不配置它，塞进摘要会让每次
 * 列表查询都多读两份与列表无关的数据（范围类型与明细）。配置页要回填「当前是哪种范围、选了哪些货主」，
 * 那是详情该回答的问题。
 *
 * @param summary       角色摘要，与列表行同源
 * @param menuIds       已勾选的菜单与权限点标识，按标识升序；未勾选任何项时为空列表
 * @param dataScopeType 数据范围类型
 * @param ownerIds      {@code CUSTOM} 时的指定货主标识，按标识升序；其他类型为空列表
 */
public record RoleDetail(RoleSummary summary,
                         List<Long> menuIds,
                         DataScopeType dataScopeType,
                         List<Long> ownerIds) {
}

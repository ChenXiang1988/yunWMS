package com.yunwms.auth.application.dto;

import com.yunwms.auth.domain.model.RoleSummary;

import java.util.List;

/**
 * 角色详情：列表行的那份摘要，加上该角色已勾选的菜单与权限点。
 *
 * <p>用组合而不是把摘要的字段再铺一遍：详情与列表在「角色是什么、占用多少」上必须是同一份口径，
 * 铺开成两个 record 之后，给摘要加一个字段就会漏掉其中一处，而漏掉的那处不会有编译错误。
 *
 * @param summary 角色摘要，与列表行同源
 * @param menuIds 已勾选的菜单与权限点标识，按标识升序；未勾选任何项时为空列表
 */
public record RoleDetail(RoleSummary summary, List<Long> menuIds) {
}

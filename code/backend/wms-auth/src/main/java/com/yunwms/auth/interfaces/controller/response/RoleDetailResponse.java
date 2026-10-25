package com.yunwms.auth.interfaces.controller.response;

import java.util.List;

/**
 * 角色详情：列表行的全部字段，加上该角色已勾选的菜单与权限点。
 *
 * <p>{@code menuIds} 含菜单（{@code C}）与操作（{@code F}）两级：配置界面是一棵树，
 * 只回操作级会让菜单节点无法回显勾选状态。元素为字符串，理由同 {@code id}。
 *
 * @param id              角色标识，字符串形式
 * @param code            角色编码
 * @param name            角色名称
 * @param builtin         是否内置角色
 * @param status          角色状态编码
 * @param userCount       已分配该角色的用户数
 * @param permissionCount 已勾选的菜单与权限点数量
 * @param remark          备注，可为 {@code null}
 * @param menuIds         已勾选的菜单与权限点标识；未勾选时为空数组
 */
public record RoleDetailResponse(String id,
                                 String code,
                                 String name,
                                 boolean builtin,
                                 String status,
                                 long userCount,
                                 long permissionCount,
                                 String remark,
                                 List<String> menuIds) {
}

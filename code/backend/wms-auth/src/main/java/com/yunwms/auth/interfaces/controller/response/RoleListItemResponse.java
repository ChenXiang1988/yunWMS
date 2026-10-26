package com.yunwms.auth.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 角色列表的一行。
 *
 * <p>{@code userCount} 与 {@code permissionCount} 由同一条 SQL 聚合得出（不是逐行再查），
 * 前端可以用 {@code userCount} 判断「该角色是否还能删除」的提示文案，不必再发一次请求。
 *
 * @param id              角色标识，字符串形式
 * @param code            角色编码
 * @param name            角色名称
 * @param builtin         是否内置角色；内置角色不可编辑、不可删除
 * @param status          角色状态编码，取值见后端的角色状态枚举名
 * @param userCount       已分配该角色的用户数
 * @param permissionCount 已勾选的菜单与权限点数量
 * @param remark          备注，可为 {@code null}
 */
public record RoleListItemResponse(
        @Schema(description = "角色标识，字符串形式的十进制整数；详情、编辑、启停、删除都用它定位")
        String id,

        @Schema(description = "角色编码，程序按它引用角色，创建后不可修改")
        String code,

        @Schema(description = "角色名称，界面展示用，可重名")
        String name,

        @Schema(description = "是否内置角色：true 表示超级管理员一类的系统自带角色，不可编辑、不可停用、不可删除")
        boolean builtin,

        @Schema(description = "角色状态编码：ACTIVE 启用，DISABLED 停用。停用的角色仍会出现在列表里，"
                + "但其权限点在下一次请求起不再生效")
        String status,

        @Schema(description = "已分配该角色的用户数，取值为整数；大于 0 时该角色不可删除")
        long userCount,

        @Schema(description = "该角色已勾选的菜单与权限点数量，取值为整数（菜单与操作两级都计入）")
        long permissionCount,

        @Schema(description = "备注，可为 null")
        String remark) {
}

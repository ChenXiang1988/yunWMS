package com.yunwms.auth.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

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
 * @param dataScopeType   数据范围类型编码
 * @param ownerIds        {@code CUSTOM} 时已选定的货主标识；其他类型为空数组
 */
public record RoleDetailResponse(
        @Schema(description = "角色标识，字符串形式的十进制整数；与列表行同名字段含义一致")
        String id,

        @Schema(description = "角色编码，程序按它引用角色，不可修改")
        String code,

        @Schema(description = "角色名称，可编辑")
        String name,

        @Schema(description = "是否内置角色：true 表示系统自带角色，不可编辑、不可停用、不可删除")
        boolean builtin,

        @Schema(description = "角色状态编码：ACTIVE 启用，DISABLED 停用")
        String status,

        @Schema(description = "已分配该角色的用户数，取值为整数；大于 0 时该角色不可删除")
        long userCount,

        @Schema(description = "该角色已勾选的菜单与权限点数量，取值为整数")
        long permissionCount,

        @Schema(description = "备注，可为 null")
        String remark,

        @Schema(description = "已勾选的菜单与权限点标识（字符串形式的十进制整数），含菜单与操作两级；"
                + "未勾选时为空数组。用于回填授权配置页的勾选状态")
        List<String> menuIds,

        @Schema(description = "数据范围类型编码：ALL 全部货主，ASSIGNED 我负责的货主，CUSTOM 指定货主")
        String dataScopeType,

        @Schema(description = "数据范围为 CUSTOM 时已选定的货主标识（字符串形式的十进制整数），按标识升序；"
                + "ALL 与 ASSIGNED 时为空数组。与 dataScopeType 一起用于回填数据范围配置")
        List<String> ownerIds) {
}

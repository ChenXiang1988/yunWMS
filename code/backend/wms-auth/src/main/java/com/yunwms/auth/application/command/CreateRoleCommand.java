package com.yunwms.auth.application.command;

import java.util.List;

/**
 * 建角色的入参。
 *
 * <p>携带原始值，不做清洗：去空白、空串按未填处理属于业务口径，与编码格式、名称必填同处用例层；
 * 放在协议层会让「接口能建出什么角色」取决于调用方是不是 HTTP。
 *
 * <p>没有 {@code builtin}、{@code dataScopeType} 与 {@code status}：三者都不是建角色的可选项，
 * 理由见 {@code NewRole}。请求体里出现这些字段会被 {@code FAIL_ON_UNKNOWN_PROPERTIES} 直接拒绝，
 * 而不是「收下但忽略」——忽略会让前端以为自己改生效了。
 *
 * @param code    角色编码
 * @param name    角色名称
 * @param remark  备注，可为 {@code null}
 * @param menuIds 勾选的菜单与权限点标识；为空或省略表示不授权任何权限
 */
public record CreateRoleCommand(String code, String name, String remark, List<Long> menuIds) {
}

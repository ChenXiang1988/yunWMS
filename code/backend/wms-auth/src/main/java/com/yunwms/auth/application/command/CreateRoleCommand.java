package com.yunwms.auth.application.command;

import com.yunwms.auth.domain.model.DataScopeType;

import java.util.List;

/**
 * 建角色的入参。
 *
 * <p>携带原始值，不做清洗：去空白、空串按未填处理属于业务口径，与编码格式、名称必填同处用例层；
 * 放在协议层会让「接口能建出什么角色」取决于调用方是不是 HTTP。
 *
 * <p>没有 {@code builtin} 与 {@code status}：两者都不是建角色的可选项，理由见 {@code NewRole}。
 * 请求体里出现这些字段会被 {@code FAIL_ON_UNKNOWN_PROPERTIES} 直接拒绝，
 * 而不是「收下但忽略」——忽略会让前端以为自己改生效了。
 *
 * <p>数据范围是<b>可选</b>入参：省略时按最保守的 {@link DataScopeType#CUSTOM} 空范围建角色，
 * 由管理员随后在配置端点里明确设置。允许建角色时一次给全，是为了不让「建一个只做某几个货主的角色」
 * 变成两次请求——两次请求之间角色已经存在且无范围，是一个真实可用的中间状态。
 *
 * @param code          角色编码
 * @param name          角色名称
 * @param remark        备注，可为 {@code null}
 * @param menuIds       勾选的菜单与权限点标识；为空或省略表示不授权任何权限
 * @param dataScopeType 数据范围类型；为 {@code null} 表示未指定，按 {@code CUSTOM} 处理
 * @param ownerIds      {@code CUSTOM} 时的指定货主标识；其他类型必须为空或省略
 */
public record CreateRoleCommand(String code,
                                String name,
                                String remark,
                                List<Long> menuIds,
                                DataScopeType dataScopeType,
                                List<Long> ownerIds) {
}

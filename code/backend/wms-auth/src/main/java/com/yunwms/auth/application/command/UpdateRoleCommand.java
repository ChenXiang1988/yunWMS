package com.yunwms.auth.application.command;

import java.util.List;

/**
 * 编辑角色的入参。
 *
 * <p>没有 {@code code}：编码是程序引用角色的标识，与用户的登录名同一口径——改了等于换一个角色
 * （配置里写死的 {@code code} 会指向一个不存在的角色）。请求体里出现它会按未知字段拒绝，
 * 而不是「收下但忽略」。
 *
 * <p>语义是<b>全量替换</b>名称、备注与授权：{@code menuIds} 提交的就是这个角色之后拥有的全部
 * 权限点，空集合表示清空。不做「传了才改」的部分更新——同一接口同时支持两种语义时，
 * 前端无法表达「把这个权限点去掉」，而清空授权恰好是真实需求。
 *
 * <p>携带原始值，不做清洗：去空白、空串按未填处理的口径与建角色一致，属于用例层规则，
 * 放在协议层会让「能改成什么」取决于调用方是不是 HTTP。
 *
 * @param roleId  角色标识
 * @param name    角色名称
 * @param remark  备注，可为 {@code null}
 * @param menuIds 替换后的菜单与权限点标识；空集合表示清空全部授权
 */
public record UpdateRoleCommand(long roleId, String name, String remark, List<Long> menuIds) {
}

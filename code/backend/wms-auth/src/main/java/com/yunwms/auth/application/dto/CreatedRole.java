package com.yunwms.auth.application.dto;

/**
 * 建角色的结果。
 *
 * <p>只回标识与编码：管理员刚提交的就是这些，原样回显没有信息量；把
 * {@code builtin}、{@code status} 之类的固定值回显出去，反而让前端以为它们是可变的。
 *
 * @param id   数据库生成的角色标识
 * @param code 角色编码，按落库值返回
 */
public record CreatedRole(long id, String code) {
}

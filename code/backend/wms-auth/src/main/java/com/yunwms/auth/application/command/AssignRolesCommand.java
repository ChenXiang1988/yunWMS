package com.yunwms.auth.application.command;

import java.util.List;

/**
 * 给用户分配角色的入参。
 *
 * <p>语义是<b>整批替换</b>，不是追加：管理员在编辑表单里提交的是「这个人的角色应该是哪几个」，
 * 而不是「再给他加一个」。请求里没有 {@code userId} 之外的定位条件，也没有
 * {@code append} 之类的开关——同一接口同时支持替换与追加，会让「提交少了两个角色」
 * 与「只想加一个」在库里变成同一种结果，事后无法判断当时是哪种意图。
 *
 * <p>{@code roleIds} 为空列表表示移除该用户的全部角色，这是合法状态（账号可以没有任何角色，
 * 登录后会被引导页拦住）；为 {@code null} 表示调用方没有传这个字段，由请求体校验拒绝，
 * 两者不能混为一谈。
 *
 * @param userId  目标用户标识
 * @param roleIds 替换后的角色标识集合，按提交顺序去重；空列表表示移除全部角色
 */
public record AssignRolesCommand(long userId, List<Long> roleIds) {
}

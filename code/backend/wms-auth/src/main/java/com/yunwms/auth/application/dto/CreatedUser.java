package com.yunwms.auth.application.dto;

/**
 * 建号结果。
 *
 * <p>只回标识与登录名，不回密码、不回完整账号：管理员刚提交的就是这些字段，原样回显没有信息量；
 * 而把 {@code mustChangePassword} 之类状态回显出去，会让前端误以为可以据此跳过首次改密。
 *
 * @param id        数据库生成的用户标识
 * @param loginName 登录名，按落库值返回（可能与提交值大小写不同，但不会只差空白）
 */
public record CreatedUser(long id, String loginName) {
}

package com.yunwms.auth.application.command;

/**
 * 编辑用户基本信息的入参。
 *
 * <p>只有三个字段：展示名、邮箱、企业微信 userid。登录名不在这里——它是账号的标识，
 * 改了等于换一个账号（旧登录名不再能登录，而登录名是别人找到这个人的依据）；
 * 主体类型与所属货主也不在这里——它们决定数据归属，改了等于把这个账号迁移到另一个货主名下，
 * 规格明确「主账号不可迁移」。要迁移是另一件事，不该藏在「编辑基本信息」里。
 *
 * <p>语义是<b>全量替换</b>这三个字段：{@code null} 表示清空（货主账号可以不填邮箱与企业微信
 * userid）。不做「传了才改」的部分更新：同一接口同时支持两种语义时，前端无法表达「把这个字段
 * 清空」，而清空邮箱恰好是真实需求。
 *
 * <p>携带原始值，不做清洗：去空白、空串按未填处理的口径与建号一致，属于用例层规则，
 * 放在协议层会让「能改成什么」取决于调用方是不是 HTTP。
 *
 * @param userId      用户标识
 * @param displayName 展示名
 * @param email       邮箱；内部员工必填，货主账号可为 {@code null}
 * @param wecomUserId 企业微信 userid，可为 {@code null}
 */
public record UpdateUserCommand(long userId, String displayName, String email, String wecomUserId) {
}

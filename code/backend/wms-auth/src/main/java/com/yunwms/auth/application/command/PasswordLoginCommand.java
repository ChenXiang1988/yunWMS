package com.yunwms.auth.application.command;

/**
 * 登录名加密码的登录入参。
 *
 * <p>两类主体共用同一条命令：主体类型不由调用方指定，而由后端按登录名查到账号后判定。
 * 让前端传主体类型等于让前端决定用哪套校验规则，货主端就能拿着员工登录名去试仓网端的入口。
 *
 * @param loginName        登录名
 * @param password         明文密码，只在本进程内存中存活，不写日志、不落库
 * @param sourceAddress    来源地址，用于会话审计
 * @param clientIdentifier 客户端标识，用于会话审计；非浏览器客户端可能为空
 */
public record PasswordLoginCommand(
        String loginName,
        String password,
        String sourceAddress,
        String clientIdentifier) {
}

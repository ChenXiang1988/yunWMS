package com.yunwms.web.api;

/**
 * 错误码。
 *
 * <p>用五位整数而非 HTTP 状态码：HTTP 状态只表达「哪一类问题」，业务需要区分到「哪一个问题」。
 * 前三位对齐 HTTP 语义，后两位是同类内的序号，便于人读日志时一眼判断大致方向。
 */
public enum ErrorCode {

    OK(0, "成功"),

    BAD_REQUEST(40000, "请求参数不合法"),
    UNAUTHORIZED(40100, "未登录或登录已失效"),

    /** 登录名不存在与密码错误共用此码：分开会把登录框变成账号枚举接口。 */
    LOGIN_FAILED(40101, "登录名或密码不正确"),
    LOGIN_ACCOUNT_DISABLED(40102, "账号已停用"),
    LOGIN_ACCOUNT_LOCKED(40103, "账号已被锁定，请稍后再试"),

    FORBIDDEN(40300, "无权访问"),
    NOT_FOUND(40400, "资源不存在"),
    CONFLICT(40900, "数据冲突"),

    INTERNAL(50000, "服务器内部错误");

    private final int code;
    private final String defaultMessage;

    ErrorCode(int code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public int code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}

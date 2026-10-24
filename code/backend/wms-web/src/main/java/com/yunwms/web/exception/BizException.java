package com.yunwms.web.exception;

import com.yunwms.web.api.ErrorCode;

/**
 * 业务异常。
 *
 * <p>只有「能被用户看到并据此操作」的失败才抛这个，例如编码重复、状态不允许。
 * 编程错误（空指针、越界）不包装成它，让它们以 500 暴露出来。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message);
    }

    public static BizException conflict(String message) {
        return new BizException(ErrorCode.CONFLICT, message);
    }

    public static BizException badRequest(String message) {
        return new BizException(ErrorCode.BAD_REQUEST, message);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}

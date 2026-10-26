package com.yunwms.web.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一响应体。
 *
 * <p>所有接口都返回这个结构，HTTP 状态码固定为 200，业务结果由 {@code code} 表达。
 * 这样前端只解析一种结构，不必同时判断 HTTP 状态和响应体。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResult<T>(
        @Schema(description = "业务结果码，0 表示成功；非 0 时 data 为 null", example = "0")
        int code,

        @Schema(description = "可直接展示给用户的中文提示；失败时为具体原因，成功时固定为「成功」",
                example = "成功")
        String message,

        @Schema(description = "业务数据；失败时为 null，分页接口为 PageResult 结构")
        T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(ErrorCode.OK.code(), ErrorCode.OK.defaultMessage(), data);
    }

    public static ApiResult<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResult<T> fail(ErrorCode errorCode) {
        return new ApiResult<>(errorCode.code(), errorCode.defaultMessage(), null);
    }

    public static <T> ApiResult<T> fail(ErrorCode errorCode, String message) {
        return new ApiResult<>(errorCode.code(), message, null);
    }

    /** 供服务端判断，不参与序列化；否则响应体里会多出一个前端没预期的 ok 字段。 */
    @JsonIgnore
    public boolean isOk() {
        return code == ErrorCode.OK.code();
    }
}

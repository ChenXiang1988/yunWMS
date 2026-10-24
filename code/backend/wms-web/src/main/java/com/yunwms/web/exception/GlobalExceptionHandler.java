package com.yunwms.web.exception;

import com.yunwms.web.api.ApiResult;
import com.yunwms.web.api.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>约定：业务异常按原样返回错误码；参数问题归到 40000 并带出具体字段；
 * 其余一律落到 50000，且不把堆栈或 SQL 信息返回给前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBiz(BizException ex) {
        log.info("业务异常：{}", ex.getMessage());
        return ApiResult.fail(ex.errorCode(), ex.getMessage());
    }

    /** 字段校验失败。把「哪个字段、错在哪」拼成人能读的一句话，而不是抛一堆平铺信息。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describe)
                .collect(Collectors.joining("；"));
        return ApiResult.fail(ErrorCode.BAD_REQUEST, detail.isEmpty()
                ? ErrorCode.BAD_REQUEST.defaultMessage()
                : detail);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ApiResult<Void> handleUnreadable(Exception ex) {
        log.info("请求体或参数无法解析：{}", ex.getMessage());
        return ApiResult.fail(ErrorCode.BAD_REQUEST, "请求格式不正确");
    }

    /**
     * 路径不存在。
     *
     * <p>Spring Boot 3 把未映射的路径交给静态资源处理器，最终抛 {@link NoResourceFoundException}；
     * 不单独拦下就会被下面的兜底分支当成 500，并打一整条堆栈——拼错一个字母不配拥有 ERROR 日志。
     * 这里用 404 而不是 200：路由是否存在属于传输层事实，不在「业务结果一律 200」的约定范围内。
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResult<Void> handleNoResource(Exception ex) {
        log.debug("路径不存在：{}", ex.getMessage());
        return ApiResult.fail(ErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleUnexpected(Exception ex) {
        // 这里必须记 error 并带堆栈，否则线上问题无从查起
        log.error("未预期的异常", ex);
        return ApiResult.fail(ErrorCode.INTERNAL);
    }

    private String describe(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }
}

package com.yunwms.auth.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.web.api.ApiResult;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 鉴权失败的响应体。
 *
 * <p>401 与 403 必须分开：401 表示「没有登录（或登录已失效）」，前端据此跳登录页；
 * 403 表示「已登录但没有这个权限」，前端应提示无权而不是跳转。两者混用会把用户卡在原地。
 * 其余异常一律按 500 处理，不把内部错误伪装成鉴权失败，否则排查时会丢掉真实原因。
 *
 * <p>「需先改密」同样用 401，但错误码是 40104：状态码负责让前端进入 401 分支，错误码负责区分
 * 「去登录页」与「去改密页」。只给 40100 会把已登录的用户踢回登录页，形成「登录 → 被踢回」的循环。
 *
 * <p>Sa-Token 的过滤器捕获异常后调用本类，并把返回的字符串写进响应体，因此状态码与
 * Content-Type 必须在返回前设置好——{@code SaJakartaServletOperateUtil.writeResult} 只写正文，
 * 不设状态码；这里也不能自己拿 writer 写，否则正文会被写两遍。
 */
@Component
public class RestAuthResponses {

    private final ObjectMapper objectMapper;

    public RestAuthResponses(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 按异常类型设置响应状态与内容类型，返回待写回的 JSON 正文。
     *
     * @param throwable Sa-Token 过滤器透传的异常
     * @return 序列化后的 {@link ApiResult} 正文
     */
    public String failureBody(Throwable throwable) {
        ErrorCode errorCode = toErrorCode(throwable);
        HttpServletResponse response = (HttpServletResponse) SaHolder.getResponse().getSource();
        response.setStatus(httpStatus(errorCode));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return toJson(ApiResult.fail(errorCode));
    }

    private ErrorCode toErrorCode(Throwable throwable) {
        if (throwable instanceof BizException bizException) {
            // 业务异常自带错误码，按取值转发而不是按异常类型猜：强制改密就是用 biz 异常表达拒绝的
            return bizException.errorCode();
        }
        if (throwable instanceof NotLoginException) {
            return ErrorCode.UNAUTHORIZED;
        }
        if (throwable instanceof NotPermissionException || throwable instanceof NotRoleException) {
            return ErrorCode.FORBIDDEN;
        }
        return ErrorCode.INTERNAL;
    }

    /**
     * 错误码到 HTTP 状态码的映射。
     *
     * <p>只有「未登录」与「需先改密」用 401，其余按 403 或 500：把业务拒绝统统写成 401 会让前端
     * 分不清「重新登录」和「这次操作失败」，而把改密写成 200 又会让拦截器放行请求。
     *
     * @param errorCode 错误码
     * @return 该错误码对应的 HTTP 状态码
     */
    static int httpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case UNAUTHORIZED, MUST_CHANGE_PASSWORD -> HttpServletResponse.SC_UNAUTHORIZED;
            case FORBIDDEN -> HttpServletResponse.SC_FORBIDDEN;
            default -> HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        };
    }

    private String toJson(ApiResult<?> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            // 连 ApiResult 都序列化不了，说明响应结构被改坏，属于编码错误；此时宁可抛出去暴露，也不返回半截正文
            throw new IllegalStateException("鉴权失败响应体序列化失败", e);
        }
    }
}

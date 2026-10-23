package com.yunwms.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunwms.common.api.ApiResult;
import com.yunwms.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证与无权限两种失败的响应。
 *
 * <p>两者必须分开：401 表示「你不知道自己该登录」，前端据此跳登录页；
 * 403 表示「你已经登录但没有这个权限」，前端应提示无权而非跳转。
 * 如果都返回 403，前端无法区分，用户会被卡在原地。
 *
 * <p>Spring Security 在关闭 formLogin 与 httpBasic 后默认没有入口点，
 * 会把未认证当成无权限处理（403），所以必须显式提供 entryPoint。
 */
@Component
public class RestAuthHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAuthHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, int httpStatus, ErrorCode code)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResult.fail(code));
    }
}

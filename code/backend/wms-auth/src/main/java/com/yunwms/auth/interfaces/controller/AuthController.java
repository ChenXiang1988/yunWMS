package com.yunwms.auth.interfaces.controller;

import com.yunwms.auth.application.command.PasswordLoginCommand;
import com.yunwms.auth.application.service.PasswordLoginService;
import com.yunwms.auth.interfaces.controller.assembler.LoginResponseAssembler;
import com.yunwms.auth.interfaces.controller.request.LoginRequest;
import com.yunwms.auth.interfaces.controller.response.LoginResponse;
import com.yunwms.web.api.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证入口。
 *
 * <p>两端共用 {@code /api/auth/login}：主体类型由后端按 {@code sys_user.subject_type} 判定，
 * 前端不需要、也不允许指定。
 *
 * <p>来源地址取 TCP 对端而不是 {@code X-Forwarded-For}：后者在未受信代理时可被请求方随意伪造，
 * 记进会话的审计信息就成了假的。将来在应用前置反向代理时，需要在这里显式加上受信代理解析。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final PasswordLoginService passwordLoginService;
    private final LoginResponseAssembler loginResponseAssembler;

    public AuthController(PasswordLoginService passwordLoginService,
                          LoginResponseAssembler loginResponseAssembler) {
        this.passwordLoginService = passwordLoginService;
        this.loginResponseAssembler = loginResponseAssembler;
    }

    /**
     * 登录名加密码登录，签发会话令牌。
     *
     * @param request     登录请求
     * @param httpRequest 原始请求，用于取来源地址与客户端标识
     * @return 令牌与主体信息；凭据错误、账号停用、账号锁定通过业务错误码返回
     */
    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                         HttpServletRequest httpRequest) {
        PasswordLoginCommand command = new PasswordLoginCommand(
                request.loginName(),
                request.password(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));
        return ApiResult.ok(loginResponseAssembler.toResponse(passwordLoginService.login(command)));
    }
}

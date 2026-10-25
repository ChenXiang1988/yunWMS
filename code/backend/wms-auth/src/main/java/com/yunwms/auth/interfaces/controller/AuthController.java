package com.yunwms.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import com.yunwms.auth.application.command.ChangePasswordCommand;
import com.yunwms.auth.application.command.PasswordLoginCommand;
import com.yunwms.auth.application.service.PasswordChangeService;
import com.yunwms.auth.application.service.PasswordLoginService;
import com.yunwms.auth.interfaces.controller.assembler.LoginResponseAssembler;
import com.yunwms.auth.interfaces.controller.request.ChangePasswordRequest;
import com.yunwms.auth.interfaces.controller.request.LoginRequest;
import com.yunwms.auth.interfaces.controller.response.LoginResponse;
import com.yunwms.web.api.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final PasswordChangeService passwordChangeService;
    private final LoginResponseAssembler loginResponseAssembler;

    public AuthController(PasswordLoginService passwordLoginService,
                          PasswordChangeService passwordChangeService,
                          LoginResponseAssembler loginResponseAssembler) {
        this.passwordLoginService = passwordLoginService;
        this.passwordChangeService = passwordChangeService;
        this.loginResponseAssembler = loginResponseAssembler;
    }

    /**
     * 登录名加密码登录，签发会话令牌。
     *
     * <p>免鉴权：本接口是拿第一张令牌的唯一入口，要求登录会形成死循环。路径同时列在
     * {@code SaTokenConfig} 的放行清单里，{@code @SaIgnore} 在这里的额外意义是让「本方法不需要鉴权」
     * 在代码里可见——只写在过滤器清单里时，读接口的人看不到它为什么没有被鉴权。
     *
     * @param request     登录请求
     * @param httpRequest 原始请求，用于取来源地址与客户端标识
     * @return 令牌与主体信息；凭据错误、账号停用、账号锁定通过业务错误码返回
     */
    @SaIgnore
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

    /**
     * 修改当前登录用户的密码。
     *
     * <p>路径是待改密状态下唯一的放行入口（见 {@code MustChangePasswordGate}）：用初始密码登录后，
     * 用户必须先走这里，其他业务接口一律返回 40104。路径写错会让账号既改不了密也出不去。
     *
     * <p>只要求登录，不要求权限点：这是每个人维护自己凭据的入口，给它加权限点等于要求
     * 「改自己的密码之前先拥有某个业务权限」，新账号会在第一步就被卡死。
     *
     * @param request 旧密码与新密码
     * @return 成功时 {@code code=0}，无业务数据；旧密码错误、新密码不合规通过业务错误码返回
     */
    @SaCheckLogin
    @PutMapping("/password")
    public ApiResult<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        passwordChangeService.change(new ChangePasswordCommand(request.oldPassword(), request.newPassword()));
        return ApiResult.ok();
    }
}

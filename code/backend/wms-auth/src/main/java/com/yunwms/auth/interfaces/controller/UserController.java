package com.yunwms.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yunwms.auth.application.command.CreateUserCommand;
import com.yunwms.auth.application.service.UserCreationService;
import com.yunwms.auth.interfaces.controller.assembler.UserResponseAssembler;
import com.yunwms.auth.interfaces.controller.request.CreateUserRequest;
import com.yunwms.auth.interfaces.controller.response.CreatedUserResponse;
import com.yunwms.web.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端的用户管理入口。
 *
 * <p>路径前缀 {@code /api/console} 表示仓网端：货主端（{@code /api/portal}）不提供建号能力，
 * 它的账号由管理员在仓网端开通。
 *
 * <p>本类当前只做建号。列表、详情、编辑、启停与重置密码属于后续任务，不在这里预留空方法：
 * 一个返回 501 或空列表的占位实现，比没有这个接口更难解释。
 *
 * <p>建号是管理动作，除「已登录」与「需先改密」两道拦截外还要求权限点
 * {@code system:user:create}：只判登录不够，任何员工账号都能建号等于没有权限体系。
 * 注解由 {@code SaTokenAnnotationConfig} 注册的拦截器消费，权限点本身在 {@code V025} 里定义。
 */
@RestController
@RequestMapping("/api/console/users")
public class UserController {

    private final UserCreationService userCreationService;
    private final UserResponseAssembler userResponseAssembler;

    public UserController(UserCreationService userCreationService,
                         UserResponseAssembler userResponseAssembler) {
        this.userCreationService = userCreationService;
        this.userResponseAssembler = userResponseAssembler;
    }

    /**
     * 创建用户账号。
     *
     * <p>新账号以初始密码启用，{@code mustChangePassword} 为 {@code true}：首次登录只能改密，
     * 其他业务接口会被拦截。
     *
     * @param request 建号请求
     * @return 新账号的标识与登录名；主体与货主不匹配、内部员工缺邮箱、密码不合规、
     *         登录名或邮箱已被占用通过业务错误码返回
     */
    @SaCheckPermission("system:user:create")
    @PostMapping
    public ApiResult<CreatedUserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        CreateUserCommand command = new CreateUserCommand(
                request.loginName(),
                request.displayName(),
                request.subjectType(),
                request.ownerId(),
                request.email(),
                request.wecomUserId(),
                request.initialPassword(),
                request.roleIds());
        return ApiResult.ok(userResponseAssembler.toResponse(userCreationService.create(command)));
    }
}

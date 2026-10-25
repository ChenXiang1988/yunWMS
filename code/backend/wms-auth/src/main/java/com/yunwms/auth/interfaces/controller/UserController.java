package com.yunwms.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yunwms.auth.application.command.AssignRolesCommand;
import com.yunwms.auth.application.command.CreateUserCommand;
import com.yunwms.auth.application.query.UserQueryService;
import com.yunwms.auth.application.service.UserCreationService;
import com.yunwms.auth.application.service.UserRoleAssignmentService;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.interfaces.controller.assembler.UserResponseAssembler;
import com.yunwms.auth.interfaces.controller.request.AssignRolesRequest;
import com.yunwms.auth.interfaces.controller.request.CreateUserRequest;
import com.yunwms.auth.interfaces.controller.response.CreatedUserResponse;
import com.yunwms.auth.interfaces.controller.response.UserDetailResponse;
import com.yunwms.auth.interfaces.controller.response.UserListItemResponse;
import com.yunwms.web.api.ApiResult;
import com.yunwms.web.api.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端的用户管理入口。
 *
 * <p>路径前缀 {@code /api/console} 表示仓网端：货主端（{@code /api/portal}）不提供建号能力，
 * 它的账号由管理员在仓网端开通。
 *
 * <p>本类做建号、列表、详情与分配角色。编辑基本信息、启停与重置密码属于 2.1c，
 * 数据范围覆盖属于 2.3/2.4，都不在这里预留空方法：一个返回 501 的占位实现，比没有这个接口更难解释。
 *
 * <p>权限点分三个：列表与详情共用 {@code system:user:list}（看得到列表的人本来就能从列表点进详情，
 * 拆成两个权限点只会多一次配置而没有边界收益）；建号用 {@code system:user:create}；
 * 分配角色用 {@code system:user:update}——角色决定这个人能做什么，能改别人的角色等于能改别人的权限，
 * 因此它不能借用建号权限。三个权限点由 {@code V025} 与 {@code V027} 定义，
 * 注解由 {@code SaTokenAnnotationConfig} 注册的拦截器消费。
 */
@RestController
@RequestMapping("/api/console/users")
public class UserController {

    private final UserCreationService userCreationService;
    private final UserQueryService userQueryService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final UserResponseAssembler userResponseAssembler;

    public UserController(UserCreationService userCreationService,
                          UserQueryService userQueryService,
                          UserRoleAssignmentService userRoleAssignmentService,
                          UserResponseAssembler userResponseAssembler) {
        this.userCreationService = userCreationService;
        this.userQueryService = userQueryService;
        this.userRoleAssignmentService = userRoleAssignmentService;
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

    /**
     * 分页查询用户。
     *
     * <p>每行带出角色编码与所属货主名称，由固定条数的查询取回，前端不必再逐个用户查一次。
     *
     * @param pageNo      页码，从 1 开始；省略时取 1
     * @param pageSize    每页条数，上限 200；省略时取 20
     * @param subjectType 主体类型；省略表示全部主体
     * @param status      账号状态；省略表示全部状态
     * @param keyword     按登录名或姓名模糊匹配；省略或全空白表示不筛选
     * @return 一页用户及总数；页码或每页条数越界时返回 40000
     */
    @SaCheckPermission("system:user:list")
    @GetMapping
    public ApiResult<PageResult<UserListItemResponse>> page(@RequestParam(required = false) Integer pageNo,
                                                            @RequestParam(required = false) Integer pageSize,
                                                            @RequestParam(required = false) SubjectType subjectType,
                                                            @RequestParam(required = false) UserStatus status,
                                                            @RequestParam(required = false) String keyword) {
        return ApiResult.ok(userResponseAssembler.toPageResponse(
                userQueryService.page(pageNo, pageSize, subjectType, status, keyword)));
    }

    /**
     * 查询用户详情。
     *
     * @param id 用户标识
     * @return 用户详情，含邮箱、企业微信 userid、数据范围覆盖值与角色标识；用户不存在时返回 40400
     */
    @SaCheckPermission("system:user:list")
    @GetMapping("/{id}")
    public ApiResult<UserDetailResponse> detail(@PathVariable long id) {
        return ApiResult.ok(userResponseAssembler.toDetailResponse(userQueryService.detail(id)));
    }

    /**
     * 给用户分配角色。
     *
     * <p><b>替换语义</b>：提交的角色集合就是这个人之后拥有的全部角色，空数组表示移除全部角色。
     * 先删后插在同一个事务里完成，不存在「删掉了但没插进去」的中间态。
     *
     * @param id      用户标识
     * @param request 替换后的角色标识集合
     * @return 成功无数据；用户不存在（40400）、角色不存在（40000）、
     *         把自己最后的初始角色换掉（40900）通过业务错误码返回
     */
    @SaCheckPermission("system:user:update")
    @PutMapping("/{id}/roles")
    public ApiResult<Void> assignRoles(@PathVariable long id, @Valid @RequestBody AssignRolesRequest request) {
        userRoleAssignmentService.assign(new AssignRolesCommand(id, request.roleIds()));
        // 不回带新的角色列表：前端保存后本就持有它提交的集合，原样回显没有信息量；
        // 需要最新数据（例如角色被并发改过）时重新取详情
        return ApiResult.ok();
    }
}

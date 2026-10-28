package com.yunwms.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yunwms.auth.application.command.AssignRolesCommand;
import com.yunwms.auth.application.command.ChangeUserStatusCommand;
import com.yunwms.auth.application.command.ConfigureUserDataScopeCommand;
import com.yunwms.auth.application.command.CreateUserCommand;
import com.yunwms.auth.application.command.ResetUserPasswordCommand;
import com.yunwms.auth.application.command.UpdateUserCommand;
import com.yunwms.auth.application.query.UserQueryService;
import com.yunwms.auth.application.service.UserCreationService;
import com.yunwms.auth.application.service.UserDataScopeConfigurationService;
import com.yunwms.auth.application.service.UserPasswordResetService;
import com.yunwms.auth.application.service.UserRoleAssignmentService;
import com.yunwms.auth.application.service.UserSessionKickService;
import com.yunwms.auth.application.service.UserStatusChangeService;
import com.yunwms.auth.application.service.UserUpdateService;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.domain.model.UserStatus;
import com.yunwms.auth.interfaces.controller.assembler.UserResponseAssembler;
import com.yunwms.auth.interfaces.controller.request.AssignRolesRequest;
import com.yunwms.auth.interfaces.controller.request.ChangeUserStatusRequest;
import com.yunwms.auth.interfaces.controller.request.CreateUserRequest;
import com.yunwms.auth.interfaces.controller.request.DataScopeRequest;
import com.yunwms.auth.interfaces.controller.request.RequestIds;
import com.yunwms.auth.interfaces.controller.request.ResetUserPasswordRequest;
import com.yunwms.auth.interfaces.controller.request.UpdateUserRequest;
import com.yunwms.auth.interfaces.controller.response.CreatedUserResponse;
import com.yunwms.auth.interfaces.controller.response.UserDetailResponse;
import com.yunwms.auth.interfaces.controller.response.UserListItemResponse;
import com.yunwms.web.api.ApiResult;
import com.yunwms.web.api.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * <p>本类做建号、列表、详情、编辑基本信息、配置数据范围覆盖、启停、踢下线、重置密码与分配角色。
 * 数据范围走独立端点 {@code PUT /{id}/data-scope}，不并入编辑基本信息：合在一起之后
 * 「只改展示名」的请求必须把范围也提交，漏传就等于把某个人的范围清空。
 *
 * <p>权限点分五个：列表与详情共用 {@code system:user:list}（看得到列表的人本来就能从列表点进详情，
 * 拆成两个权限点只会多一次配置而没有边界收益）；建号用 {@code system:user:create}；
 * 编辑基本信息、启停与分配角色共用 {@code system:user:update}——三者改的都是「这个账号本身」，
 * 其中分配角色最重（角色决定这个人能做什么，能改别人的角色等于能改别人的权限），
 * 因此它不能借用建号权限；重置密码用 {@code system:user:reset}，它比编辑更进一步
 * （拿到它等于能替任何人设置凭据），所以必须单独受控；踢下线用 {@code system:user:kick}，
 * 它是安全动作——能给员工改备注的角色，不一定该有强制把人赶下线的能力。
 * 五个权限点由 {@code V025}、{@code V027}、{@code V028} 与 {@code V041} 定义，
 * 注解由 {@code SaTokenAnnotationConfig} 注册的拦截器消费。
 */
@Tag(name = "用户管理", description = "仓网端的账号管理：建号、查询、编辑基本信息、启停、踢下线、重置密码与分配角色。"
        + "货主端没有建号能力，货主账号由管理员在这里开通。全部接口都要求对应权限点，"
        + "只登录但没有权限点的账号会拿到 403。")
@RestController
@RequestMapping("/api/console/users")
public class UserController {

    private final UserCreationService userCreationService;
    private final UserQueryService userQueryService;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final UserUpdateService userUpdateService;
    private final UserStatusChangeService userStatusChangeService;
    private final UserSessionKickService userSessionKickService;
    private final UserPasswordResetService userPasswordResetService;
    private final UserDataScopeConfigurationService userDataScopeConfigurationService;
    private final UserResponseAssembler userResponseAssembler;

    public UserController(UserCreationService userCreationService,
                          UserQueryService userQueryService,
                          UserRoleAssignmentService userRoleAssignmentService,
                          UserUpdateService userUpdateService,
                          UserStatusChangeService userStatusChangeService,
                          UserSessionKickService userSessionKickService,
                          UserPasswordResetService userPasswordResetService,
                          UserDataScopeConfigurationService userDataScopeConfigurationService,
                          UserResponseAssembler userResponseAssembler) {
        this.userCreationService = userCreationService;
        this.userQueryService = userQueryService;
        this.userRoleAssignmentService = userRoleAssignmentService;
        this.userUpdateService = userUpdateService;
        this.userStatusChangeService = userStatusChangeService;
        this.userSessionKickService = userSessionKickService;
        this.userPasswordResetService = userPasswordResetService;
        this.userDataScopeConfigurationService = userDataScopeConfigurationService;
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
     *         登录名或邮箱已被占用通过业务错误码返回；{@code ownerId} 或 {@code roleIds}
     *         不是十进制整数的字符串时返回 40000
     */
    @Operation(summary = "创建用户账号",
            description = "由管理员开通一个账号，供内部员工或货主用户登录。新账号一律启用，初始密码由管理员给出，"
                    + "并标记为「首次登录必须改密」：登录后除改密外调不到任何业务接口。主体与货主必须匹配——"
                    + "货主账号要带 ownerId，内部员工必须不传；内部员工还必须留邮箱，用于自助找回密码。"
                    + "登录名与邮箱都全局唯一，被占用分别返回 40901、40903，密码不满足策略返回 40000。"
                    + "响应只回带标识与登录名，口令不回显。")
    @SaCheckPermission("system:user:create")
    @PostMapping
    public ApiResult<CreatedUserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        // 请求体里的标识按契约以字符串传输（见 docs/api-convention.md 第五节），
        // 在这里解析成 Long：字符串是 HTTP 协议事实，不带进用例层
        CreateUserCommand command = new CreateUserCommand(
                request.loginName(),
                request.displayName(),
                request.subjectType(),
                RequestIds.optionalId(request.ownerId(), "ownerId"),
                request.email(),
                request.wecomUserId(),
                request.initialPassword(),
                RequestIds.optionalIds(request.roleIds(), "roleIds"));
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
    @Operation(summary = "分页查询用户账号",
            description = "按主体类型与状态筛选账号，支持按登录名或姓名模糊匹配，返回每行的角色编码与所属货主名称，"
                    + "供管理端列表页使用。角色与货主由固定条数的查询随行带出，前端不必再逐个用户查一次。"
                    + "所有筛选条件都可省略，全部省略即返回全量分页；页码从 1 开始，每页条数上限 200，"
                    + "越界返回 40000 而不是静默截断。")
    @SaCheckPermission("system:user:list")
    @GetMapping
    public ApiResult<PageResult<UserListItemResponse>> page(
            @Parameter(description = "页码，从 1 开始；省略时取 1")
            @RequestParam(required = false) Integer pageNo,
            @Parameter(description = "每页条数，上限 200；省略时取 20，超出上限返回 40000")
            @RequestParam(required = false) Integer pageSize,
            @Parameter(description = "主体类型：STAFF 内部员工，OWNER 货主用户；省略表示全部主体")
            @RequestParam(required = false) SubjectType subjectType,
            @Parameter(description = "账号状态：ACTIVE 启用，DISABLED 停用；省略表示全部状态")
            @RequestParam(required = false) UserStatus status,
            @Parameter(description = "按登录名或展示名模糊匹配的关键词；省略或全空白表示不筛选")
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
    @Operation(summary = "查询用户详情",
            description = "取单个账号的完整信息：列表行里已有的字段之外，再带出邮箱、企业微信 userid、用户级数据范围"
                    + "覆盖值与用于回填编辑表单的角色标识（roleIds）。角色编码与角色标识两种表示同时返回，"
                    + "前端保存后刷新详情时不必再发一次列表请求。账号不存在或已被逻辑删除返回 40400。")
    @SaCheckPermission("system:user:list")
    @GetMapping("/{id}")
    public ApiResult<UserDetailResponse> detail(
            @Parameter(description = "用户标识，取自列表或详情响应里的 id")
            @PathVariable long id) {
        return ApiResult.ok(userResponseAssembler.toDetailResponse(userQueryService.detail(id)));
    }

    /**
     * 编辑用户基本信息。
     *
     * <p><b>全量替换</b>展示名、邮箱与企业微信 userid：字段传 {@code null} 表示清空。
     * 登录名、主体类型与所属货主不在请求体里，多传会被 {@code FAIL_ON_UNKNOWN_PROPERTIES} 拒绝。
     *
     * <p>改完之后不回带详情：前端提交的就是它屏幕上那份数据，原样回显没有信息量；
     * 需要最新值（例如邮箱被并发改过）时重新取详情。
     *
     * @param id      用户标识
     * @param request 编辑请求体
     * @return 成功无数据；用户不存在（40400）、展示名为空或内部员工清空邮箱（40000）、
     *         邮箱已被占用（40903）通过业务错误码返回
     */
    @Operation(summary = "编辑用户基本信息",
            description = "全量替换展示名、邮箱与企业微信 userid：字段传 null 表示清空，不传表示按 null 处理，"
                    + "即调用方必须提交自己希望保留的全部值。登录名、主体类型与所属货主不在可编辑范围内——"
                    + "它们决定账号身份与数据归属，改动属于另一件事（换账号或迁移归属），因此请求体里没有这些字段，"
                    + "多传会被拒绝而不是静默忽略。内部员工清空邮箱返回 40000，邮箱被他人占用返回 40903，"
                    + "账号不存在返回 40400。成功后不回带详情，需要最新值时重新取。")
    @SaCheckPermission("system:user:update")
    @PutMapping("/{id}")
    public ApiResult<Void> update(
            @Parameter(description = "用户标识，取自列表或详情响应里的 id")
            @PathVariable long id,
            @Valid @RequestBody UpdateUserRequest request) {
        userUpdateService.update(new UpdateUserCommand(
                id, request.displayName(), request.email(), request.wecomUserId()));
        return ApiResult.ok();
    }

    /**
     * 配置或清除用户级数据范围覆盖。
     *
     * <p><b>整批替换</b>，两种意图分开表达：
     * <ul>
     *   <li>{@code dataScopeType} 非空：写入覆盖，生效范围只按它算，<b>不再回退角色</b>；</li>
     *   <li>{@code dataScopeType} 为 {@code null}：清除覆盖，回落到角色并集。</li>
     * </ul>
     * 合并成一种会让「管理员清不掉某个人的权限」或「清除没生效」二者必居其一。
     *
     * <p>独立成一条端点而不是并入 {@code PUT /{id}}：资料更新的契约不变，避免「编辑资料漏传字段
     * 就把数据范围清空」。
     *
     * @param id      用户标识
     * @param request 目标类型与明细；类型为 {@code null} 表示清除覆盖
     * @return 成功无数据；用户不存在（40400）、账号是货主账号、类型与明细矛盾、
     *         明细里的货主不存在或未启用、清除时却提交了明细、{@code ownerIds} 含非十进制整数的字符串
     *         （40000）通过业务错误码返回
     */
    @Operation(summary = "配置用户级数据范围覆盖（整批替换）",
            description = "把账号的数据范围覆盖整体替换为提交的这一组：dataScopeType 与 ownerIds 就是它此后的"
                    + "全部覆盖，重复提交同一组是幂等的。dataScopeType 传 null 表示清除覆盖、回落到角色默认，"
                    + "此时不能提交 ownerIds（40000）。ALL 与 ASSIGNED 是规则而不是名单，不接受 ownerIds；"
                    + "CUSTOM 允许空数组（效果是看不见任何货主），其货主必须存在且启用。货主账号的范围由所属货主"
                    + "强制决定，不接受覆盖配置（40000）。账号不存在返回 40400。成功后不回带范围，"
                    + "需要最新值时取详情。")
    @SaCheckPermission("system:user:update")
    @PutMapping("/{id}/data-scope")
    public ApiResult<Void> configureDataScope(
            @Parameter(description = "用户标识，取自列表或详情响应里的 id")
            @PathVariable long id,
            @Valid @RequestBody DataScopeRequest request) {
        userDataScopeConfigurationService.configure(new ConfigureUserDataScopeCommand(
                id, request.dataScopeType(), RequestIds.optionalIds(request.ownerIds(), "ownerIds")));
        // 不回带范围：前端提交的就是它屏幕上那份数据；需要最新值时重新取详情
        return ApiResult.ok();
    }

    /**
     * 启用或停用用户。
     *
     * <p>停用会让该账号的既有会话立即失效、且无法再次登录（规格「停用用户」）。
     * 目标状态与当前状态相同时返回成功，不报错。
     *
     * <p>停用方向有三条业务保护：不能停用自己、不能停用最后一个可用超管、名下仍有货主的员工必须先转交。
     *
     * @param id      用户标识
     * @param request 目标状态
     * @return 成功无数据；用户不存在（40400）、停用自己、停用最后一个可用超管、停用的员工仍担任
     *         货主负责人（40900）通过业务错误码返回
     */
    @Operation(summary = "启用或停用用户账号",
            description = "切换账号状态。停用立即生效：该账号的既有会话随即失效，且无法再次登录，"
                    + "不必等令牌过期。目标状态与当前状态相同时返回成功，不报错——重复提交同一次启停是正常的重放。"
                    + "三条业务保护（均返回 40900）：不能停用自己；不能停用最后一个可用的超级管理员，"
                    + "否则系统会失去唯一能配置权限的账号；仍担任货主负责人的员工不能停用，"
                    + "必须先转交名下货主，否则货主侧会挂着一个永远停用的负责人。账号不存在返回 40400。")
    @SaCheckPermission("system:user:update")
    @PutMapping("/{id}/status")
    public ApiResult<Void> changeStatus(
            @Parameter(description = "用户标识，取自列表或详情响应里的 id")
            @PathVariable long id,
            @Valid @RequestBody ChangeUserStatusRequest request) {
        userStatusChangeService.change(new ChangeUserStatusCommand(id, request.status()));
        return ApiResult.ok();
    }

    /**
     * 使该用户的全部会话立即失效（踢下线）。
     *
     * <p>用 {@code DELETE} 而不是 {@code PUT}：被删掉的是该用户手上的全部会话，而不是把账号的某个
     * 属性改成新值。{@code PUT /{id}/status} 与 {@code PUT /{id}/password} 改的是账号本身，
     * 重复提交会得到同一个状态；踢下线没有「当前值」可言，只有「发生过」。
     *
     * <p>允许踢自己：只会把发起本次请求的会话一起打掉，权限与角色都在库里，重新登录即可。
     * 与「不能停用自己」不是同一条判断——那条防的是操作完就没有恢复入口。判定写在
     * {@link UserSessionKickService} 的类说明里。
     *
     * @param id 用户标识，可以是当前登录账号
     * @return 成功无数据；用户不存在或已逻辑删除（40400）通过业务错误码返回
     */
    @Operation(summary = "踢下线（使该用户的全部会话立即失效）",
            description = "让指定账号手里的全部令牌立刻作废：下一次请求即返回 40100，需要重新登录。"
                    + "用于账号疑似被盗、人员交接这类必须立刻止血的场景。它不改变账号状态，"
                    + "被踢的人重新登录即可继续使用；要禁止登录请停用账号。允许踢自己——"
                    + "只会把自己这次会话一起打掉，不丢任何权限。账号不存在返回 40400："
                    + "对不存在的标识执行失效本来会静默成功，那样接口回 200 而实际什么都没发生。")
    @SaCheckPermission("system:user:kick")
    @DeleteMapping("/{id}/sessions")
    public ApiResult<Void> kickSessions(
            @Parameter(description = "用户标识，取自列表或详情响应里的 id；可以是当前登录账号")
            @PathVariable long id) {
        userSessionKickService.kick(id);
        // 不回带任何会话信息：令牌值的唯一去处是它自己的持有者，响应里出现一次就是多一处泄露面
        return ApiResult.ok();
    }

    /**
     * 重置用户密码。
     *
     * <p>管理员不验旧密码，因此本接口不能作用于调用者自己：那会变成一条绕过旧密码校验的通道，
     * 自己改密请走 {@code PUT /api/auth/password}。重置成功后该账号的全部会话立即失效，
     * 且下次登录会被强制改密。
     *
     * @param id      用户标识
     * @param request 新密码
     * @return 成功无数据；用户不存在（40400）、重置自己的密码（40900）、
     *         新密码不满足密码策略（40000，一次返回全部违规项）通过业务错误码返回
     */
    @Operation(summary = "重置他人密码",
            description = "管理员为指定账号设置新密码，不校验旧密码，用于员工忘记密码的情形。"
                    + "正因为不验旧密码，它不能作用于调用者自己（返回 40900）——否则这就是一条绕过旧密码校验的通道，"
                    + "改自己的密码请走 PUT /api/auth/password。成功后该账号的全部会话立即失效，"
                    + "并重新标记为「首次登录必须改密」，下次登录只能先改密。"
                    + "新密码不满足策略时一次返回全部违规项（40000），账号不存在返回 40400；"
                    + "新密码不回显，响应无业务数据。")
    @SaCheckPermission("system:user:reset")
    @PutMapping("/{id}/password")
    public ApiResult<Void> resetPassword(
            @Parameter(description = "被重置密码的用户标识，不能是当前登录账号")
            @PathVariable long id,
            @Valid @RequestBody ResetUserPasswordRequest request) {
        userPasswordResetService.reset(new ResetUserPasswordCommand(id, request.newPassword()));
        // 不回带临时密码：它由管理员自己给出，回显只会让它多出现在一个地方（响应体与日志）
        return ApiResult.ok();
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
     *         把自己最后的初始角色换掉（40900）、{@code roleIds} 含非十进制整数的字符串（40000）
     *         通过业务错误码返回
     */
    @Operation(summary = "分配用户角色（整批替换）",
            description = "把账号的角色集合整体替换为提交的这一组：请求体里的 roleIds 就是这个人之后拥有的全部角色，"
                    + "空数组表示移除全部角色。先删后插在同一个事务里完成，不会出现「删掉了但没插进去」的中间态，"
                    + "因此重复提交同一组角色是幂等的。这是唯一会改变「谁能做什么」的入口，权限点不可与其他写操作共用。"
                    + "被拒绝的情形：用户不存在（40400）、请求里有不存在或已删除的角色（40000）、"
                    + "操作人把自己替换成不含内置角色的组合（40900，否则系统可能不再有能配置权限的账号）。"
                    + "成功后不回带角色列表，需要最新值时重新取详情。")
    @SaCheckPermission("system:user:update")
    @PutMapping("/{id}/roles")
    public ApiResult<Void> assignRoles(
            @Parameter(description = "用户标识，取自列表或详情响应里的 id")
            @PathVariable long id,
            @Valid @RequestBody AssignRolesRequest request) {
        // 元素按契约以字符串传输，解析与建号同一口径：非法值一律 40000 并带出字段名
        userRoleAssignmentService.assign(
                new AssignRolesCommand(id, RequestIds.optionalIds(request.roleIds(), "roleIds")));
        // 不回带新的角色列表：前端保存后本就持有它提交的集合，原样回显没有信息量；
        // 需要最新数据（例如角色被并发改过）时重新取详情
        return ApiResult.ok();
    }
}

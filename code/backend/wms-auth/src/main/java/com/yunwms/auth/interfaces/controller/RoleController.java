package com.yunwms.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yunwms.auth.application.command.ChangeRoleStatusCommand;
import com.yunwms.auth.application.command.CreateRoleCommand;
import com.yunwms.auth.application.command.UpdateRoleCommand;
import com.yunwms.auth.application.query.RoleQueryService;
import com.yunwms.auth.application.service.RoleCreationService;
import com.yunwms.auth.application.service.RoleModificationService;
import com.yunwms.auth.domain.model.RoleStatus;
import com.yunwms.auth.interfaces.controller.assembler.RoleResponseAssembler;
import com.yunwms.auth.interfaces.controller.request.ChangeRoleStatusRequest;
import com.yunwms.auth.interfaces.controller.request.CreateRoleRequest;
import com.yunwms.auth.interfaces.controller.request.UpdateRoleRequest;
import com.yunwms.auth.interfaces.controller.response.CreatedRoleResponse;
import com.yunwms.auth.interfaces.controller.response.RoleDetailResponse;
import com.yunwms.auth.interfaces.controller.response.RoleListItemResponse;
import com.yunwms.web.api.ApiResult;
import com.yunwms.web.api.PageResult;
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
 * 管理端的角色管理入口。
 *
 * <p>路径前缀 {@code /api/console} 表示仓网端：货主端（{@code /api/portal}）没有角色管理能力，
 * 它的权限由管理员在仓网端配置。
 *
 * <p>本类做角色的建、读、改、启停、删：角色管理在这里闭环。
 *
 * <p>全部接口都要求权限点而不是只要求登录：角色是授权数据的来源，能改角色就等于能给自己
 * 加权限。列表与详情共用 {@code system:role:list}——看得到角色列表的人本来就能从列表点进详情，
 * 拆成两个权限点只会多一次配置而没有边界收益。编辑与启停共用 {@code system:role:update}：
 * 两者改的都是「这个角色本身」，其中启停是更轻的一个（随时可以改回）。
 * 删除独立成 {@code system:role:delete}，因为角色删除后从管理界面消失，后果比改名重。
 * 四个权限点由 {@code V026} 与 {@code V029} 定义，注解由 {@code SaTokenAnnotationConfig}
 * 注册的拦截器消费。
 */
@RestController
@RequestMapping("/api/console/roles")
public class RoleController {

    private final RoleCreationService roleCreationService;
    private final RoleQueryService roleQueryService;
    private final RoleModificationService roleModificationService;
    private final RoleResponseAssembler roleResponseAssembler;

    public RoleController(RoleCreationService roleCreationService,
                          RoleQueryService roleQueryService,
                          RoleModificationService roleModificationService,
                          RoleResponseAssembler roleResponseAssembler) {
        this.roleCreationService = roleCreationService;
        this.roleQueryService = roleQueryService;
        this.roleModificationService = roleModificationService;
        this.roleResponseAssembler = roleResponseAssembler;
    }

    /**
     * 创建角色。
     *
     * <p>新角色以启用状态落库，{@code builtin} 恒为 {@code false}，数据范围固定 {@code CUSTOM}：
     * 三者都不是请求参数，理由见 {@code NewRole}。
     *
     * @param request 建角色请求
     * @return 新角色的标识与编码；编码格式不合规、编码已被占用、勾选的菜单或权限点不存在
     *         通过业务错误码返回
     */
    @SaCheckPermission("system:role:create")
    @PostMapping
    public ApiResult<CreatedRoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        CreateRoleCommand command = new CreateRoleCommand(
                request.code(),
                request.name(),
                request.remark(),
                request.menuIds());
        return ApiResult.ok(roleResponseAssembler.toCreatedResponse(roleCreationService.create(command)));
    }

    /**
     * 分页查询角色。
     *
     * <p>每行带出占用人数与权限点数量，由同一条 SQL 聚合，前端不必再逐个角色查一次。
     *
     * @param pageNo   页码，从 1 开始；省略时取 1
     * @param pageSize 每页条数，上限 200；省略时取 20
     * @param keyword  按编码或名称模糊匹配；省略或全空白表示不筛选
     * @param status   角色状态；省略表示全部状态
     * @return 一页角色及总数；页码或每页条数越界时返回 40000
     */
    @SaCheckPermission("system:role:list")
    @GetMapping
    public ApiResult<PageResult<RoleListItemResponse>> page(@RequestParam(required = false) Integer pageNo,
                                                            @RequestParam(required = false) Integer pageSize,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) RoleStatus status) {
        return ApiResult.ok(roleResponseAssembler.toPageResponse(
                roleQueryService.page(pageNo, pageSize, keyword, status)));
    }

    /**
     * 查询角色详情。
     *
     * @param id 角色标识
     * @return 角色摘要与其已勾选的菜单及权限点标识；角色不存在时返回 40400
     */
    @SaCheckPermission("system:role:list")
    @GetMapping("/{id}")
    public ApiResult<RoleDetailResponse> detail(@PathVariable long id) {
        return ApiResult.ok(roleResponseAssembler.toDetailResponse(roleQueryService.detail(id)));
    }

    /**
     * 编辑角色。
     *
     * <p><b>全量替换</b>名称、备注与授权：{@code menuIds} 提交的就是这个角色之后拥有的全部权限点，
     * 空数组表示清空全部授权，两者在同一个事务里落库。编码不在请求体里，多传会被
     * {@code FAIL_ON_UNKNOWN_PROPERTIES} 拒绝。
     *
     * <p>改完之后不回带详情：前端提交的就是它屏幕上那份数据，原样回显没有信息量；
     * 需要最新值（例如授权被并发改过）时重新取详情。
     *
     * @param id      角色标识
     * @param request 编辑请求体
     * @return 成功无数据；角色不存在（40400）、名称为空或菜单不存在（40000）、
     *         内置角色（40900）通过业务错误码返回
     */
    @SaCheckPermission("system:role:update")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable long id, @Valid @RequestBody UpdateRoleRequest request) {
        roleModificationService.update(new UpdateRoleCommand(
                id, request.name(), request.remark(), request.menuIds()));
        return ApiResult.ok();
    }

    /**
     * 启用或停用角色。
     *
     * <p>停用立即生效：鉴权侧每次请求都按角色状态过滤，持有该角色的用户下一次请求就不再有
     * 它带来的权限点，不必等会话过期。目标状态与当前状态相同时返回成功，不报错。
     *
     * <p>内置角色不可停用：停完之后没有任何账号能配置权限，而界面上没有恢复入口。
     *
     * @param id      角色标识
     * @param request 目标状态
     * @return 成功无数据；角色不存在（40400）、内置角色（40900）通过业务错误码返回
     */
    @SaCheckPermission("system:role:update")
    @PutMapping("/{id}/status")
    public ApiResult<Void> changeStatus(@PathVariable long id,
                                        @Valid @RequestBody ChangeRoleStatusRequest request) {
        roleModificationService.changeStatus(new ChangeRoleStatusCommand(id, request.status()));
        return ApiResult.ok();
    }

    /**
     * 删除角色。
     *
     * <p>软删除：库里留下 {@code deleted_at}，与账号的删除模式一致，将来审计日志（2.8）里的
     * 角色编码才对得上真实对象；列表与详情按 {@code deleted_at IS NULL} 过滤，删完即查不到。
     *
     * <p>仍被用户占用的角色一律拒绝并在提示里带出占用人数：{@code sys_user_role.role_id} 是
     * {@code ON DELETE CASCADE}，少了这道校验，删除会静默带走关联记录、不报任何错，
     * 那些用户会静默失去这个角色带来的权限。
     *
     * <p>副作用要提前知道：{@code ux_sys_role_code} 是全表唯一索引、不带 {@code deleted_at} 条件，
     * 因此删除之后该编码仍被占用，重建同名角色会被拒绝，而界面看不到已删除的角色——
     * 建角色侧的提示已写明「含已删除的角色」。
     *
     * @param id 角色标识
     * @return 成功无数据；角色不存在（40400）、内置角色或仍被用户占用（40900）通过业务错误码返回
     */
    @SaCheckPermission("system:role:delete")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable long id) {
        roleModificationService.delete(id);
        // 不回带被删对象：它已经查不到了，回显一份只存在于响应里的数据没有意义
        return ApiResult.ok();
    }
}

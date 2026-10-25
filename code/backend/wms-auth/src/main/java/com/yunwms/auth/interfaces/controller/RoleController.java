package com.yunwms.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yunwms.auth.application.command.CreateRoleCommand;
import com.yunwms.auth.application.query.RoleQueryService;
import com.yunwms.auth.application.service.RoleCreationService;
import com.yunwms.auth.domain.model.RoleStatus;
import com.yunwms.auth.interfaces.controller.assembler.RoleResponseAssembler;
import com.yunwms.auth.interfaces.controller.request.CreateRoleRequest;
import com.yunwms.auth.interfaces.controller.response.CreatedRoleResponse;
import com.yunwms.auth.interfaces.controller.response.RoleDetailResponse;
import com.yunwms.auth.interfaces.controller.response.RoleListItemResponse;
import com.yunwms.web.api.ApiResult;
import com.yunwms.web.api.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
 * <p>本类只做「建」与「读」。编辑、启停、删除属于 2.2b2，不在这里预留空方法：
 * 一个返回 501 的空实现，比没有这个接口更难解释。
 *
 * <p>三个接口都要求权限点而不是只要求登录：角色是授权数据的来源，能改角色就等于能给自己
 * 加权限。列表与详情共用 {@code system:role:list}——看得到角色列表的人本来就能从列表点进详情，
 * 拆成两个权限点只会多一次配置而没有边界收益。
 */
@RestController
@RequestMapping("/api/console/roles")
public class RoleController {

    private final RoleCreationService roleCreationService;
    private final RoleQueryService roleQueryService;
    private final RoleResponseAssembler roleResponseAssembler;

    public RoleController(RoleCreationService roleCreationService,
                          RoleQueryService roleQueryService,
                          RoleResponseAssembler roleResponseAssembler) {
        this.roleCreationService = roleCreationService;
        this.roleQueryService = roleQueryService;
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
}

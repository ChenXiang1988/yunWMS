package com.yunwms.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yunwms.auth.application.command.ChangeRoleStatusCommand;
import com.yunwms.auth.application.command.ConfigureRoleDataScopeCommand;
import com.yunwms.auth.application.command.CreateRoleCommand;
import com.yunwms.auth.application.command.UpdateRoleCommand;
import com.yunwms.auth.application.query.RoleQueryService;
import com.yunwms.auth.application.service.RoleCreationService;
import com.yunwms.auth.application.service.RoleModificationService;
import com.yunwms.auth.domain.model.RoleStatus;
import com.yunwms.auth.interfaces.controller.assembler.RoleResponseAssembler;
import com.yunwms.auth.interfaces.controller.request.ChangeRoleStatusRequest;
import com.yunwms.auth.interfaces.controller.request.CreateRoleRequest;
import com.yunwms.auth.interfaces.controller.request.DataScopeRequest;
import com.yunwms.auth.interfaces.controller.request.RequestIds;
import com.yunwms.auth.interfaces.controller.request.UpdateRoleRequest;
import com.yunwms.auth.interfaces.controller.response.CreatedRoleResponse;
import com.yunwms.auth.interfaces.controller.response.RoleDetailResponse;
import com.yunwms.auth.interfaces.controller.response.RoleListItemResponse;
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
@Tag(name = "角色管理", description = "仓网端的角色管理：建、读、改、启停、删在这里闭环。角色是授权数据的来源，"
        + "能改角色就等于能给自己加权限，因此全部接口都要求权限点而不是只要求登录。"
        + "货主端没有角色管理能力，权限由管理员在这里配置。")
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
     * <p>新角色以启用状态落库，{@code builtin} 恒为 {@code false}：两者都不是请求参数，理由见 {@code NewRole}。
     * 数据范围可以一次给全，省略时为 {@code CUSTOM} 空明细（保守方向，即什么都看不见）。
     *
     * @param request 建角色请求
     * @return 新角色的标识与编码；编码格式不合规、编码已被占用、勾选的菜单或权限点不存在、
     *         数据范围类型与明细矛盾、明细里的货主不存在或未启用、
     *         {@code menuIds} 或 {@code ownerIds} 含非十进制整数的字符串通过业务错误码返回
     */
    @Operation(summary = "创建角色",
            description = "新建一个自定义角色并同时写入它勾选的菜单、权限点与数据范围。两个属性不在请求体里、"
                    + "也不能由调用方指定：新角色一律启用、builtin 恒为 false——接口不能创建内置角色，"
                    + "否则等于绕开权限体系造一个超级管理员。数据范围可选：省略时是 CUSTOM 空明细，"
                    + "管理员随后在 PUT /{id}/data-scope 里明确设置；ALL 与 ASSIGNED 不接受货主明细，"
                    + "CUSTOM 的货主必须存在且启用。角色编码是程序引用角色的标识，格式不合规或已被占用"
                    + "（含已删除的角色，因为唯一索引不带删除条件）返回 40000/40902；勾选的菜单或权限点不存在"
                    + "同样返回 40000。响应只回带标识与编码。")
    @SaCheckPermission("system:role:create")
    @PostMapping
    public ApiResult<CreatedRoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        // 请求体里的标识按契约以字符串传输（见 docs/api-convention.md 第五节），
        // 在这里解析成 Long：字符串是 HTTP 协议事实，不带进用例层
        CreateRoleCommand command = new CreateRoleCommand(
                request.code(),
                request.name(),
                request.remark(),
                RequestIds.optionalIds(request.menuIds(), "menuIds"),
                request.dataScopeType(),
                RequestIds.optionalIds(request.ownerIds(), "ownerIds"));
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
    @Operation(summary = "分页查询角色",
            description = "按状态筛选角色，支持按编码或名称模糊匹配，返回每行的占用人数与权限点数量，"
                    + "供管理端角色列表页使用。两个计数由同一条 SQL 聚合得出，前端可以直接用 userCount 判断"
                    + "「该角色还能不能删」而不再发一次请求。筛选条件均可省略，全部省略即返回全量分页；"
                    + "页码从 1 开始，每页条数上限 200，越界返回 40000。")
    @SaCheckPermission("system:role:list")
    @GetMapping
    public ApiResult<PageResult<RoleListItemResponse>> page(
            @Parameter(description = "页码，从 1 开始；省略时取 1")
            @RequestParam(required = false) Integer pageNo,
            @Parameter(description = "每页条数，上限 200；省略时取 20，超出上限返回 40000")
            @RequestParam(required = false) Integer pageSize,
            @Parameter(description = "按角色编码或名称模糊匹配的关键词；省略或全空白表示不筛选")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "角色状态：ACTIVE 启用，DISABLED 停用；省略表示全部状态")
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
    @Operation(summary = "查询角色详情",
            description = "取单个角色的完整信息：列表行已有的字段之外，再带出该角色已勾选的菜单与权限点标识，"
                    + "供授权配置页回显勾选状态。menuIds 同时含菜单（C）与操作（F）两级——只回操作级会让菜单节点"
                    + "无法回显。角色不存在或已被逻辑删除返回 40400。")
    @SaCheckPermission("system:role:list")
    @GetMapping("/{id}")
    public ApiResult<RoleDetailResponse> detail(
            @Parameter(description = "角色标识，取自列表响应里的 id")
            @PathVariable long id) {
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
     *         内置角色（40900）、{@code menuIds} 含非十进制整数的字符串（40000）
     *         通过业务错误码返回
     */
    @Operation(summary = "编辑角色",
            description = "全量替换角色名称、备注与授权：menuIds 提交的就是这个角色之后拥有的全部权限点，"
                    + "空数组表示清空全部授权，名称与授权在同一个事务里落库，不存在只改了一半的中间态。"
                    + "编码不在可编辑范围内——配置与代码按编码引用角色，改了等于换一个角色，"
                    + "因此多传 code 会被拒绝而不是静默忽略。内置角色不可编辑（40900）；"
                    + "角色不存在返回 40400，名称为空或菜单不存在返回 40000。成功后不回带详情。")
    @SaCheckPermission("system:role:update")
    @PutMapping("/{id}")
    public ApiResult<Void> update(
            @Parameter(description = "角色标识，取自列表响应里的 id")
            @PathVariable long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        // 与建角色同一口径：元素以字符串传输，解析成 Long 后再构造 Command
        roleModificationService.update(new UpdateRoleCommand(
                id, request.name(), request.remark(),
                RequestIds.optionalIds(request.menuIds(), "menuIds")));
        return ApiResult.ok();
    }

    /**
     * 配置角色的数据范围。
     *
     * <p><b>整批替换</b>：提交的类型与明细就是该角色此后的全部范围。独立成一条端点而不是并入
     * {@code PUT /{id}}：合在一起之后「只改名称」的请求必须把范围也提交，漏传就等于清空范围，
     * 而漏传只需要界面上少渲染一个字段。
     *
     * <p>权限点用 {@code system:role:update}：改数据范围和改名称一样，动的都是「这个角色本身」，
     * 而它决定这个角色的人能看见哪些货主，与改名同级而不是与删除同级。
     *
     * @param id      角色标识
     * @param request 目标类型与明细
     * @return 成功无数据；角色不存在（40400）、内置角色（40900）、类型为空、类型与明细矛盾、
     *         明细里的货主不存在或未启用、{@code ownerIds} 含非十进制整数的字符串（40000）
     *         通过业务错误码返回
     */
    @Operation(summary = "配置角色数据范围（整批替换）",
            description = "把角色的数据范围整体替换为提交的这一组：dataScopeType 与 ownerIds 就是该角色此后的"
                    + "全部范围，重复提交同一组是幂等的。独立成一条端点而不是并入编辑角色，是为了避免"
                    + "「资料编辑漏传字段就把数据范围清空」。ALL（全部货主）与 ASSIGNED（我负责的货主）"
                    + "是规则而不是名单，不接受 ownerIds（返回 40000）；CUSTOM 允许空数组，效果是看不见任何货主。"
                    + "CUSTOM 的货主必须存在且启用，否则返回 40000。内置角色不可修改（40900），"
                    + "角色不存在返回 40400。成功后不回带范围，需要最新值时取详情。")
    @SaCheckPermission("system:role:update")
    @PutMapping("/{id}/data-scope")
    public ApiResult<Void> configureDataScope(
            @Parameter(description = "角色标识，取自列表或详情响应里的 id")
            @PathVariable long id,
            @Valid @RequestBody DataScopeRequest request) {
        roleModificationService.configureDataScope(new ConfigureRoleDataScopeCommand(
                id, request.dataScopeType(), RequestIds.optionalIds(request.ownerIds(), "ownerIds")));
        // 不回带范围：前端提交的就是它屏幕上那份数据；需要最新值（例如被并发改过）时重新取详情
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
    @Operation(summary = "启用或停用角色",
            description = "切换角色状态。停用立即生效：鉴权侧每次请求都按角色状态过滤，持有该角色的用户下一次请求"
                    + "就不再拥有它带来的权限点，不必等会话过期；启用同样立即恢复。目标状态与当前状态相同时"
                    + "返回成功，不报错。内置角色不可停用（40900）——停完之后没有任何账号能配置权限，"
                    + "而界面上没有恢复入口。角色不存在返回 40400。")
    @SaCheckPermission("system:role:update")
    @PutMapping("/{id}/status")
    public ApiResult<Void> changeStatus(
            @Parameter(description = "角色标识，取自列表响应里的 id")
            @PathVariable long id,
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
    @Operation(summary = "删除角色",
            description = "删除一个自定义角色，采用软删除（库里留 deleted_at），与账号的删除模式一致，"
                    + "将来审计日志里的角色编码才对得上真实对象；删除后列表与详情立即查不到它。"
                    + "仍被用户占用的角色一律拒绝并在提示里带出占用人数（40900）：关联表是级联删除，"
                    + "少了这道校验，删除会静默带走关联记录，那些用户会静默失去该角色带来的权限。"
                    + "内置角色不可删除（40900），角色不存在返回 40400。副作用需提前知道：角色编码的唯一索引"
                    + "不带删除条件，删除后该编码仍被占用，重建同名角色会被拒绝。成功后不回带被删对象。")
    @SaCheckPermission("system:role:delete")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(
            @Parameter(description = "角色标识，取自列表响应里的 id")
            @PathVariable long id) {
        roleModificationService.delete(id);
        // 不回带被删对象：它已经查不到了，回显一份只存在于响应里的数据没有意义
        return ApiResult.ok();
    }
}

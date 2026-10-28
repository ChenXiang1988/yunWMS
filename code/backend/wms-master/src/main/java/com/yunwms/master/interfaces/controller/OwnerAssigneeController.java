package com.yunwms.master.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yunwms.master.application.command.AssignOwnerAssigneeCommand;
import com.yunwms.master.application.service.OwnerAssigneeService;
import com.yunwms.master.interfaces.controller.assembler.OwnerResponseAssembler;
import com.yunwms.master.interfaces.controller.request.OwnerAssigneeSaveRequest;
import com.yunwms.master.interfaces.controller.response.OwnerAssigneeResponse;
import com.yunwms.web.api.ApiResult;
import com.yunwms.web.exception.BizException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端的货主负责客服入口：回显当前配置、把主副两个角色一次写到目标状态。
 *
 * <p><b>为什么单独一个 Controller：</b>负责客服有自己的路径段（{@code /assignees}）与独立的请求/响应类型，
 * 按 {@code docs/coding-project.md}「一个能力一个 Controller」单独成类；{@link OwnerController} 已承载八个端点，
 * 再塞进两个方法会越过「一次能读完」的边界。三个类共用路径前缀 {@code /api/console/owners} 是允许的：
 * Spring 按完整路径匹配，负责人路径多一段，与货主的八个端点、联系人的四个端点都不冲突。
 *
 * <p><b>写入口用 {@code system:owner:update}，不新增权限点：</b>权限矩阵的「客户管理」节只列到
 * 「编辑货主档案」，没有单列「维护负责客服」；负责客服是货主档案「负责客服」页签的内容，职责上属于
 * 编辑货主档案的范围，复用编辑点不会让谁多拿到一项他现在没有的能力（能编辑货主的人本来就能改它的负责人），
 * 而新增一个权限点要同时改权限表、角色模板与前端按钮，收益只是把一个按钮拆出来。
 * 读入口与货主详情同口径用 {@code system:owner:list}：能看到货主详情的人本来就能看到它的负责人页，
 * 为只读再拆一个点只会多一次配置而没有边界收益（与 {@link OwnerContactController} 同一取向）。
 *
 * <p><b>{@code PUT} 返回 {@code ApiResult<Void>}：</b>与 {@link OwnerController#update}、
 * {@link OwnerContactController#update} 同一口径——成功后不回带详情，需要最新状态时前端重新 {@code GET}。
 * 回带一份「刚写进去的值」没有信息量（前端手里就是它提交的那一份），而两份来源在并发改动之后
 * 会引入两种真相。
 *
 * <p><b>并发冲突已被翻译：</b>{@code ux_owner_assignee_role} 建在 {@code (owner_id, relation_type)} 上，
 * 两笔并发的替换可能都越过了「删旧行」这一步，后写的一笔撞上唯一索引。那是一次能被如实解释的正常失败
 * （别人先改了同一角色），由 {@code OwnerConstraintTranslator#translateAssigneeWrite} 翻成 40900 并提示
 * 「该货主已有主负责人，请刷新后重试」，而不是让人在 50000 上看到一条 SQL 报错。
 * 两条并发请求只有一条生效，另一条刷新后再提交。
 *
 * <p><b>不新增错误码：</b>当前会话无效（40100）；货主不存在、已逻辑删除或不在当前账号的数据范围内（40400）；
 * 主负责人缺失（40000）、标识不是十进制整数（40000）、主副为同一名员工（40000）、
 * 任一员工不是当前在册的启用员工（40000）；并发改写同一角色（40900）。
 *
 * <p>本类只做协议转换：把请求体搬成命令对象、调用用例、把结果搬成响应体。校验顺序、可见性判定、
 * 员工有效性、主副不同与审计留痕都在 {@link OwnerAssigneeService} 里，换一个 HTTP 入口不会改变这些行为。
 */
@Tag(name = "货主负责客服", description = "货主详情「负责客服」的读写入口：一名主负责人 + 至多一名副负责人。"
        + "两个角色由 owner_assignee 的 (owner_id, relation_type) 唯一索引保证各至多一个，"
        + "并发下两笔「改主负责人」撞上该索引时返回 40900 并提示刷新后重试。"
        + "写入是全量替换：请求体同时给出主、副两个角色的目标值，不传副负责人表示不设副，"
        + "而主负责人不可撤销——每个货主一旦配置负责人，主负责人就在其中。"
        + "归属取路径里的 ownerId，请求体不含该字段；负责人没有独立的可见范围，它随货主的可见性，"
        + "货主不可见时返回 40400。读用 system:owner:list（与货主详情同口径），"
        + "写用 system:owner:update（负责客服属货主档案的维护范围），只登录但没有权限点的账号会拿到 403。")
@RestController
@RequestMapping("/api/console/owners/{ownerId}/assignees")
public class OwnerAssigneeController {

    private final OwnerAssigneeService ownerAssigneeService;
    private final OwnerResponseAssembler ownerResponseAssembler;

    /**
     * @param ownerAssigneeService   负责客服用例
     * @param ownerResponseAssembler 用例结果到响应体的转换
     */
    public OwnerAssigneeController(OwnerAssigneeService ownerAssigneeService,
                                   OwnerResponseAssembler ownerResponseAssembler) {
        this.ownerAssigneeService = ownerAssigneeService;
        this.ownerResponseAssembler = ownerResponseAssembler;
    }

    /**
     * 取某货主当前的主、副负责人，供详情页与配置弹窗回显。
     *
     * <p>先判可见性再读两个角色：不可见时的正确结果不是「两个都空」——那会被读成「这个货主还没有配置负责人」，
     * 而事实是「你无权看这个货主」，两者对调用方的下一步动作完全不同。
     *
     * @param ownerId 货主标识
     * @return 当前配置；某个角色还没有人时对应字段为 {@code null}。货主不存在、已逻辑删除或不在当前账号的
     *         数据范围内时返回 40400
     */
    @Operation(summary = "查询货主负责客服",
            description = "取某货主当前的主、副负责人各一名，只含员工标识，不含姓名——姓名在 sys_user，"
                    + "属 wms-auth 的用户表，本接口不把姓名抄一份。三个标识都以字符串返回："
                    + "字符串形式的十进制整数。某个角色还没有人时该字段为 null，不是 \"null\" 也不是空串——"
                    + "配置弹窗据此显示「未配置」并允许选择员工。"
                    + "响应回带 ownerId：配置弹窗随货主切换复用同一个组件，先发的请求可能在切换之后才返回，"
                    + "前端凭这个字段判断手里这份配置是不是当前货主的并丢弃过期响应。"
                    + "负责人没有独立的可见范围，它随货主的可见性：货主不存在、已被逻辑删除或不在当前账号的"
                    + "数据范围内返回 40400，而不是返回两个 null——后者会让人以为这个货主还没配置负责人。")
    @SaCheckPermission("system:owner:list")
    @GetMapping
    public ApiResult<OwnerAssigneeResponse> detail(
            @Parameter(description = "货主标识，取自货主列表或详情响应里的 id；不可见时返回 40400")
            @PathVariable long ownerId) {
        return ApiResult.ok(ownerResponseAssembler.toAssigneeResponse(ownerAssigneeService.view(ownerId)));
    }

    /**
     * 把某货主的主、副负责人一次写到目标状态。
     *
     * <p>归属取路径里的 {@code ownerId}，请求体里没有这个字段：让调用方在请求体里再声明一次归属，
     * 就会出现「路径说 A、身体说 B」这种只能靠一句「以路径为准」解释的状态。
     *
     * <p>不回带写入后的配置，理由见类注释：前端手里就是它提交的那一份，需要最新状态时重新 {@code GET}。
     *
     * @param ownerId 货主标识
     * @param request 负责客服请求体
     * @return 成功无数据；当前会话无效（40100）、货主不存在或不可见（40400）、主负责人缺失或标识非法
     *         或主副为同一名员工或员工不在册（40000）、并发改写同一角色（40900）通过业务错误码返回
     */
    @Operation(summary = "配置货主负责客服",
            description = "全量替换某货主的主、副负责人：一次提交给出两个角色的目标值，"
                    + "而不是「这次改哪一个角色」——部分更新分不清「没传」与「传了空值表示清除」，"
                    + "一次「只换副负责人」的提交就可能静默清掉主负责人。"
                    + "primaryUserId 必填，字符串形式的十进制整数：主负责人不能撤销，换人只能提交另一名员工；"
                    + "缺失或只填空白返回 40000。secondaryUserId 不传或传 null 表示该货主不设副负责人，"
                    + "已有的副负责人会被清除；传了值就必须是可解析的十进制整数。"
                    + "两个标识不是十进制整数（含超出 Long 范围、整串空白）一律返回 40000 并带出字段名与原值，"
                    + "不会因解析失败变成 50000。主副不能是同一名员工，否则返回 40000——唯一索引建在角色上，"
                    + "拦不住同一个人各占一行。两个员工都必须在册且启用，否则返回 40000 并点名是哪个角色。"
                    + "并发下两笔「改主负责人」仍可能撞上唯一索引 ux_owner_assignee_role，此时返回 40900 并提示"
                    + "「该货主已有主负责人，请刷新后重试」——两条只有一条生效，另一条刷新后再提交。"
                    + "成功后不回带配置，需要最新值时重新 GET。货主不存在、已逻辑删除或不在当前账号的数据范围内"
                    + "返回 40400。")
    @SaCheckPermission("system:owner:update")
    @PutMapping
    public ApiResult<Void> update(
            @Parameter(description = "货主标识，取自货主列表或详情响应里的 id；不可见时返回 40400")
            @PathVariable long ownerId,
            @Valid @RequestBody OwnerAssigneeSaveRequest request) {
        ownerAssigneeService.assign(new AssignOwnerAssigneeCommand(
                ownerId,
                toUserId(request.primaryUserId(), "primaryUserId"),
                toUserId(request.secondaryUserId(), "secondaryUserId")));
        return ApiResult.ok();
    }

    /**
     * 把请求体里以字符串传输的员工标识解析成 {@code Long}。
     *
     * <p>解析停在接口层：{@code docs/api-convention.md} 第五节要求 JSON 里的标识是字符串，
     * 而用例只认识 {@code Long}。口径与 {@code wms-auth} 的 {@code RequestIds} 一致
     * （那个类在 {@code wms-auth} 的 interfaces 层，本模块不能依赖它）：{@code null} 原样传空、
     * 整串空白与非十进制整数一律 40000 并带出字段名与实际值。
     *
     * <p><b>不能让 {@code NumberFormatException} 冒到全局兜底：</b>那会变成 50000，
     * 把「调用方传错了」伪装成「服务端故障」，用户据此无法自查。
     *
     * @param raw       请求体里的原始字符串，{@code null} 表示字段缺省
     * @param fieldName JSON 字段名，用于拼装错误提示，使调用方能定位到具体字段
     * @return {@code raw} 为 {@code null} 时返回 {@code null}，否则返回其十进制数值
     * @throws BizException 值为空白或不是十进制整数（含超出 {@code Long} 范围）时抛出，错误码 40000
     */
    private static Long toUserId(String raw, String fieldName) {
        if (raw == null) {
            // 副负责人的「不设」就是不带这个字段，null 因此是合法输入；主负责人由 @NotBlank 先挡一次
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            // 整串空白不能当成 0：0 是一个合法但不存在的标识，当成 0 会让一条错误请求
            // 以「不是当前在册的启用员工」的形式返回，掩盖真正的原因
            throw BizException.badRequest(fieldName + " 不能为空，必须是字符串形式的十进制整数");
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            // 转成本方异常时保留 cause：提示面向调用方，cause 面向排查，丢掉它等于抹掉
            // 「这个值在解析阶段就被拒绝」这条边界
            BizException failure = BizException.badRequest(
                    fieldName + " 必须是字符串形式的十进制整数，实际为：" + text);
            failure.initCause(e);
            throw failure;
        }
    }
}

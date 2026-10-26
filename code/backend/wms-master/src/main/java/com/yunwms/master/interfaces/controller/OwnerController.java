package com.yunwms.master.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yunwms.master.application.command.CreateOwnerCommand;
import com.yunwms.master.application.command.UpdateOwnerCommand;
import com.yunwms.master.application.query.OwnerQueryService;
import com.yunwms.master.application.service.OwnerCreationService;
import com.yunwms.master.application.service.OwnerUpdateService;
import com.yunwms.master.interfaces.controller.assembler.OwnerResponseAssembler;
import com.yunwms.master.interfaces.controller.request.OwnerCreateRequest;
import com.yunwms.master.interfaces.controller.request.OwnerUpdateRequest;
import com.yunwms.master.interfaces.controller.response.CreatedOwnerResponse;
import com.yunwms.master.interfaces.controller.response.OwnerDetailResponse;
import com.yunwms.master.interfaces.controller.response.OwnerListItemResponse;
import com.yunwms.web.api.ApiResult;
import com.yunwms.web.api.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * 管理端的货主档案入口：建档、列表、详情、编辑。
 *
 * <p>路径前缀 {@code /api/console} 表示仓网端：货主端（{@code /api/portal}）只能看自己的档案，
 * 建档、列表、详情与编辑都在仓网端完成。
 *
 * <p>状态流转属 3.2，联系人属 3.5，税号属 3.6，主账号属 3.3——都不在这里预留空方法：
 * 一个返回 501 的占位实现，比没有这个接口更难解释。
 *
 * <p>权限点三个：建档 {@code system:owner:create}，编辑 {@code system:owner:update}，
 * 列表与详情共用 {@code system:owner:list}。前两个不合并，是因为建档的副作用（取号、占用 SKU 前缀）
 * 与编辑不同，编辑又是草稿提交与状态流转（3.2）的落点；列表与详情合并成一个点，是因为看得到列表的人
 * 本来就能从列表点进详情，拆开只会多一次配置而没有边界收益。三个点由 {@code V033} 定义，
 * 注解由 {@code SaTokenAnnotationConfig} 注册的拦截器消费。
 *
 * <p>本类只做协议转换：把请求体搬成命令对象，把查询参数交给用例，把用例结果搬成响应体。
 * 校验顺序、唯一冲突翻译、分页范围与「哪些字段不可改」都在用例与领域层，改一个 HTTP 入口
 * 不会改变这些行为。
 */
@Tag(name = "货主档案", description = "仓网端的货主（客户）建档、列表、详情与编辑。建档由系统生成客户编码并占用 SKU 前缀；"
        + "编辑不能改编码、SKU 前缀与状态。全部接口都要求对应权限点，只登录但没有权限点的账号会拿到 403。")
@RestController
@RequestMapping("/api/console/owners")
public class OwnerController {

    private final OwnerCreationService ownerCreationService;
    private final OwnerQueryService ownerQueryService;
    private final OwnerUpdateService ownerUpdateService;
    private final OwnerResponseAssembler ownerResponseAssembler;

    public OwnerController(OwnerCreationService ownerCreationService,
                           OwnerQueryService ownerQueryService,
                           OwnerUpdateService ownerUpdateService,
                           OwnerResponseAssembler ownerResponseAssembler) {
        this.ownerCreationService = ownerCreationService;
        this.ownerQueryService = ownerQueryService;
        this.ownerUpdateService = ownerUpdateService;
        this.ownerResponseAssembler = ownerResponseAssembler;
    }

    /**
     * 新建货主。
     *
     * <p>客户编码由系统按编码规则生成（形如 {@code OWNER0001}），请求体里没有这个字段；
     * SKU 前缀在建档时确定并占用，此后不可修改。
     *
     * @param request 建档请求体
     * @return 新货主的标识与系统编码；初始状态不可用于建档、SKU 前缀格式不合规、地址或字典规则不满足
     *         通过 40000 返回，编码 / SKU 前缀 / 外部码被占用分别通过 40904 / 40905 / 40906 返回
     */
    @Operation(summary = "新建货主",
            description = "由仓网端操作人建立一个货主档案。系统生成客户编码并占用 SKU 前缀——编码不回填到请求里，"
                    + "它由取号引擎产生，只有响应体里才有。initialStatus 决定这次是「保存草稿」（DRAFT）还是"
                    + "「直接提交」（ACTIVE），其余三个状态只能由状态流转产生，传入返回 40000。"
                    + "SKU 前缀必须是 4 位大写字母或数字且不含 0、O、1、I，系统拒绝这类输入而不做替换；"
                    + "它全局唯一（草稿也算占用）且建档后锁定，因此重复返回 40905，提示里带被占用的前缀原文"
                    + "与占用方的系统编码。"
                    + "外部码可选，在未删除的货主之间唯一，重复返回 40906。客户名称可以重复——名称不是标识。"
                    + "注册地址按字典校验：国家与州省必须取自已启用的字典项，州省必须属于所选国家，"
                    + "美国必须填州省，无邮编的地区（港澳台）可以留空邮编。编码撞号返回 40904，重新提交即可。")
    @SaCheckPermission("system:owner:create")
    @PostMapping
    public ApiResult<CreatedOwnerResponse> create(@Valid @RequestBody OwnerCreateRequest request) {
        CreateOwnerCommand command = new CreateOwnerCommand(
                request.name(),
                request.shortName(),
                request.nameEn(),
                request.externalCode(),
                request.skuPrefix(),
                request.ownerType(),
                request.settlementEntity(),
                request.country(),
                request.province(),
                request.city(),
                request.district(),
                request.postalCode(),
                request.addressLine(),
                request.remark(),
                request.initialStatus());
        return ApiResult.ok(ownerResponseAssembler.toResponse(ownerCreationService.create(command)));
    }

    /**
     * 分页查询货主。
     *
     * <p>筛选与分页参数全部可选，全部省略即返回全量分页。
     *
     * <p>本批<b>不做数据范围过滤</b>：任何有 {@code system:owner:list} 的账号都能看到全部货主。
     * 这是任务 2.3 / 2.4 要解决的问题，在这里先自行加一层过滤会让本批的行为依赖一个尚未定义的权限模型。
     *
     * @param pageNo    页码，从 1 开始；省略时取 1
     * @param pageSize  每页条数，上限 200；省略时取 20
     * @param keyword   按编码或名称模糊匹配；省略或全空白表示不筛选
     * @param ownerType 客户类型，取 {@code OWNER_TYPE} 字典的 value；省略表示不筛选
     * @param status    货主状态编码，取枚举名；省略表示全部状态
     * @return 一页货主及总数；页码或每页条数越界、状态编码未知时返回 40000
     */
    @Operation(summary = "分页查询货主",
            description = "供管理端货主列表页使用：按名称或编码模糊匹配、按客户类型与状态筛选，返回每行的"
                    + "标识、编码、名称、客户类型、状态与更新时间。keyword 同时匹配系统编码与客户名称（仓储侧"
                    + "还匹配外部码），全空白按不筛选处理。ownerType 按字典 value 精确匹配，但不校验该值是否"
                    + "仍在字典中——存量货主可能引用已停用的字典项，按它筛选不该因此报错。status 取状态枚举名，"
                    + "未知值返回 40000。页码从 1 开始，每页条数上限 200，越界返回 40000（拒绝而不是静默截断）。"
                    + "本批不返回主负责人与 SKU 数：两个字段分别依赖联系人（3.5）与商品模块，尚无数据来源，"
                    + "字段因此不出现，而不是返回 null 或 0 冒充已有能力。列表当前返回全部货主，"
                    + "数据范围过滤属后续任务。")
    @SaCheckPermission("system:owner:list")
    @GetMapping
    public ApiResult<PageResult<OwnerListItemResponse>> page(
            @Parameter(description = "页码，从 1 开始；省略时取 1")
            @RequestParam(required = false) Integer pageNo,
            @Parameter(description = "每页条数，上限 200；省略时取 20，超出上限返回 40000")
            @RequestParam(required = false) Integer pageSize,
            @Parameter(description = "按系统编码或客户名称模糊匹配的关键词；省略或全空白表示不筛选")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "客户类型，取 OWNER_TYPE 字典的 value（BRAND / FORWARDER）；省略表示不筛选。"
                    + "不校验该值是否仍在字典中")
            @RequestParam(required = false) String ownerType,
            @Parameter(description = "货主状态编码：DRAFT / POTENTIAL / ACTIVE / SUSPENDED / TERMINATED；"
                    + "省略表示全部状态，其它值返回 40000")
            @RequestParam(required = false) String status) {
        return ApiResult.ok(ownerResponseAssembler.toPageResponse(
                ownerQueryService.page(pageNo, pageSize, keyword, ownerType, status)));
    }

    /**
     * 查询货主详情。
     *
     * <p>只返回详情页「基本信息」页签的内容。联系人、税号与账号三个页签分别属 3.5 / 3.6 / 3.3，
     * 本批不返回任何占位字段。
     *
     * @param id 货主标识
     * @return 货主详情；货主不存在或已逻辑删除时返回 40400
     */
    @Operation(summary = "查询货主详情",
            description = "取单个货主的完整档案：列表行已有的标识、编码、名称、客户类型、状态与更新时间之外，"
                    + "再带出英文名称、简称、外部码、SKU 前缀、结算主体、注册地址六字段、备注与创建时间，"
                    + "供详情页「基本信息」页签回填。SKU 前缀只读——它是已生成 SKUID 的前四位，创建后不可修改。"
                    + "本批只做基本信息页签：联系人（3.5）、税号（3.6）与主账号（3.3）不在响应里，"
                    + "也不返回占位字段。货主不存在或已被逻辑删除返回 40400。")
    @SaCheckPermission("system:owner:list")
    @GetMapping("/{id}")
    public ApiResult<OwnerDetailResponse> detail(
            @Parameter(description = "货主标识，取自列表响应里的 id")
            @PathVariable long id) {
        return ApiResult.ok(ownerResponseAssembler.toDetailResponse(ownerQueryService.detail(id)));
    }

    /**
     * 编辑货主档案。
     *
     * <p><b>全量替换</b>可编辑字段：字段传 {@code null} 表示清空，因此调用方必须提交自己希望保留的全部值。
     * 编码、SKU 前缀与状态不在请求体里，多传会被 {@code FAIL_ON_UNKNOWN_PROPERTIES} 拒绝。
     *
     * <p>改完之后不回带详情：前端提交的就是它屏幕上那份数据，原样回显没有信息量；
     * 需要最新值（例如外部码被并发改过）时重新取详情。
     *
     * @param id      货主标识
     * @param request 编辑请求体
     * @return 成功无数据；货主不存在（40400）、字段或字典规则不满足（40000）、
     *         外部码已被占用（40906）通过业务错误码返回
     */
    @Operation(summary = "编辑货主档案",
            description = "全量替换可编辑字段：字段传 null 表示清空，不传按 null 处理，即调用方必须提交"
                    + "自己希望保留的全部值。编码、SKU 前缀与状态不在可编辑范围内——编码是标识，"
                    + "SKU 前缀是已生成 SKUID 的前四位（改了会让已打印的标签指向另一个货主的货，草稿期同样不允许），"
                    + "状态属状态流转。这三个字段不出现在请求体里，多传会被拒绝而不是静默忽略。"
                    + "字典校验与建档同一套：客户类型、国家要启用，州省要属于所选国家，美国必须填州省，"
                    + "无邮编地区（港澳台）可留空邮编。外部码在未删除的货主之间唯一，被他人占用返回 40906；"
                    + "传 null 可清空，清空后该编码可被其他货主使用。客户名称可以重复。货主不存在返回 40400。"
                    + "成功后不回带详情，需要最新值时重新取。")
    @SaCheckPermission("system:owner:update")
    @PutMapping("/{id}")
    public ApiResult<Void> update(
            @Parameter(description = "货主标识，字符串形式的十进制整数")
            @PathVariable long id,
            @Valid @RequestBody OwnerUpdateRequest request) {
        ownerUpdateService.update(new UpdateOwnerCommand(
                id,
                request.name(),
                request.shortName(),
                request.nameEn(),
                request.externalCode(),
                request.ownerType(),
                request.settlementEntity(),
                request.country(),
                request.province(),
                request.city(),
                request.district(),
                request.postalCode(),
                request.addressLine(),
                request.remark()));
        return ApiResult.ok();
    }
}

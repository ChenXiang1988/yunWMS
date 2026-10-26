package com.yunwms.master.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yunwms.master.application.command.CreateOwnerCommand;
import com.yunwms.master.application.command.UpdateOwnerCommand;
import com.yunwms.master.application.service.OwnerCreationService;
import com.yunwms.master.application.service.OwnerUpdateService;
import com.yunwms.master.interfaces.controller.assembler.OwnerResponseAssembler;
import com.yunwms.master.interfaces.controller.request.OwnerCreateRequest;
import com.yunwms.master.interfaces.controller.request.OwnerUpdateRequest;
import com.yunwms.master.interfaces.controller.response.CreatedOwnerResponse;
import com.yunwms.web.api.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端的货主档案入口：建档与编辑。
 *
 * <p>路径前缀 {@code /api/console} 表示仓网端：货主端（{@code /api/portal}）只能看自己的档案，
 * 建档与编辑都在仓网端完成。
 *
 * <p>本类只有两个端点。列表与详情属 3.1b-2，状态流转属 3.2，联系人与税号属 3.5 / 3.6——
 * 都不在这里预留空方法：一个返回 501 的占位实现，比没有这个接口更难解释。
 *
 * <p>权限点两个：建档用 {@code system:owner:create}，编辑用 {@code system:owner:update}。
 * 建档的副作用（取号、占用 SKU 前缀）与编辑不同，编辑又是草稿提交与状态流转（3.2）的落点，
 * 因此不合并成一个「写」权限。两个点由 {@code V033} 定义，注解由
 * {@code SaTokenAnnotationConfig} 注册的拦截器消费。
 *
 * <p>本类只做协议转换：把请求体搬成命令对象，把用例结果搬成响应体。校验顺序、唯一冲突翻译
 * 与「哪些字段不可改」都在用例与领域层，改一个 HTTP 入口不会改变这些行为。
 */
@Tag(name = "货主档案", description = "仓网端的货主（客户）建档与编辑。建档由系统生成客户编码并占用 SKU 前缀；"
        + "编辑不能改编码、SKU 前缀与状态。全部接口都要求对应权限点，只登录但没有权限点的账号会拿到 403。")
@RestController
@RequestMapping("/api/console/owners")
public class OwnerController {

    private final OwnerCreationService ownerCreationService;
    private final OwnerUpdateService ownerUpdateService;
    private final OwnerResponseAssembler ownerResponseAssembler;

    public OwnerController(OwnerCreationService ownerCreationService,
                           OwnerUpdateService ownerUpdateService,
                           OwnerResponseAssembler ownerResponseAssembler) {
        this.ownerCreationService = ownerCreationService;
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
                    + "它全局唯一（草稿也算占用）且建档后锁定，因此重复返回 40905 并指出被占用的前缀。"
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

package com.yunwms.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yunwms.auth.application.query.AuditQueryService;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.interfaces.controller.assembler.AuditLogResponseAssembler;
import com.yunwms.auth.interfaces.controller.response.AuditLogItemResponse;
import com.yunwms.web.api.ApiResult;
import com.yunwms.web.api.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端的审计日志入口：只有一个「按对象追溯」的查询接口。
 *
 * <p>路径前缀 {@code /api/console} 表示仓网端：审计日志横跨两端，能看它的人必须在仓网端；
 * 货主端没有这个能力，也没有对应的路径。
 *
 * <p><b>为什么只有 GET，没有 DELETE 也没有 PUT。</b>规格要求「审计日志 MUST 只追加不可修改」，
 * 场景「日志不可删除」的满足方式是<b>结构上没有这条路径</b>，而不是「有但校验拒绝」——
 * 后者要把「谁有删日志的权限」变成一个可以讨论、可以被误配的东西，而前者根本不存在这个问题。
 * 权限点也只配了 {@code system:audit:list}，没有为删除或修改预留任何权限点（见 {@code V038}）。
 *
 * <p>权限点由 {@code V038} 定义，注解由 {@code SaTokenAnnotationConfig} 注册的拦截器消费。
 */
@Tag(name = "审计日志", description = "按对象追溯变更记录，按操作时间倒序。只提供查询：审计日志只追加不可修改，"
        + "没有删除与修改入口——这是结构上的不存在，不是权限校验的拒绝。")
@RestController
@RequestMapping("/api/console/audit-logs")
public class AuditLogController {

    private final AuditQueryService auditQueryService;
    private final AuditLogResponseAssembler auditLogResponseAssembler;

    public AuditLogController(AuditQueryService auditQueryService,
                              AuditLogResponseAssembler auditLogResponseAssembler) {
        this.auditQueryService = auditQueryService;
        this.auditLogResponseAssembler = auditLogResponseAssembler;
    }

    /**
     * 按对象检索审计日志。
     *
     * @param bizType  审计对象类型编码，必填，如 {@code OWNER}
     * @param bizId    对象标识，必填，如 {@code OWNER0001}
     * @param pageNo   页码，从 1 开始；省略时取 1
     * @param pageSize 每页条数，上限 200；省略时取 20
     * @return 该对象的一页日志及总数，按操作时间倒序；对象类型或标识未填、页码或每页条数越界时返回 40000
     */
    @Operation(summary = "按对象查询审计日志",
            description = "按「对象类型 + 对象标识」检索该对象的全部变更记录，按操作时间倒序返回，"
                    + "供货主详情、商品详情的操作日志抽屉使用。两个筛选条件都必填：少任一个就退化成全表分页，"
                    + "而审计表只会越来越大。每行带出操作人、操作时间、动作、变更前后快照与来源端；"
                    + "beforeValue / afterValue 原样返回，子表类数据（联系人、税号）保存的是完整集合快照而不是差异。"
                    + "本接口只读：审计日志不提供删除与修改端点。")
    @SaCheckPermission("system:audit:list")
    @GetMapping
    public ApiResult<PageResult<AuditLogItemResponse>> page(
            @Parameter(description = "审计对象类型编码，必填，如 OWNER、OWNER_CONTACT、SKU、USER")
            @RequestParam(required = false) AuditBizType bizType,
            @Parameter(description = "对象标识，必填，如 OWNER0001；与 bizType 一起定位被审计的数据")
            @RequestParam(required = false) String bizId,
            @Parameter(description = "页码，从 1 开始；省略时取 1")
            @RequestParam(required = false) Integer pageNo,
            @Parameter(description = "每页条数，上限 200；省略时取 20，超出上限返回 40000")
            @RequestParam(required = false) Integer pageSize) {
        return ApiResult.ok(auditLogResponseAssembler.toPageResponse(
                auditQueryService.page(bizType, bizId, pageNo, pageSize)));
    }
}

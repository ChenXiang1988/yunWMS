package com.yunwms.master.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yunwms.master.application.command.SaveOwnerTaxCommand;
import com.yunwms.master.application.service.OwnerTaxService;
import com.yunwms.master.interfaces.controller.assembler.OwnerResponseAssembler;
import com.yunwms.master.interfaces.controller.request.OwnerTaxSaveRequest;
import com.yunwms.master.interfaces.controller.response.OwnerTaxResponse;
import com.yunwms.web.api.ApiResult;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端的货主税号入口：列表、新增、编辑与删除。
 *
 * <p><b>为什么单独一个 Controller，而不是往 {@link OwnerController} 里加四个方法：</b>那个类已承载
 * 八个端点、近四百行，再塞进税号的四个方法，单文件会越过「一次能读完」的边界。税号有自己的权限点
 * （{@code system:owner:tax:edit}，见 {@code V042}）与独立的请求/响应类型，按
 * {@code docs/coding-project.md}「一个能力一个 Controller」单独成类。三个类共用路径前缀
 * {@code /api/console/owners} 是允许的：Spring 按完整路径匹配，税号路径多一段 {@code /taxes}，
 * 与货主的八个端点、联系人的四个端点、负责客服的两个端点都不冲突，也不存在「谁先注册谁生效」的问题。
 *
 * <p><b>{@code ownerId} 在 {@code PUT} 与 {@code DELETE} 上只用于资源定位，不参与归属判定：</b>
 * 服务按<b>税号自身记录里的归属</b>判货主可见性——编辑与删除的路径里虽然带着 {@code ownerId}，
 * 那个值不会送进可见性判定；命令对象里虽然有一个 {@code ownerId} 字段，编辑时服务也不读它
 * （见 {@link OwnerTaxService#update} 的入参说明）。因此把 {@code ownerId} 写错不会把请求导向
 * 另一个货主，改到的是「{@code taxId} 指向的那条税号」，而它是否改得动，仍由它自己的货主
 * 对当前账号是否可见决定。理由与 {@link OwnerContactController} 逐条相同，不在这里重述一遍：
 * 做「路径归属与记录归属一致」的校验会让归属出现第二处真源，而真正的边界已经由服务的可见性判定守住。
 *
 * <p><b>权限点两个：</b>列表用 {@code system:owner:list}，新增、编辑、删除共用
 * {@code system:owner:tax:edit}。列表与货主详情同口径，因为是同一个页签：能看到货主详情的人
 * 本来就能打开它的税号页签，为只读再拆一个点只会多一次配置而没有边界收益；三个写入口共用一个点
 * 是权限矩阵给的口径（只列「维护税号」一个能力，不按 CRUD 拆，见 {@code V042}）。
 *
 * <p><b>不新增错误码：</b>入参不合法（必填缺失、超出列宽、税号类型不在 {@code TAX_ID_TYPE} 的适用清单中、
 * 州销售税号未填州省、州省不属于所选国家、统一社会信用代码不是 18 位）返回 40000；
 * 货主或税号不存在、已逻辑删除、不在当前账号的数据范围内返回 40400；无有效会话返回 40100。
 * 同一国家的默认税号并发写、同一国家同一州省同一类型的重复登记返回 40900——撞上
 * {@code ux_owner_tax_default} 与 {@code ux_owner_tax_unique} 都是能被如实解释的失败，
 * 翻译规则见 {@code OwnerConstraintTranslator}。
 *
 * <p>本类只做协议转换：请求体搬成命令对象、调用用例、结果搬成响应体。校验顺序、可见性判定、
 * 州省与字典规则、默认切换与审计留痕都在用例层，换一个 HTTP 入口不会改变这些行为。
 */
@Tag(name = "货主税号", description = "货主详情「税号」页签的读写入口：列表、新增、编辑与删除。"
        + "一个货主可有多条税务登记，按国家、州省与类型区分；同一货主在同一国家、同一州省下的同一类型"
        + "至多一条，同一货主同一国家至多一条默认税号，两条都由库里的部分唯一索引保证。"
        + "税号类型取自 TAX_ID_TYPE 字典，且必须与其适用国家匹配；州销售税号必须选择州省，"
        + "州省非空时必须属于所选国家，两条都由字典判定。"
        + "归属取路径里的 ownerId：请求体不含 ownerId，响应也不回带它。编辑与删除按税号自身记录的归属"
        + "判可见性，路径里的 ownerId 只用于定位，因此把 ownerId 传错不会把请求导向另一个货主。"
        + "税号没有独立的可见范围，它随货主的可见性；货主不可见时返回 40400 而不是空列表。"
        + "列表用 system:owner:list，三个写入口共用 system:owner:tax:edit，"
        + "只登录但没有权限点的账号会拿到 403。")
@RestController
@RequestMapping("/api/console/owners/{ownerId}/taxes")
public class OwnerTaxController {

    private final OwnerTaxService ownerTaxService;
    private final OwnerResponseAssembler ownerResponseAssembler;

    /**
     * @param ownerTaxService        税号用例
     * @param ownerResponseAssembler 用例结果到响应体的转换
     */
    public OwnerTaxController(OwnerTaxService ownerTaxService,
                              OwnerResponseAssembler ownerResponseAssembler) {
        this.ownerTaxService = ownerTaxService;
        this.ownerResponseAssembler = ownerResponseAssembler;
    }

    /**
     * 取某货主名下的全部税号。
     *
     * <p>不分页：单个货主的税号是个位数量级，排序由端口固定为国家、州省、类型升序
     * （见 {@code OwnerTaxRepository#findByOwnerId}）。
     *
     * @param ownerId 货主标识
     * @return 该货主的未删除税号，按国家、州省、类型升序；没有时为空列表。货主不存在、已逻辑删除
     *         或不在当前账号的数据范围内时返回 40400
     */
    @Operation(summary = "查询货主税号",
            description = "取某货主名下的全部税务登记，按国家、州省、税号类型升序返回，不分页——"
                    + "单个货主的税号是个位数量级，翻页只会让「一共几条」变成一个要另算的数字；"
                    + "州省为空的记录（国家级税号）排在每个国家的末尾。"
                    + "每行带标识、国家、州省、类型取值、税号与是否该国默认，不含 ownerId："
                    + "它就在路径上，响应里再带一份会让前端有两处可能不一致的来源。"
                    + "先判货主可见性再取列表：货主不存在、已被逻辑删除或不在当前账号的数据范围内返回 40400，"
                    + "而不是返回空列表——空列表的意思是「这个货主没有登记税号」，"
                    + "与「你无权看这个货主」对调用方的下一步动作完全不同。没有税号时返回 []，不是 null。")
    @SaCheckPermission("system:owner:list")
    @GetMapping
    public ApiResult<List<OwnerTaxResponse>> list(
            @Parameter(description = "货主标识，取自货主列表或详情响应里的 id；不可见时返回 40400")
            @PathVariable long ownerId) {
        return ApiResult.ok(ownerResponseAssembler.toTaxListResponse(ownerTaxService.list(ownerId)));
    }

    /**
     * 在指定货主名下新增一条税务登记。
     *
     * <p>归属取路径里的 {@code ownerId}，请求体里没有这个字段：让调用方在请求体里再声明一次归属，
     * 就会出现「路径说 A、身体说 B」这种只能靠一句「以路径为准」解释的状态。
     *
     * <p>返回整条而不只返回标识：前端要拿 {@code id} 渲染这一行并允许随即编辑或删除，
     * 只回标识会让它立刻再取一次列表；而这一行的字段就是刚提交的值，回带没有第二种真相。
     *
     * @param ownerId 货主标识
     * @param request 税号请求体
     * @return 新税号，含数据库生成的标识；货主不存在或不可见（40400）、必填缺失或超出列宽或类型与
     *         国家不匹配或州省规则不满足或统一社会信用代码形态不对（40000）、同一国家同一州省
     *         已有同类型税号（40900）通过业务错误码返回
     */
    @Operation(summary = "新增货主税号",
            description = "在路径指定的货主名下新增一条税务登记。归属取路径里的 ownerId，请求体不含该字段，"
                    + "多传字段返回 40000（FAIL_ON_UNKNOWN_PROPERTIES，不静默忽略）。"
                    + "国家、税号类型与税号必填，州省可为空；列宽上限为国家 2、州省 64、类型 20、税号 64"
                    + "个字符，超出返回 40000。国家取 COUNTRY 字典的取值，类型取 TAX_ID_TYPE 字典的启用项，"
                    + "且必须适用于所填国家（如州销售税号只适用于 US），否则返回 40000 并在提示里带出"
                    + "提交的类型与国家。州销售税号（US_STATE_SALES）必须填州省——它按州登记，没有州就不成立，"
                    + "而库里的唯一索引把空州省归一后判重，不填州省的州销售税号会让不同州的登记被判成重复；"
                    + "州省非空时必须属于所选国家，否则返回 40000。统一社会信用代码（CN_USCC）还必须是"
                    + "18 位大写字母或数字，不做大小写归一。isDefault 缺省按 false 处理；置 true 时接口先把"
                    + "该货主同国家的原有默认税号置为非默认再写入本条，两步在同一次事务里，因此成功返回后"
                    + "同国家至多只有一条默认税号。并发下两笔「设为默认」仍可能撞上唯一索引"
                    + "ux_owner_tax_default，此时返回 40900 并提示刷新后重新提交。"
                    + "字段不去首尾空格：提交「 941234567 」会原样入库。货主不存在、已逻辑删除或不在当前"
                    + "账号的数据范围内返回 40400。返回新税号（含标识），前端据此渲染这一行而不必重新取列表。")
    @SaCheckPermission("system:owner:tax:edit")
    @PostMapping
    public ApiResult<OwnerTaxResponse> create(
            @Parameter(description = "货主标识，取自货主列表或详情响应里的 id；不可见时返回 40400")
            @PathVariable long ownerId,
            @Valid @RequestBody OwnerTaxSaveRequest request) {
        return ApiResult.ok(ownerResponseAssembler.toTaxResponse(
                ownerTaxService.create(toCommand(ownerId, request))));
    }

    /**
     * 覆盖一条税号的可编辑字段：州省、号码与默认标记。
     *
     * <p><b>路径里的 {@code ownerId} 只用于定位，不参与归属判定：</b>归属以税号自身记录为准，
     * 命令里的 {@code ownerId} 在编辑时被服务忽略。不做「路径归属与记录归属一致」的校验，
     * 理由见类注释——那会变成第二处归属真源，而真正的边界由服务的可见性判定守住。
     *
     * <p>国家与类型不可改：它们是这一行的身份，请求体里同名的两个字段在编辑时不参与判定，
     * 服务判的是库里那一行（见 {@link com.yunwms.master.application.command.SaveOwnerTaxCommand}）。
     * 换国家或换类型在业务上是另一条登记，应删旧登新。
     *
     * <p>不回带更新后的税号：设为默认会连带把同国原有的默认税号改成非默认，而响应里只有一行，
     * 回带它反而会让调用方以为列表的其余行没变；需要最新状态时重新取列表。
     *
     * @param ownerId 货主标识，仅用于定位，不参与归属判定
     * @param taxId   税号标识
     * @param request 税号请求体
     * @return 成功无数据；税号不存在或已逻辑删除（40400）、其所属货主不在当前账号的数据范围内（40400）、
     *         必填缺失或超出列宽或州省规则不满足或统一社会信用代码形态不对（40000）、
     *         同一国家同一州省已有同类型税号（40900）通过业务错误码返回
     */
    @Operation(summary = "编辑货主税号",
            description = "全量替换一条税务登记的三个可编辑字段与默认标记：州省、税号、是否默认。"
                    + "字段传 null 表示清空，不传按 null 处理，因此调用方必须提交自己希望保留的全部值；"
                    + "校验口径与新增一致（列宽、州省归属、统一社会信用代码 18 位）。"
                    + "国家与类型不可改：它们与归属一起构成这一行的身份，请求体里虽有这两个字段（与新增共用"
                    + "一个请求体），编辑时服务以记录自身的值为准，传别的值既不生效也不报错——"
                    + "换国家或换类型应删旧登新。州销售税号清空州省返回 40000；州省非空时必须属于这一行的"
                    + "国家，否则返回 40000。isDefault 缺省按 false 处理——全量替换下省略等于提交 false，"
                    + "要保留默认标记必须显式传 true；置 true 时同样先清同国旧默认再写入，两步在同一事务里。"
                    + "并发下两笔「设为默认」仍可能撞上唯一索引 ux_owner_tax_default，此时返回 40900 并提示"
                    + "刷新后重新提交。归属不可改：税号不支持在货主之间移动，路径里的 ownerId 只用于定位、"
                    + "不参与归属判定，所以把它写错不会把请求导向另一个货主，改到的始终是 taxId 指向的那条"
                    + "税号，而它是否改得动仍由它自己的货主对当前账号是否可见决定。"
                    + "成功后不回带更新后的税号：设为默认会连带把另一行改成非默认，回带单行会让调用方以为"
                    + "列表的其余行没变；需要最新状态时重新取列表。税号不存在、已逻辑删除，或其所属货主"
                    + "不在当前账号的数据范围内返回 40400。")
    @SaCheckPermission("system:owner:tax:edit")
    @PutMapping("/{taxId}")
    public ApiResult<Void> update(
            @Parameter(description = "货主标识，仅用于定位资源、不参与归属判定；写错也不会改到另一个货主的税号")
            @PathVariable long ownerId,
            @Parameter(description = "税号标识，取自税号列表响应里的 id")
            @PathVariable long taxId,
            @Valid @RequestBody OwnerTaxSaveRequest request) {
        ownerTaxService.update(taxId, toCommand(ownerId, request));
        return ApiResult.ok();
    }

    /**
     * 软删除一条税务登记。
     *
     * <p>归属判定与编辑相同：路径里的 {@code ownerId} 只用于定位，不参与判定。
     *
     * <p>删除会释放该货主在该国家下的默认税号名额（部分唯一索引带 {@code deleted_at IS NULL}），
     * 因此删掉默认税号之后可以直接设一个新的，不需要先清一次。
     *
     * @param ownerId 货主标识，仅用于定位，不参与归属判定
     * @param taxId   税号标识
     * @return 成功无数据；税号不存在、已逻辑删除，或在读到之后被另一个请求删掉（40400），
     *         其所属货主不在当前账号的数据范围内（40400）通过业务错误码返回
     */
    @Operation(summary = "删除货主税号",
            description = "软删除一条税务登记（置 deleted_at），它不再出现在列表里，记录本身保留供追溯。"
                    + "删除会释放该货主在该国家下的默认税号名额：删掉默认税号之后可以直接把另一条设为默认，"
                    + "不需要先取消默认标记。归属判定与编辑一致——路径里的 ownerId 只用于定位、"
                    + "不参与归属判定，写错它不会删掉另一个货主的税号。"
                    + "幂等：重复删除返回 40400——读取侧本就把「已删除」与「不存在」当作同一件事，"
                    + "第二次删除不会产生写入，也不会把记录写活。"
                    + "税号不存在、已逻辑删除，或其所属货主不在当前账号的数据范围内返回 40400。")
    @SaCheckPermission("system:owner:tax:edit")
    @DeleteMapping("/{taxId}")
    public ApiResult<Void> delete(
            @Parameter(description = "货主标识，仅用于定位资源、不参与归属判定；写错也不会删掉另一个货主的税号")
            @PathVariable long ownerId,
            @Parameter(description = "税号标识，取自税号列表响应里的 id")
            @PathVariable long taxId) {
        ownerTaxService.delete(taxId);
        return ApiResult.ok();
    }

    /**
     * 把请求体搬成写入命令。
     *
     * <p>新增与编辑共用这一个转换：两处各写一遍时，漏一个字段的后果是「改了个号码，默认标记被清空」，
     * 而这类缺陷不会被编译拦下（与 {@code SaveOwnerTaxCommand} 共用一份的理由同源）。
     *
     * <p>{@code isDefault} 在这里把「没传」定死为 {@code false}：请求体声明成 {@code Boolean}
     * 是为了让缺失在类型上可见，转换点只有这一处，读这一行就知道缺省的语义是什么。
     *
     * @param ownerId 路径里的货主标识；编辑时服务不读它，理由见 {@link #update}
     * @param request 税号请求体
     * @return 写入命令
     */
    private static SaveOwnerTaxCommand toCommand(long ownerId, OwnerTaxSaveRequest request) {
        return new SaveOwnerTaxCommand(
                ownerId,
                request.country(),
                request.province(),
                request.taxType(),
                request.taxNumber(),
                Boolean.TRUE.equals(request.isDefault()));
    }
}

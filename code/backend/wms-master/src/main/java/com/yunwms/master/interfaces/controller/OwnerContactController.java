package com.yunwms.master.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yunwms.master.application.command.SaveOwnerContactCommand;
import com.yunwms.master.application.service.OwnerContactService;
import com.yunwms.master.interfaces.controller.assembler.OwnerResponseAssembler;
import com.yunwms.master.interfaces.controller.request.OwnerContactSaveRequest;
import com.yunwms.master.interfaces.controller.response.OwnerContactResponse;
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
 * 管理端的货主联系人入口：列表、新增、编辑与删除。
 *
 * <p><b>为什么单独一个 Controller，而不是往 {@link OwnerController} 里加四个方法：</b>那个类已经承载
 * 八个端点、近四百行，再塞进联系人的四个方法，单文件会越过「一次能读完」的边界。联系人有自己的权限点
 * （{@code system:owner:contact:edit}，见 {@code V040}）与独立的请求/响应类型，按
 * {@code docs/coding-project.md}「一个能力一个 Controller」单独成类。两个类共用路径前缀
 * {@code /api/console/owners} 是允许的：Spring 按完整路径匹配，联系人路径多一段 {@code /contacts}，
 * 与货主的八个端点不冲突，也不存在「谁先注册谁生效」的问题。
 *
 * <p><b>{@code ownerId} 在 {@code PUT} 与 {@code DELETE} 上只用于资源定位，不参与归属判定：</b>
 * 服务按<b>联系人自身记录里的归属</b>判货主可见性——编辑与删除的路径里虽然带着 {@code ownerId}，
 * 那个值不会送进可见性判定；命令对象里虽然有一个 {@code ownerId} 字段，编辑时服务也不读它
 * （见 {@link OwnerContactService#update} 的入参说明）。因此把 {@code ownerId} 写错不会把请求导向
 * 另一个货主，改到的是「{@code contactId} 指向的那条联系人」，而它是否改得动，仍由它自己的货主
 * 对当前账号是否可见决定。
 *
 * <p><b>为什么不做「路径归属与记录归属一致」的校验：</b>那会让归属出现第二处真源——一处是联系人行里的
 * {@code owner_id}，另一处是「路径里的值与它相等」这个结论。两者迟早会因为一次重放、一次前端拼接错误
 * 而分歧，而分歧时没有依据说哪一处算准。真正的边界已经由服务里的可见性判定守住：联系人所属货主不在
 * 当前账号的数据范围内时，四条用例都返回 40400，路径里写哪个 {@code ownerId} 都改变不了这一点。
 * 那道校验的收益只是「错误的前端调用会得到 400 而不是 404」，代价是两条独立的归属口径，
 * 因此这里不写，改为在契约里把 {@code ownerId} 的用途写明。
 *
 * <p><b>权限点两个：</b>列表用 {@code system:owner:list}，新增、编辑、删除共用
 * {@code system:owner:contact:edit}。列表与货主详情同口径，因为是同一个页签：能看到货主详情的人
 * 本来就能打开它的联系人页签，为只读再拆一个点只会多一次配置而没有边界收益；三个写入口共用一个点
 * 是权限矩阵给的口径（只列「维护联系人」一个能力，不按 CRUD 拆，见 {@code V040}）。
 *
 * <p><b>不新增错误码：</b>入参不合法（必填缺失、超出列宽、角色不在 {@code CONTACT_ROLE} 的启用项中）
 * 返回 40000；货主或联系人不存在、已逻辑删除、不在当前账号的数据范围内返回 40400；无有效会话返回 40100。
 * 主联系人并发冲突返回 40900——撞上 {@code ux_owner_contact_primary} 是一次能被如实解释的失败
 * （别人先设了主联系人），提示里据此要求刷新列表后重新提交，理由见 {@link OwnerContactService} 的类注释。
 *
 * <p>本类只做协议转换：请求体搬成命令对象、调用用例、结果搬成响应体。校验顺序、可见性判定、
 * 角色字典校验、主联系人切换与审计留痕都在用例层，换一个 HTTP 入口不会改变这些行为。
 */
@Tag(name = "货主联系人", description = "货主详情「联系人」页签的读写入口：列表、新增、编辑与删除。"
        + "一个货主可有多条联系人，至多一条为主联系人，由库里的部分唯一索引保证；"
        + "置某条为主时接口先清掉原有主标记再写入，两步在同一次事务里，不会留下「一个主联系人也没有」的中间态。"
        + "归属取路径里的 ownerId：请求体不含 ownerId，响应也不回带它。编辑与删除按联系人自身记录的归属"
        + "判可见性，路径里的 ownerId 只用于定位，因此把 ownerId 传错不会把请求导向另一个货主。"
        + "联系人没有独立的可见范围，它随货主的可见性；货主不可见时返回 40400 而不是空列表。"
        + "列表用 system:owner:list，三个写入口共用 system:owner:contact:edit，只登录但没有权限点的账号会拿到 403。")
@RestController
@RequestMapping("/api/console/owners/{ownerId}/contacts")
public class OwnerContactController {

    private final OwnerContactService ownerContactService;
    private final OwnerResponseAssembler ownerResponseAssembler;

    /**
     * @param ownerContactService     联系人用例
     * @param ownerResponseAssembler  用例结果到响应体的转换
     */
    public OwnerContactController(OwnerContactService ownerContactService,
                                  OwnerResponseAssembler ownerResponseAssembler) {
        this.ownerContactService = ownerContactService;
        this.ownerResponseAssembler = ownerResponseAssembler;
    }

    /**
     * 取某货主名下的全部联系人。
     *
     * <p>不分页：单个货主的联系人是几条到几十条，排序由端口固定为标识升序（即登记先后）。
     *
     * @param ownerId 货主标识
     * @return 该货主的未删除联系人，按标识升序；没有时为空列表。货主不存在、已逻辑删除或不在当前账号的
     *         数据范围内时返回 40400
     */
    @Operation(summary = "查询货主联系人",
            description = "取某货主名下的全部联系人，按登记先后（联系人标识升序）返回，不分页——"
                    + "单个货主的联系人是几条到几十条，翻页只会让「一共几条」变成一个要另算的数字。"
                    + "每行带标识、姓名、角色取值、电话、邮箱、是否主联系人与备注，不含 ownerId："
                    + "它就在路径上，响应里再带一份会让前端有两处可能不一致的来源。"
                    + "先判货主可见性再取列表：货主不存在、已被逻辑删除或不在当前账号的数据范围内返回 40400，"
                    + "而不是返回空列表——空列表的意思是「这个货主没有联系人」，与「你无权看这个货主」"
                    + "对调用方的下一步动作完全不同。没有联系人时返回 []，不是 null。")
    @SaCheckPermission("system:owner:list")
    @GetMapping
    public ApiResult<List<OwnerContactResponse>> list(
            @Parameter(description = "货主标识，取自货主列表或详情响应里的 id；不可见时返回 40400")
            @PathVariable long ownerId) {
        return ApiResult.ok(ownerResponseAssembler.toContactListResponse(
                ownerContactService.list(ownerId)));
    }

    /**
     * 在指定货主名下新增一条联系人。
     *
     * <p>归属取路径里的 {@code ownerId}，请求体里没有这个字段：让调用方在请求体里再声明一次归属，
     * 就会出现「路径说 A、身体说 B」这种只能靠一句「以路径为准」解释的状态。
     *
     * @param ownerId 货主标识
     * @param request 联系人请求体
     * @return 新联系人，含数据库生成的标识；货主不存在或不可见（40400）、
     *         必填缺失或超出列宽或角色不在字典启用项中（40000）通过业务错误码返回
     */
    @Operation(summary = "新增货主联系人",
            description = "在路径指定的货主名下新增一条联系人。归属取路径里的 ownerId，请求体不含该字段，"
                    + "多传字段返回 40000（FAIL_ON_UNKNOWN_PROPERTIES，不静默忽略）。"
                    + "姓名、角色与电话必填，只填空白按未填处理；列宽上限为姓名 64、角色 20、电话 32、"
                    + "邮箱 128、备注 255 个字符，超出返回 40000。角色必须取自 CONTACT_ROLE 字典的启用项"
                    + "（BUSINESS / FINANCE / LOGISTICS / ONSITE / RETURN），停用项按不存在处理，"
                    + "不在其中的取值返回 40000 且提示里带提交的原值。"
                    + "isPrimary 缺省按 false 处理；置 true 时接口先把该货主原有的主联系人置为非主再写入本条，"
                    + "两步在同一次事务里，因此成功返回后列表里至多只有一条主联系人，不会有「一个主联系人"
                    + "也没有」的中间态。并发下两笔「设我为主」仍可能撞上唯一索引 ux_owner_contact_primary，"
                    + "此时返回 40900 并提示「该货主已有主联系人，请刷新列表后重新提交」——"
                    + "两条并发请求只有一条能成为主联系人，另一条刷新列表后再提交。"
                    + "字段不去首尾空格：提交「 张三 」会原样入库。货主不存在、已逻辑删除或不在当前账号的"
                    + "数据范围内返回 40400。返回新联系人（含标识），前端据此渲染这一行而不必重新取列表。")
    @SaCheckPermission("system:owner:contact:edit")
    @PostMapping
    public ApiResult<OwnerContactResponse> create(
            @Parameter(description = "货主标识，取自货主列表或详情响应里的 id；不可见时返回 40400")
            @PathVariable long ownerId,
            @Valid @RequestBody OwnerContactSaveRequest request) {
        return ApiResult.ok(ownerResponseAssembler.toContactResponse(
                ownerContactService.create(toCommand(ownerId, request))));
    }

    /**
     * 覆盖一条联系人的全部可编辑字段与主标记。
     *
     * <p><b>路径里的 {@code ownerId} 只用于定位，不参与归属判定：</b>归属以联系人自身记录为准，
     * 命令里的 {@code ownerId} 在编辑时被服务忽略。不做「路径归属与记录归属一致」的校验，
     * 理由见类注释——那会变成第二处归属真源，而真正的边界由服务的可见性判定守住。
     *
     * <p>不回带更新后的联系人：设为主联系人会连带把该货主原有的主联系人改成非主，而响应里只有一行，
     * 回带它反而会让调用方以为列表的其余行没变；需要最新状态时重新取列表。
     *
     * @param ownerId   货主标识，仅用于定位，不参与归属判定
     * @param contactId 联系人标识
     * @param request   联系人请求体
     * @return 成功无数据；联系人不存在或已逻辑删除（40400）、其所属货主不在当前账号的数据范围内
     *         （40400）、必填缺失或超出列宽或角色不在字典启用项中（40000）通过业务错误码返回
     */
    @Operation(summary = "编辑货主联系人",
            description = "全量替换一条联系人的五个可编辑字段与主标记：字段传 null 表示清空，"
                    + "不传按 null 处理，因此调用方必须提交自己希望保留的全部值。姓名、角色与电话必填，"
                    + "只填空白按未填处理；校验口径与新增完全一致（列宽、角色须取自 CONTACT_ROLE 字典的"
                    + "启用项）。isPrimary 缺省按 false 处理——全量替换下省略等于提交 false，要保留主标记"
                    + "必须显式传 true；置 true 时同样先清旧主再写入，两步在同一事务里。"
                    + "并发下两笔「设我为主」仍可能撞上唯一索引 ux_owner_contact_primary，此时返回 40900 并"
                    + "提示「该货主已有主联系人，请刷新列表后重新提交」——两条并发请求只有一条能成为主联系人，"
                    + "另一条刷新列表后再提交。"
                    + "归属不可改：联系人不支持在货主之间移动，请求体里没有 ownerId，路径里的 ownerId "
                    + "只用于定位资源、不参与归属判定——服务按联系人自身记录里的归属判可见性，"
                    + "所以把路径里的 ownerId 写错不会把请求导向另一个货主，改到的始终是 contactId "
                    + "指向的那条联系人，而它是否改得动仍由它自己的货主对当前账号是否可见决定。"
                    + "成功后不回带更新后的联系人：设为主联系人会连带把另一行改成非主，"
                    + "回带单行会让调用方以为列表的其余行没变；需要最新状态时重新取列表。"
                    + "联系人不存在、已逻辑删除，或其所属货主不在当前账号的数据范围内返回 40400。")
    @SaCheckPermission("system:owner:contact:edit")
    @PutMapping("/{contactId}")
    public ApiResult<Void> update(
            @Parameter(description = "货主标识，仅用于定位资源、不参与归属判定；写错也不会改到另一个货主的联系人")
            @PathVariable long ownerId,
            @Parameter(description = "联系人标识，取自联系人列表响应里的 id")
            @PathVariable long contactId,
            @Valid @RequestBody OwnerContactSaveRequest request) {
        ownerContactService.update(contactId, toCommand(ownerId, request));
        return ApiResult.ok();
    }

    /**
     * 软删除一条联系人。
     *
     * <p>归属判定与编辑相同：路径里的 {@code ownerId} 只用于定位，不参与判定。
     *
     * <p>删除会释放该货主的主联系人名额（部分唯一索引带 {@code deleted_at IS NULL}），
     * 因此删掉主联系人之后可以直接设新的，不需要先清一次。
     *
     * @param ownerId   货主标识，仅用于定位，不参与归属判定
     * @param contactId 联系人标识
     * @return 成功无数据；联系人不存在、已逻辑删除，或在读到之后被另一个请求删掉（40400），
     *         其所属货主不在当前账号的数据范围内（40400）通过业务错误码返回
     */
    @Operation(summary = "删除货主联系人",
            description = "软删除一条联系人（置 deleted_at），它不再出现在列表里，记录本身保留供追溯。"
                    + "删除会释放该货主的主联系人名额：删掉主联系人之后可以直接把另一条设为主，"
                    + "不需要先取消主标记。归属判定与编辑一致——路径里的 ownerId 只用于定位、"
                    + "不参与归属判定，改错它不会删掉另一个货主的联系人。"
                    + "幂等：重复删除返回 40400——读取侧本就把「已删除」与「不存在」当作同一件事，"
                    + "第二次删除不会产生写入，也不会把记录写活。"
                    + "联系人不存在、已逻辑删除，或其所属货主不在当前账号的数据范围内返回 40400。")
    @SaCheckPermission("system:owner:contact:edit")
    @DeleteMapping("/{contactId}")
    public ApiResult<Void> delete(
            @Parameter(description = "货主标识，仅用于定位资源、不参与归属判定；写错也不会删掉另一个货主的联系人")
            @PathVariable long ownerId,
            @Parameter(description = "联系人标识，取自联系人列表响应里的 id")
            @PathVariable long contactId) {
        ownerContactService.delete(contactId);
        return ApiResult.ok();
    }

    /**
     * 把请求体搬成写入命令。
     *
     * <p>新增与编辑共用这一个转换：两处各写一遍时，漏一个字段的后果是「改了个电话，邮箱被清空」，
     * 而这类缺陷不会被编译拦下（与 {@code SaveOwnerContactCommand} 共用一份的理由同源）。
     *
     * <p>{@code isPrimary} 在这里把「没传」定死为 {@code false}：请求体声明成 {@code Boolean}
     * 是为了让缺失在类型上可见，转换点只有这一处，读这一行就知道缺省的语义是什么。
     *
     * @param ownerId 路径里的货主标识；编辑时服务不读它，理由见 {@link #update}
     * @param request 联系人请求体
     * @return 写入命令
     */
    private static SaveOwnerContactCommand toCommand(long ownerId, OwnerContactSaveRequest request) {
        return new SaveOwnerContactCommand(
                ownerId,
                request.name(),
                request.roleType(),
                request.phone(),
                request.email(),
                Boolean.TRUE.equals(request.isPrimary()),
                request.remark());
    }
}

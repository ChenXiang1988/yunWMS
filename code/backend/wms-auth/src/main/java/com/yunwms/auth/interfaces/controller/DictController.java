package com.yunwms.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yunwms.auth.application.query.DictQueryService;
import com.yunwms.auth.application.service.DictMaintenanceService;
import com.yunwms.auth.domain.model.DictItem;
import com.yunwms.auth.domain.model.DictTypeSummary;
import com.yunwms.auth.domain.port.DictMaintenance;
import com.yunwms.auth.interfaces.controller.assembler.DictResponseAssembler;
import com.yunwms.auth.interfaces.controller.request.DictItemCreateRequest;
import com.yunwms.auth.interfaces.controller.request.DictItemStatusRequest;
import com.yunwms.auth.interfaces.controller.request.DictItemUpdateRequest;
import com.yunwms.auth.interfaces.controller.response.CreatedDictItemResponse;
import com.yunwms.auth.interfaces.controller.response.DictItemResponse;
import com.yunwms.auth.interfaces.controller.response.DictTypeListItemResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端的字典入口：类型清单、某类字典项、多类型批量读取，以及字典项的新增、编辑、启停、删除。
 *
 * <p>路径前缀 {@code /api/console} 表示仓网端：字典是全系统共用的配置数据，货主端没有维护能力，
 * 也没有绕开管理端读取它的理由。
 *
 * <p><b>读写各用一个权限点。</b>读的三个接口共用 {@code system:dict:list}：能看到类型清单的人本来就
 * 能从列表逐类点进去看项，拆成三个点只会多两次配置而没有边界收益。写的四个接口共用
 * {@code system:dict:update}：四条用例改的都是「某一类字典的元素集合」，加错一条与删错一条的后果
 * 同类（下拉里多一个或少一个选项），恢复动作也相同，拆成 create/update/delete 三点只会让
 * 「能新增」与「能删除」变成两种必须分别授予的角色。权限点由 {@code V031} 定义，
 * 注解由 {@code SaTokenAnnotationConfig} 注册的拦截器消费。
 *
 * <p><b>编辑、启停、删除都按项标识定位</b>（{@code /dicts/items/{id}}），路径里不带 {@code dictType}：
 * 项标识全局唯一，再带一个类型编码就得校验两者是否一致，归属会出现第二处真源——两处对不上时
 * 报哪个错、以哪个为准，是需要另立规则才能回答的问题。
 *
 * <p><b>停用与删除是两个端点。</b>停用是「不再被新数据引用」，行还在、可重新启用；删除是逻辑删除
 * （置 {@code deleted_at}），删完连管理端也看不到。把停用做成删除，会让管理员清理下拉时顺手毁掉
 * 历史数据的可解释性。
 */
@Tag(name = "数据字典", description = "仓网端的字典读取与维护：类型清单、某类字典项、多类型批量读取，以及字典项的"
        + "新增、编辑、启停、删除。读接口用 enabledOnly 承载两种消费语义——下拉只要启用项，历史数据回显要"
        + "连停用项一起取，因此不拆成两个端点；双语名称同时返回，由前端按当前界面语言取值，后端不做回退。"
        + "写接口不提供改 dictType 与 value 的能力：两者是业务数据的引用键，改掉会让历史数据指向不存在的项。")
@RestController
@RequestMapping("/api/console/dicts")
public class DictController {

    private final DictQueryService dictQueryService;
    private final DictMaintenanceService dictMaintenanceService;
    private final DictResponseAssembler dictResponseAssembler;

    public DictController(DictQueryService dictQueryService,
                          DictMaintenanceService dictMaintenanceService,
                          DictResponseAssembler dictResponseAssembler) {
        this.dictQueryService = dictQueryService;
        this.dictMaintenanceService = dictMaintenanceService;
        this.dictResponseAssembler = dictResponseAssembler;
    }

    /**
     * 查询字典类型清单。
     *
     * <p>每行的项数由同一条 SQL 聚合得出，前端不必逐类再数一次。
     *
     * @return 全部字典类型及其项数，按类型编码升序
     */
    @Operation(summary = "查询字典类型清单",
            description = "返回全部字典类型及各自的项数，供字典管理页与「有哪些字典可用」的调试场景使用。"
                    + "itemCount 计的是该类下未删除的项数，含已停用的项——它回答「这一类有多少条」，"
                    + "不是「有多少条可选」；下拉要的是启用项，由字典项接口的 enabledOnly 决定。"
                    + "停用的类型也会出现在清单里，否则停用之后再也找不到它、无法重新启用。"
                    + "不存在的路径与参数错误分别返回 404 段与 400 段错误码。")
    @SaCheckPermission("system:dict:list")
    @GetMapping
    public ApiResult<List<DictTypeListItemResponse>> listTypes() {
        List<DictTypeSummary> types = dictQueryService.listTypes();
        return ApiResult.ok(dictResponseAssembler.toTypeListResponse(types));
    }

    /**
     * 查询某类字典项。
     *
     * @param dictType    类型编码，如 {@code OWNER_TYPE}
     * @param enabledOnly {@code true}（默认）只取启用项，供给下拉；{@code false} 含停用项，供给历史回显
     * @param parentValue 只取 {@code ext.parent} 等于该值的项，用于取某个国家下的省/州；
     *                    省略或全空白表示不按层级筛选
     * @return 字典项，按排序号、标识升序
     */
    @Operation(summary = "查询某类字典项",
            description = "取一个字典类型下的项，按排序号升序返回，同时带出中英双语名称、取值、状态与扩展属性。"
                    + "enabledOnly 承载两种消费语义：true（默认）只返回启用项，对应「停用后不可选」；"
                    + "false 连停用项一起返回，对应「历史数据仍正常展示」——编辑页要同时显示下拉与历史值，"
                    + "拆成两个端点会逼它调两次。parentValue 按扩展属性里的 parent 过滤层级"
                    + "（如 PROVINCE 下取国家 US 的州/省），不靠给字典项加 parent_id 列。"
                    + "label 与 labelEn 原样返回、后端不做语言回退：接口不知道界面语言，"
                    + "回退会让「英文界面看到中文」无法排查。字典类型不存在返回 404 段错误码。")
    @SaCheckPermission("system:dict:list")
    @GetMapping("/{dictType}/items")
    public ApiResult<List<DictItemResponse>> items(
            @Parameter(description = "字典类型编码，取自类型清单响应里的 dictType，如 OWNER_TYPE")
            @PathVariable String dictType,
            @Parameter(description = "true（默认）只返回启用项，供下拉使用；false 连停用项一起返回，供历史数据回显")
            @RequestParam(required = false, defaultValue = "true") boolean enabledOnly,
            @Parameter(description = "只返回扩展属性 parent 等于该值的项，用于按国家取省/州；省略表示不按层级筛选")
            @RequestParam(required = false) String parentValue) {
        List<DictItem> items = dictQueryService.items(dictType, enabledOnly, parentValue);
        return ApiResult.ok(dictResponseAssembler.toItemListResponse(items));
    }

    /**
     * 一次取回多个类型的字典项。
     *
     * <p>返回的 Map 只含「实际取到项」的类型：入参里的类型可能已被管理员删掉，为此报错会让整页挂掉。
     *
     * @param types       类型编码，逗号分隔（{@code OWNER_TYPE,TEMP_ZONE}）；省略或全部为空白时返回空 Map
     * @param enabledOnly {@code true}（默认）只取启用项；{@code false} 含停用项
     * @return 类型编码到字典项列表的映射
     */
    @Operation(summary = "批量查询多类字典项",
            description = "一个表单常要同时取客户类型、温区、合规属性，按类型逐个请求会让页面变慢，"
                    + "因此提供按多类型一次取回的接口：types 逗号分隔，返回 { 类型编码: 字典项列表 }。"
                    + "不存在的类型静默跳过——入参可能来自管理员刚删掉的配置，为此报错会让整页挂掉；"
                    + "由此「类型不存在」「类型下没有任何项」「项全部被筛选掉」三种情况都表现为结果里不出现该键，"
                    + "前端按 data[类型] ?? [] 取值即可。多类型的读取由一条 SQL 完成，SQL 条数与类型数量无关。"
                    + "enabledOnly 与单类型接口同义：true（默认）只返回启用项，false 含停用项。")
    @SaCheckPermission("system:dict:list")
    @GetMapping("/batch")
    public ApiResult<Map<String, List<DictItemResponse>>> batch(
            @Parameter(description = "字典类型编码，逗号分隔，如 OWNER_TYPE,TEMP_ZONE；重复与空白会被忽略")
            @RequestParam(required = false) List<String> types,
            @Parameter(description = "true（默认）只返回启用项，供下拉使用；false 连停用项一起返回")
            @RequestParam(required = false, defaultValue = "true") boolean enabledOnly) {
        Map<String, List<DictItem>> itemsByType = dictQueryService.batch(types, enabledOnly);
        return ApiResult.ok(dictResponseAssembler.toBatchResponse(itemsByType));
    }

    /**
     * 新增一个字典项。
     *
     * <p>新项以启用状态落库：请求体里没有 {@code status}，「建好就停用」没有真实用法，而多一个入参
     * 就多一条只能在事后被审计发现的路径。
     *
     * <p>{@code dictType} 取自路径而不是请求体：这个类型是本次新增的归属，写在 URL 上让「往哪个类型里加」
     * 在访问日志里就能看出来；请求体里再带一个同样的字段会出现两份归属。
     *
     * @param dictType 类型编码，必须存在且未删除
     * @param request  新增请求体
     * @return 新字典项的标识；字典类型不存在或已删除（40000）、该类型下已存在同值的未删除项（40900）、
     *         字段为空或超长、{@code sortNo} 省略（40000）通过业务错误码返回
     */
    @Operation(summary = "新增字典项",
            description = "在一个已存在的字典类型下新增取值为 value 的项。新项的 status 恒为 ACTIVE，不由调用方"
                    + "指定；dictType 与 value 是业务数据的引用键，建好之后不可修改（多传会被拒绝而不是忽略）。"
                    + "同一类型下已存在同值的未删除项返回 40900；已删除的同值项不占用取值，因此可以先删再建，"
                    + "新建的是另一行标识，历史数据引用的仍是同一取值。字典类型不存在或已被删除返回 40000"
                    + "（不是 40400：路径指向的类型清单本身是存在的，错的是这次提交的参数）。"
                    + "value、label 为空或超出 64 字符返回 40000；sortNo 省略同样返回 40000——列上虽有默认值 0，"
                    + "但按默认值处理会让漏传的项静默排到最前。响应只回带标识，不回带整行。")
    @SaCheckPermission("system:dict:update")
    @PostMapping("/{dictType}/items")
    public ApiResult<CreatedDictItemResponse> createItem(
            @Parameter(description = "字典类型编码，取自类型清单响应里的 dictType，如 COUNTRY")
            @PathVariable String dictType,
            @Valid @RequestBody DictItemCreateRequest request) {
        DictMaintenance.DictItemDraft draft = new DictMaintenance.DictItemDraft(
                request.label(), request.labelEn(), request.sortNo(), request.ext());
        return ApiResult.ok(dictResponseAssembler.toCreatedResponse(
                dictMaintenanceService.create(dictType, request.value(), draft)));
    }

    /**
     * 编辑字典项的可编辑字段。
     *
     * <p><b>全量替换</b>中文名、英文名、排序号与扩展属性：提交什么就是改后的值，{@code ext} 传 {@code null}
     * 表示清空。改不到 {@code dictType}、{@code value} 与 {@code status}：前两者是业务数据的引用键，
     * 状态走 {@link #changeItemStatus} 这个独立动作。
     *
     * <p>改完之后不回带详情：前端提交的就是它屏幕上那份数据，原样回显没有信息量；需要最新值时重新取列表。
     *
     * @param id      字典项标识
     * @param request 编辑请求体
     * @return 成功无数据；字典项不存在或已删除（40400）、中文名为空或超长、
     *         {@code sortNo} 省略（40000）通过业务错误码返回
     */
    @Operation(summary = "编辑字典项",
            description = "全量替换字典项的中文名、英文名、排序号与扩展属性：提交什么就是改后的值，"
                    + "labelEn 传 null 表示清空英文名，ext 传 null 表示清空扩展属性（落库为 {}）。"
                    + "dictType、value 与 status 不在可编辑范围内——前两者是业务数据的引用键，改了会让历史数据"
                    + "指向不存在的项，状态请走 PUT /items/{id}/status；多传这些字段会被拒绝而不是静默忽略。"
                    + "sortNo 必传，不按「不传就取 0」处理。字典项不存在或已被逻辑删除返回 40400，"
                    + "中文名为空或字段超长返回 40000。成功后不回带详情。")
    @SaCheckPermission("system:dict:update")
    @PutMapping("/items/{id}")
    public ApiResult<Void> updateItem(
            @Parameter(description = "字典项标识，取自字典项列表响应里的 id；标识全局唯一，因此路径里不再带类型编码")
            @PathVariable long id,
            @Valid @RequestBody DictItemUpdateRequest request) {
        DictMaintenance.DictItemDraft draft = new DictMaintenance.DictItemDraft(
                request.label(), request.labelEn(), request.sortNo(), request.ext());
        dictMaintenanceService.update(id, draft);
        // 不回带字典项：前端提交的就是它屏幕上那份数据；需要最新值时重新取列表
        return ApiResult.ok();
    }

    /**
     * 启用或停用字典项。
     *
     * <p>停用立即生效：读侧每次按状态过滤，停用项下一次请求就从「下拉」语义里消失，历史数据仍按它展示，
     * 不必重启也不依赖缓存失效。目标状态与当前状态相同时返回成功，不报错。
     *
     * @param id      字典项标识
     * @param request 目标状态
     * @return 成功无数据；字典项不存在或已删除（40400）通过业务错误码返回
     */
    @Operation(summary = "启用或停用字典项",
            description = "切换字典项状态。停用立即生效：读侧每次按状态过滤，停用项下一次请求就不再出现在"
                    + "enabledOnly=true 的结果里，而 enabledOnly=false 与历史数据回显仍能看到它，不必重启"
                    + "也不依赖缓存失效；启用同样立即恢复。目标状态与当前状态相同时返回成功，不报错——"
                    + "重复点「停用」不该收到错误。status 用枚举名而不是序号，传 null 与传未知编码都会被拒绝。"
                    + "字典项不存在或已被逻辑删除返回 40400。")
    @SaCheckPermission("system:dict:update")
    @PutMapping("/items/{id}/status")
    public ApiResult<Void> changeItemStatus(
            @Parameter(description = "字典项标识，取自字典项列表响应里的 id；标识全局唯一，因此路径里不再带类型编码")
            @PathVariable long id,
            @Valid @RequestBody DictItemStatusRequest request) {
        dictMaintenanceService.changeStatus(id, request.status());
        return ApiResult.ok();
    }

    /**
     * 删除字典项。
     *
     * <p>逻辑删除：行仍在库里，只置 {@code deleted_at}，删完列表与下拉都查不到它。物理删除会让
     * 「这条历史记录当时写的是哪个取值」无从追溯——那正是字典存在的意义。
     *
     * <p>不看当前状态：已停用的项可以直接删，不需要先启用。删掉之后同值可以重新新增
     * （{@code ux_sys_dict_item_value} 带 {@code deleted_at IS NULL} 条件），因此也不必为了腾出取值先动状态。
     *
     * @param id 字典项标识
     * @return 成功无数据；字典项不存在、已被删除，或判存在之后被其他请求删除（40400）通过业务错误码返回
     */
    @Operation(summary = "删除字典项",
            description = "删除一个字典项，采用逻辑删除（置 deleted_at，行仍在库里）：删除后列表与下拉都查不到它，"
                    + "而引用过该取值的历史数据仍能解释。不物理删除是因为 value 可能被历史业务数据引用，"
                    + "删掉记录之后「这条当时写的是哪个取值」就无从追溯。删除不看当前状态，已停用的项可以直接删；"
                    + "删掉之后同一类型下同值可以重新新增，新建的是另一行标识。字典项不存在、已被删除，"
                    + "或判存在之后被其他请求抢先删除，均返回 40400（提示语区分这两种「已经不在」）。"
                    + "成功后不回带被删对象。")
    @SaCheckPermission("system:dict:update")
    @DeleteMapping("/items/{id}")
    public ApiResult<Void> deleteItem(
            @Parameter(description = "字典项标识，取自字典项列表响应里的 id；标识全局唯一，因此路径里不再带类型编码")
            @PathVariable long id) {
        dictMaintenanceService.delete(id);
        // 不回带被删对象：它已经查不到了，回显一份只存在于响应里的数据没有意义
        return ApiResult.ok();
    }
}

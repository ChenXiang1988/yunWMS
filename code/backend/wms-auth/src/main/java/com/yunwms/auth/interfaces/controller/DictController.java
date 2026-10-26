package com.yunwms.auth.interfaces.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.yunwms.auth.application.query.DictQueryService;
import com.yunwms.auth.domain.model.DictItem;
import com.yunwms.auth.domain.model.DictTypeSummary;
import com.yunwms.auth.interfaces.controller.assembler.DictResponseAssembler;
import com.yunwms.auth.interfaces.controller.response.DictItemResponse;
import com.yunwms.auth.interfaces.controller.response.DictTypeListItemResponse;
import com.yunwms.web.api.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端的字典读取入口：类型清单、某类字典项、多类型批量读取。
 *
 * <p>路径前缀 {@code /api/console} 表示仓网端：字典是全系统共用的配置数据，货主端没有维护能力，
 * 也没有绕开管理端读取它的理由。
 *
 * <p><b>为什么只有读接口</b>：新增、编辑、启停属于 2.7b。本批的目标是「建好数据 + 能按四种语义
 * 读出来」，等维护接口落地后再一次性补全写入面，避免半套维护入口被前端先接上。
 *
 * <p><b>为什么三个接口共用一个权限点</b> {@code system:dict:list}：能看到类型清单的人本来就能
 * 逐类点进去看项，拆成三个点只会多两次配置而没有边界收益。权限点由 {@code V031} 定义，
 * 注解由 {@code SaTokenAnnotationConfig} 注册的拦截器消费。
 */
@Tag(name = "数据字典", description = "字典的读取入口：类型清单、某类字典项、多类型批量读取。"
        + "同一个接口用 enabledOnly 承载两种消费语义——下拉只要启用项，历史数据回显要连停用项一起取，"
        + "因此不拆成两个端点；双语名称同时返回，由前端按当前界面语言取值，后端不做回退。")
@RestController
@RequestMapping("/api/console/dicts")
public class DictController {

    private final DictQueryService dictQueryService;
    private final DictResponseAssembler dictResponseAssembler;

    public DictController(DictQueryService dictQueryService, DictResponseAssembler dictResponseAssembler) {
        this.dictQueryService = dictQueryService;
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
}

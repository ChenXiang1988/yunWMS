package com.yunwms.auth.interfaces.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 新增字典项的请求体：{@code {"value": "CHILLED", "label": "冷藏", "labelEn": "Chilled", "sortNo": 10}}。
 *
 * <p><b>{@code value} 只在这里出现，编辑请求体里没有。</b>它是业务数据的引用键——{@code owner.owner_type}、
 * {@code owner_contact.role_type} 里存的就是它。建好之后改掉等于让历史数据指向一个不存在的项，而这种损坏
 * 在界面上看不出来（记录还在，只是显示不出名字）。因此它只在新增时给一次，之后由
 * {@code DictItemDraft} 的形状把它挡在编辑之外。
 *
 * <p><b>没有 {@code dictType} 与 {@code status}：</b>前者在路径里（{@code POST /{dictType}/items}），
 * 后者不由调用方决定——新建的项一律启用，理由见 {@code DictMaintenance#insert}。多传这两个字段会被
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} 直接拒绝，而不是「收下但忽略」：忽略会让前端以为建出了停用项。
 *
 * <p><b>{@code sortNo} 必传。</b>列上有 {@code DEFAULT 0}，省略字段不会报错，而是把这一项静默排到最前
 * ——管理员看不到任何提示，只能事后发现下拉顺序不对。后端也没有依据替使用者决定「这一项该插到队首」，
 * 因此把「没决定排序」这件事在入口打回。
 *
 * <p><b>{@code ext} 不在入口兜底：</b>可空，{@code null} 由 {@code DictItemDraft} 的紧凑构造器归一成
 * 不可变空 Map。入口再判一次 null 是同一份数据校验两遍（{@code docs/coding.md}「校验与异常」）。
 *
 * @param value   取值，必填，最长 64 字符；同一类型下的未删除项里唯一，建好之后不可改
 * @param label   中文名，必填，最长 64 字符
 * @param labelEn 英文名，可为空，最长 64 字符；缺失由前端回退到中文名，后端不代填
 * @param sortNo  排序号，必传，越小越靠前；允许与其他项重复
 * @param ext     扩展属性，可为空；需要层级的字典在 {@code ext.parent} 里写父项的 value
 */
public record DictItemCreateRequest(
        @NotBlank(message = "字典项取值不能为空")
        @Size(max = 64, message = "字典项取值不能超过 64 个字符")
        @Schema(description = "取值，必填，最长 64 个字符，如 CHILLED；业务数据存的就是它，建好之后不可修改。"
                + "同一字典类型下的未删除项里唯一，重复返回 40900（已删除的同值项不占用取值）")
        String value,

        @NotBlank(message = "字典项中文名不能为空")
        @Size(max = 64, message = "字典项中文名不能超过 64 个字符")
        @Schema(description = "中文名，必填，最长 64 个字符，如「冷藏」；给人看的名字，同一类型下可重名")
        String label,

        @Size(max = 64, message = "字典项英文名不能超过 64 个字符")
        @Schema(description = "英文名，可为 null，最长 64 个字符。后端不做语言回退：为空时由前端取中文名，"
                + "整串空白与不传同处理（归一成 null）")
        String labelEn,

        @NotNull(message = "排序号不能为空")
        @Schema(description = "排序号，必传，越小越靠前，不能为 null。列上虽有默认值 0，但省略字段会让这一项"
                + "静默排到最前，因此不按「不传就取 0」处理；允许与其他项重复，同号时按标识升序")
        Integer sortNo,

        @Schema(description = "扩展属性，可为 null，原样落库为 jsonb；传 null 与不传都按 {} 处理。"
                + "行政区划等层级字典在 parent 里写父项的 value，如 {\"parent\": \"US\"}")
        Map<String, Object> ext) {
}

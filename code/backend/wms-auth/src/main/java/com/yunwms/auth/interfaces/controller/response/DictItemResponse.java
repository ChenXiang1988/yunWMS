package com.yunwms.auth.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 一个字典项。
 *
 * <p>{@code label} 与 {@code labelEn} 同时返回，后端<b>不做语言回退</b>：接口不知道调用方
 * 用的是哪个界面语言。英文界面取 {@code labelEn}，为空时用 {@code label}——这条规则属于展示层，
 * 放在后端会让「英文界面看到中文」从「数据缺英文名」变成「某处代码回退过」，无法排查。
 *
 * @param id      项标识，字符串形式的十进制整数（超过 2^53 的整数在 JS 里会丢精度）
 * @param label   中文名
 * @param labelEn 英文名，可为 {@code null}
 * @param value   取值，业务数据存的就是它
 * @param sortNo  排序号，越小越靠前
 * @param status  项状态编码：{@code ACTIVE} 启用，{@code DISABLED} 停用
 * @param ext     扩展属性，原样透传，恒不为 {@code null}；需要层级的字典在 {@code ext.parent} 里
 *                写父项的 value
 */
public record DictItemResponse(
        @Schema(description = "字典项标识，字符串形式的十进制整数")
        String id,

        @Schema(description = "中文名，如「冷藏」")
        String label,

        @Schema(description = "英文名，可为 null；前端按当前界面语言取值，为空时回退到 label")
        String labelEn,

        @Schema(description = "取值，如 CHILLED；业务数据存的就是这个值")
        String value,

        @Schema(description = "排序号，越小越靠前；同排序号时按标识升序")
        int sortNo,

        @Schema(description = "项状态编码：ACTIVE 启用，DISABLED 停用。停用项不出现在下拉里，历史数据仍按它展示")
        String status,

        @Schema(description = "扩展属性，原样透传；没有扩展属性时为 {}；行政区划等层级字典用 parent 指向父项的 value")
        Map<String, Object> ext) {
}

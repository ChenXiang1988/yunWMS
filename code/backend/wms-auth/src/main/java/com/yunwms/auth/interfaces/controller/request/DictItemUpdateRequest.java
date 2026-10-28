package com.yunwms.auth.interfaces.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 编辑字典项的请求体：{@code {"label": "冷藏", "labelEn": "Chilled", "sortNo": 10, "ext": {}}}。
 *
 * <p><b>全量替换</b>这四个可编辑字段：提交什么就是改后的值。{@code labelEn} 传 {@code null} 表示清空英文名，
 * {@code ext} 传 {@code null} 表示清空扩展属性——请求体里没有「不传就不改」的字段，因为
 * {@code DictMaintenance#update} 的 SET 列表固定是这四列，部分更新语义在接口与用例之间没有落点。
 *
 * <p><b>没有 {@code value} 与 {@code dictType}：</b>两者是业务数据的引用键，改掉会让
 * {@code owner.owner_type} 这类历史列指向一个不存在的项。多传会被
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} 拒绝而不是静默忽略——忽略会让前端以为改了。状态走独立端点
 * {@code PUT /items/{id}/status}：停用的语义是「从下拉里消失」，它不该顺带改标签或排序。
 *
 * <p><b>{@code sortNo} 必传。</b>与新增同一口径：列上的 {@code DEFAULT 0} 会让漏传的项静默排到最前，
 * 后端没有依据替使用者决定排序。{@code DictItemDraft} 收 {@code int}，入口的 {@code @NotNull} 先拦下
 * {@code null}，因此拆箱不会 NPE。
 *
 * <p>{@code ext} 可空，{@code null} 由 {@code DictItemDraft} 的紧凑构造器归一成不可变空 Map，
 * 入口不重复兜底。
 *
 * @param label   中文名，必填，最长 64 字符
 * @param labelEn 英文名，可为空，最长 64 字符；传 {@code null} 表示清空
 * @param sortNo  排序号，必传，越小越靠前；允许与其他项重复
 * @param ext     扩展属性，可为空；传 {@code null} 表示清空（落库为 {@code {}}）
 */
public record DictItemUpdateRequest(
        @NotBlank(message = "字典项中文名不能为空")
        @Size(max = 64, message = "字典项中文名不能超过 64 个字符")
        @Schema(description = "中文名，必填，最长 64 个字符。全量替换语义：提交什么就是改后的值")
        String label,

        @Size(max = 64, message = "字典项英文名不能超过 64 个字符")
        @Schema(description = "英文名，可为 null，最长 64 个字符。全量替换语义：传 null 表示清空，"
                + "不传同样按 null 处理；后端不做语言回退")
        String labelEn,

        @NotNull(message = "排序号不能为空")
        @Schema(description = "排序号，必传，越小越靠前，不能为 null。全量替换语义；允许与其他项重复，"
                + "同号时按标识升序")
        Integer sortNo,

        @Schema(description = "扩展属性，可为 null，原样落库为 jsonb。全量替换语义：传 null 表示清空"
                + "（落库为 {}），不传同样按 null 处理")
        Map<String, Object> ext) {
}

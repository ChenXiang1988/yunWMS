package com.yunwms.master.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 终止影响清单的响应体。
 *
 * <p>两个计数都是数量而不是标识列表，因此用数字传输——{@code docs/api-convention.md} 第五节只要求
 * <b>标识</b>以字符串传输（避免 JS 丢精度），计数没有这个问题。
 *
 * <p>两个字段恒有值：没有商品就是 {@code 0}，不返回 {@code null}。前端据此可以直接渲染
 * 「影响 0 个商品与 0 个账号」，不需要再判空。
 *
 * @param skuCount     该货主下仍存在的商品数，排除已逻辑删除的商品
 * @param accountCount 该货主下仍存在的账号数，含已停用的账号，排除已逻辑删除的账号
 */
public record OwnerTerminationImpactResponse(

        @Schema(description = "该货主下仍存在的商品数，排除已删除的商品。终止不会修改这些商品的状态，"
                + "它们保留但不可用于新的业务单据", example = "3")
        long skuCount,

        @Schema(description = "该货主下仍存在的账号数，停用的账号也计入，排除已删除的账号。"
                + "终止不会停用这些账号", example = "2")
        long accountCount) {
}

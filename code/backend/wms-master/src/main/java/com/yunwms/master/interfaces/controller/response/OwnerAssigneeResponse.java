package com.yunwms.master.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 货主当前负责客服的响应体，配置弹窗回显用它。
 *
 * <p><b>三个标识一律以字符串传输：</b>{@code Long} 超过 2^53 时 JS 会丢精度，等到出问题再改要动全部前端，
 * 而首日就按字符串发没有任何代价（{@code docs/api-convention.md} 第五节）。与
 * {@code OwnerContactResponse}、{@code CreatedOwnerResponse} 同一约定。
 *
 * <p><b>某个角色还没有人时输出 {@code null}，不输出 {@code "null"} 也不输出空串：</b>三种「空」在
 * 前端分别意味着「没有值」「字符串 'null'」「什么都没有，或真的清空了」，混用会让配置弹窗把
 * 「还没配负责人」显示成一名标识为 null 的员工。转换由装配器做，见
 * {@code OwnerResponseAssembler#toAssigneeResponse}。
 *
 * <p><b>回带 {@code ownerId}，与 {@code OwnerContactResponse} 不同：</b>那个响应省略它，因为归属就在路径上；
 * 这里多带一份的理由是乱序响应防护——配置弹窗会随货主切换复用同一个组件，先发的请求可能在切换之后才返回，
 * 前端凭这个字段就能判断「手里这份配置是不是当前货主的」并丢弃过期响应。它不是归属的第二处真源：
 * 写入口的归属只取路径，读侧的这个值由用例按被查询的货主回填。
 *
 * <p><b>不含员工姓名：</b>姓名在 {@code sys_user}，归 {@code wms-auth}；本模块只经 {@code StaffLookup}
 * 拿到「是不是在册员工」这一个是/否，拿不到档案字段。要展示姓名的调用方自己声明它需要的查询能力，
 * 本类型不替它把姓名抄一份存下来——抄下来的那份迟早与用户表不一致（理由见 {@code OwnerAssigneeView}）。
 *
 * @param ownerId         货主标识，字符串形式的十进制整数；与请求路径上的 {@code ownerId} 相同，
 *                        供前端判断这份配置是否属于当前货主
 * @param primaryUserId   主负责人的员工标识，字符串形式的十进制整数；该货主尚未配置负责人时为 {@code null}
 * @param secondaryUserId 副负责人的员工标识，字符串形式的十进制整数；该货主没有副负责人时为 {@code null}
 */
public record OwnerAssigneeResponse(
        @Schema(description = "货主标识，字符串形式的十进制整数；与请求路径上的 ownerId 相同，"
                + "供前端丢弃切换货主之后才返回的过期响应", example = "3")
        String ownerId,

        @Schema(description = "主负责人的员工标识，字符串形式的十进制整数；该货主尚未配置负责人时为 null。"
                + "员工姓名不在本响应里——它在 wms-auth 的用户表，需要时另行查询", example = "7")
        String primaryUserId,

        @Schema(description = "副负责人的员工标识，字符串形式的十进制整数；该货主没有副负责人时为 null",
                example = "9")
        String secondaryUserId) {
}

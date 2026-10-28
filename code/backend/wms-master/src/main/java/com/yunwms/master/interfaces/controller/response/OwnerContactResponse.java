package com.yunwms.master.interfaces.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 货主联系人的响应体，列表与新增共用一份。
 *
 * <p><b>标识以字符串传输：</b>{@code Long} 超过 2^53 时 JS 会丢精度，等到出问题再改要动全部前端，
 * 而首日就按字符串发没有任何代价（{@code docs/api-convention.md} 第五节）。与
 * {@code CreatedOwnerResponse}、{@code OwnerListItemResponse} 同一约定。
 *
 * <p><b>没有 {@code ownerId}：</b>它就在请求路径上（{@code /owners/{ownerId}/contacts}）。
 * 响应里再带一份，等于给前端两个可能不一致的来源——某次编辑之后前端手里有两处 {@code ownerId}，
 * 用哪一个去拼下一次请求就成了没有依据的选择，而这处不一致只有在真出错时才会被发现。
 *
 * <p><b>不回带审计列与创建时间：</b>联系人页签展示的是「找谁」，{@code created_by} / {@code updated_at}
 * 由审计留痕负责，读侧没有第二个消费者（理由与 {@code OwnerContact} 不搬审计列一致）。
 *
 * @param id        联系人标识，字符串形式的十进制整数；编辑、删除与设为主联系人都用它定位
 * @param name      联系人姓名，可重复——姓名不是标识
 * @param roleType  角色取值，取 {@code CONTACT_ROLE} 字典的 value，如 {@code BUSINESS}
 * @param phone     联系电话；模型必填，因此响应里不会为 {@code null}
 * @param email     邮箱，未填时为 {@code null}
 * @param isPrimary 是否主联系人；同一货主至多一条为 {@code true}
 * @param remark    备注，未填时为 {@code null}
 */
public record OwnerContactResponse(
        @Schema(description = "联系人标识，字符串形式的十进制整数；编辑、删除都用它定位", example = "12")
        String id,

        @Schema(description = "联系人姓名，可与其他联系人重名——姓名不是标识；按原样返回，不去首尾空格",
                example = "张三")
        String name,

        @Schema(description = "角色取值，取自 CONTACT_ROLE 字典的 value：BUSINESS 业务对接人、"
                + "FINANCE 财务联系人、LOGISTICS 物流联系人、ONSITE 现场联系人、RETURN 退货联系人；"
                + "展示时按字典取中文标签，接口只给 value", example = "BUSINESS")
        String roleType,

        @Schema(description = "联系电话，必填字段，不会为 null；原样返回，不做脱敏与格式化",
                example = "13800001111")
        String phone,

        @Schema(description = "邮箱，可为 null（未填或已清空）；原样返回，不做脱敏", example = "zhangsan@example.com")
        String email,

        @Schema(description = "是否主联系人，同一货主至多一条为 true；页签上的「主」标记用它渲染",
                example = "true")
        boolean isPrimary,

        @Schema(description = "备注，可为 null（未填或已清空）", example = "工作日 9:00-18:00 接听")
        String remark) {
}

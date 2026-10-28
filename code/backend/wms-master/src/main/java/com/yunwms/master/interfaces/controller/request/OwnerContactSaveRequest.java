package com.yunwms.master.interfaces.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 货主联系人的写入请求体，新增与编辑共用一份。
 *
 * <p><b>没有 {@code ownerId}：</b>归属从路径
 * {@code /api/console/owners/{ownerId}/contacts} 取。请求体里再带一份就出现「路径说 A、身体说 B」，
 * 而两边不一致时没有依据说哪一处算准；编辑时更糟——服务的归属判定根本不读它，
 * 字段却出现在契约里，调用方会以为自己改了归属。
 *
 * <p><b>不在这里写「角色是否属于 {@code CONTACT_ROLE} 字典」的校验：</b>那条规则已由
 * {@code OwnerContactService#create} / {@code #update} 用字典判定，并给出带原值的 40000 提示。
 * 在这里再用正则或枚举表达一遍就是两处真源：字典新增角色项后注解放不过，或者注解与字典对
 * 「停用项算不算存在」判得不一样——两种症状都只在运行期出现。本类只用标准注解表达单字段约束
 * （必填与列宽），取值是否合法交给持有那条规则的那一处。
 *
 * <p><b>列宽取自 {@code V007__owner_contact.sql} 的列定义：</b>{@code name} 64、
 * {@code roleType} 20、{@code phone} 32、{@code email} 128、{@code remark} 255。
 * 没有这些约束，超长输入会以数据库的 {@code 22001} 出现在 50000 上，而那是一次纯粹的入参错误。
 *
 * <p><b>{@code isPrimary} 用 {@code Boolean} 而不是 {@code boolean}：</b>让「没传」这件事在类型上可见。
 * 新增与编辑都是全量替换，缺省按 {@code false}（不是主联系人）处理——这是唯一不产生连带改动的默认值，
 * 换成 {@code true} 会让「漏传一个字段」变成「把别人的主标记清掉了」。转换在
 * {@code OwnerContactController} 里用 {@code Boolean.TRUE.equals} 写死一次，读方法签名就能读到这个口径。
 *
 * <p>五个字段原样提交、原样入库，接口层不裁首尾空格：整串空白会在领域层被归一为 {@code null}
 * （{@code email} 与 {@code remark} 因此按「没填」处理），而有内容的串一律保留用户的真实输入。
 *
 * @param name      联系人姓名，必填，最长 64 个字符
 * @param roleType  联系人角色，必填，取 {@code CONTACT_ROLE} 字典的 value，最长 20 个字符
 * @param phone     联系电话，必填，最长 32 个字符
 * @param email     邮箱，可为空，最长 128 个字符
 * @param isPrimary 是否主联系人；缺省按 {@code false} 处理，置 {@code true} 会先清掉该货主原有的主联系人
 * @param remark    备注，可为空，最长 255 个字符
 */
public record OwnerContactSaveRequest(

        @NotBlank(message = "联系人姓名不能为空")
        @Size(max = 64, message = "联系人姓名不能超过 64 个字符")
        @Schema(description = "联系人姓名，必填，最长 64 个字符；只填空白按未填处理。姓名可以重复——"
                + "它是联系人的称呼，不是标识", example = "张三")
        String name,

        @NotBlank(message = "联系人角色不能为空")
        @Size(max = 20, message = "联系人角色不能超过 20 个字符")
        @Schema(description = "联系人角色，必填，取 CONTACT_ROLE 字典的启用项：BUSINESS 业务对接人、"
                + "FINANCE 财务联系人、LOGISTICS 物流联系人、ONSITE 现场联系人、RETURN 退货联系人。"
                + "空白、停用项或未知取值均返回 40000，提示里带提交的原值", example = "BUSINESS")
        String roleType,

        @NotBlank(message = "联系电话不能为空")
        @Size(max = 32, message = "联系电话不能超过 32 个字符")
        @Schema(description = "联系电话，必填，最长 32 个字符；只填空白按未填处理。按原样保存，"
                + "不做格式校验——国内外号码形态不一，格式校验会把能打通的号码挡在外面",
                example = "13800001111")
        String phone,

        @Size(max = 128, message = "联系人邮箱不能超过 128 个字符")
        @Schema(description = "邮箱，可为空，最长 128 个字符；传 null 或整串空白表示清空。"
                + "按原样保存，不做格式校验", example = "zhangsan@example.com")
        String email,

        @Schema(description = "是否主联系人，缺省（不传）按 false 处理。置 true 时接口会先把该货主原有的"
                + "主联系人置为非主再写入本条，因此一个货主至多一条主联系人。要保留一条已有的主标记必须"
                + "显式传 true——本接口是全量替换，省略该字段等于提交 false", example = "true")
        Boolean isPrimary,

        @Size(max = 255, message = "联系人备注不能超过 255 个字符")
        @Schema(description = "备注，可为空，最长 255 个字符；传 null 或整串空白表示清空",
                example = "工作日 9:00-18:00 接听")
        String remark) {
}

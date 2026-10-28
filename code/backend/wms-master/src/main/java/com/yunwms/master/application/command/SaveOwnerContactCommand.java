package com.yunwms.master.application.command;

/**
 * 货主联系人的写入入参：新增与编辑共用一份。
 *
 * <p><b>为什么新增与编辑共用一个类型：</b>联系人的五个可写字段在两条入口上完全一致——没有编码、
 * 没有状态机，唯一带状态味道的 {@code isPrimary} 恰恰<b>必须</b>可改（换主联系人就是改这一位），
 * 理由与 {@code OwnerContactDraft} 的类注释同源。为编辑单独再造一个 record 只多一次逐字段转写，
 * 而转写漏一个字段的后果是「改了个电话，邮箱被清空」——这类缺陷不会被编译拦下。
 *
 * <p><b>本类型不做字段校验，也不归一空白串：</b>必填与列宽由 {@code OwnerContactDraft} 的构造器判，
 * 入口（接口层的声明式约束）只校验一次；同一个规则在两个类型里各实现一遍，两处对「什么算空白」
 * 迟早判得不一样。这里尤其不能借用 {@code OwnerTextNormalizer}：它会裁掉两侧空格，而
 * {@code OwnerContactDraft} 刻意保留非空白串的原样（{@code " 张三 "} 照原样入库），
 * 在这里裁一次，落库的值与提交的值就不一致，审计里也解释不清「库里为什么和填的不一样」。
 *
 * <p><b>编辑时 {@link #ownerId} 不参与任何判定：</b>归属以库里那一行为准（用例先按联系人标识取出
 * {@code ownerId} 再判货主可见性），否则调用方自报一个归属就能改到别人的联系人。保留该字段而不是
 * 为编辑另造类型，理由见上一条。
 *
 * @param ownerId   所属货主标识，仅新增时使用
 * @param name      联系人姓名，必填，最长 64 字符
 * @param roleType  联系人角色，取 {@code CONTACT_ROLE} 字典的 value（{@code BUSINESS} / {@code FINANCE} /
 *                  {@code LOGISTICS} / {@code ONSITE} / {@code RETURN}），必填，最长 20 字符
 * @param phone     联系电话，必填，最长 32 字符
 * @param email     邮箱，可为 {@code null}，最长 128 字符
 * @param isPrimary 是否主联系人；置 {@code true} 时用例会先清掉该货主原有的主联系人
 * @param remark    备注，可为 {@code null}，最长 255 字符
 */
public record SaveOwnerContactCommand(
        long ownerId,
        String name,
        String roleType,
        String phone,
        String email,
        boolean isPrimary,
        String remark) {
}

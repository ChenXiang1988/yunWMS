package com.yunwms.master.domain.model;

/**
 * 货主联系人的写入模型：新建与更新共用的输入值对象。
 *
 * <p><b>为什么新建与更新共用一个类型，而货主那边分 {@link NewOwner} 与 {@link OwnerProfile}：</b>
 * 那两个类型的差异不是形式上的「新增 vs 编辑」，而是**可写字段集**不同——{@code code}、{@code skuPrefix}
 * 创建后不可改，{@code status} 属状态机，所以编辑入口必须拿不到它们。联系人的五个字段在新建与编辑时
 * 完全一致：没有编码，没有状态机，没有创建后锁定的字段；唯一带状态味道的 {@code isPrimary}
 * 恰恰<b>必须</b>可改（换主联系人就是改这一位）。这种情况下拆成两个类型只多一次逐字段转写，
 * 而转写漏一个字段的后果是「改了个电话，邮箱被清空」——这种缺陷不会被编译拦下。
 * 差异真正出现时（例如联系人也要走启用/停用），再按那两个类型的做法拆，而不是现在就预建。
 *
 * <p><b>为什么 {@code phone} 在本类里必填，而 {@code owner_contact.phone} 在库里可空：</b>
 * 规格（{@code openspec/specs/customer-management/spec.md}「Requirement: 货主联系人」）写的是
 * 「每条记录 MUST 包含角色、姓名与联系电话」，而 {@code V007} 建表时把 {@code phone} 留成了可空，
 * 也没有后续迁移收紧它。两者冲突时以规格为准，因此在<b>写入侧</b>拒绝空电话。
 * 但本任务<b>不改表、不加 {@code NOT NULL} 迁移</b>：收紧列约束需要先处理存量空值，
 * 是一次「数据修复 + 迁移」的独立工作，与「建类型」不是一件事
 * （{@code docs/coding-java.md}「数据库迁移」：一次迁移一个文件，且不得与代码改动混提交）。
 * 代价必须说清楚：库里若真的存在 {@code phone IS NULL} 的行，读出来会在
 * {@link OwnerContact} 的构造器上当场失败，而不是被静默展示成一条没有电话的联系人。
 * 这是刻意的——静默放行会让「货主联系人必须留电话」这条规则在事实上只对新数据生效，
 * 而且没有人知道哪些货主的联系人打不通。
 *
 * <p><b>为什么归属不在这里：</b>本类没有 {@code ownerId}。新建时「挂到哪个货主」由
 * {@code OwnerContactRepository#insert} 的入参决定，编辑时归属根本不可改（端口方法拿不到它），
 * 因此不存在「一次编辑把联系人搬到另一个货主名下」的路径，而不是靠注释提醒别写那一列。
 * {@code id} 同理由库生成，{@link OwnerContact} 的标识是「已落库」的既成事实。
 *
 * <p><b>空白串归一为 {@code null}，但非空白串原样保留：</b>六个字段先做一次
 * 「整串空白 → {@code null}」，再判不变量。归一是因为 {@code email} 与 {@code remark} 可空，
 * 而整串空白在业务上与「没填」没有区别，存下 {@code ' '} 只会让「填了没有」的判空永远为真，
 * 展示层把它渲染成一个点不开的空链接；必填三项同样先归一，因此「只填了空格」得到的是
 * 「不能为空」而不是一条合法记录。反过来，非空白串一律不去首尾空格（{@code " 张三 "} 照原样保存）：
 * 那是用户的真实输入，领域层悄悄改写会让审计无法解释「库里的值和提交的不一致」。
 * 与 {@link OwnerAddress} 的区别在于它归一时只看「是否整串空白」，不改写任何有内容的字符，
 * 该处的顾虑（分不清「填了空格」与「没填」）在可空字段上不成立。
 *
 * <p>本类不判「角色是否属于 {@code CONTACT_ROLE} 字典的启用项」：那需要查字典，属服务层的
 * {@code OwnerDictionaryValidator} 同族校验，与 {@link NewOwner} 不判 {@code OWNER_TYPE} 一致。
 * 也不判「该货主是否已有主联系人」：那是 {@code ux_owner_contact_primary} 的事，
 * 端口里的 {@code clearPrimary} 负责先腾出位置，模型不重复判定。
 *
 * @param name      联系人姓名，必填，最长 64 字符
 * @param roleType  角色，取 {@code CONTACT_ROLE} 字典的 value（{@code BUSINESS} / {@code FINANCE} /
 *                  {@code LOGISTICS} / {@code ONSITE} / {@code RETURN}），必填，最长 20 字符
 * @param phone     联系电话，必填，最长 32 字符
 * @param email     邮箱，可为 {@code null}，最长 128 字符
 * @param isPrimary 是否主联系人；置 {@code true} 前须由调用方先清掉旧的主联系人
 * @param remark    备注，可为 {@code null}，最长 255 字符
 */
public record OwnerContactDraft(
        String name,
        String roleType,
        String phone,
        String email,
        boolean isPrimary,
        String remark) {

    /**
     * 归一空白串并校验创建期不变量。
     *
     * <p>这是最后一道防线：用例层会先判一次并给出可展示的业务错误，这里拦的是绕过用例直接构造
     * （将来的批量导入、数据修复脚本）把脏数据写下去。规则本体在
     * {@link OwnerContact#requireFields}，本类只多做一步归一。
     *
     * @throws IllegalArgumentException 姓名、角色或电话为空，或任一字段超出列宽
     */
    public OwnerContactDraft {
        name = blankToNull(name);
        roleType = blankToNull(roleType);
        phone = blankToNull(phone);
        email = blankToNull(email);
        remark = blankToNull(remark);
        OwnerContact.requireFields(name, roleType, phone, email, remark);
    }

    /**
     * 把整串空白归一为 {@code null}，其余值原样返回。
     *
     * <p>只做「整串是否空白」这一个判断，不裁剪：理由见类注释「空白串归一为 {@code null}，
     * 但非空白串原样保留」一段。
     *
     * @param value 原始输入，可为 {@code null}
     * @return 整串空白时为 {@code null}，否则为原值
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

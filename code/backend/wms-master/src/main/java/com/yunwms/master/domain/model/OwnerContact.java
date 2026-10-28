package com.yunwms.master.domain.model;

/**
 * 货主联系人，承载从库中读到的既成事实。
 *
 * <p><b>为什么与 {@link OwnerContactDraft} 分成两个类型：</b>标识与归属只在落库之后才存在。
 * 若用一个 {@code id=0}、{@code ownerId=0} 的实例冒充「待插入」，读代码的人无法从签名上分辨
 * 「已经是某货主名下的联系人」与「即将成为联系人的一堆字段值」，也会诱使后续代码直接拿它去更新
 * 而不校验它到底有没有落库。这与 {@link Owner} / {@link NewOwner} 的分工是同一条理由，
 * 而不是因为「领域模型和入参模型本来就该分开」这种没有落点的说法。
 *
 * <p><b>这里为什么没有审计四列：</b>{@code created_by} / {@code updated_by} 等由仓储在写入时维护，
 * 读侧用途是货主详情的联系人页签，它展示的是联系人与角色，不展示「谁改的」。
 * 把审计列搬进本类型会让它变成审计快照的第二个来源，而审计留痕已经由 {@code AuditTrail} 承担
 * （见 {@link OwnerAuditEvent}）。将来真需要展示时再加，加的时候所有构造点会一起编译失败，漏不掉。
 *
 * <p><b>重建路径同样校验：</b>紧凑构造器调用 {@link #requireFields}，与
 * {@link OwnerContactDraft} 用的是同一份实现——创建期被拒的组合，重建期也不会被放行。
 * 库里读到脏数据（典型是 {@code phone} 为 {@code NULL} 的历史行，见 {@link OwnerContactDraft}
 * 关于「模型必填而列可空」的说明）时在这里当场失败，而不是被下游当成一条没有电话的合法联系人展示出去。
 * 这也意味着 {@code NOT NULL} 与 {@code varchar} 宽度不是本类的重复校验：库只拦写入，拦不住已存在的、
 * 或绕过约束写入的行，那时唯一的发现点就是这里。
 *
 * <p>本类型不提供任何改法：编辑走 {@link OwnerContactDraft} 加
 * {@code OwnerContactRepository#update}，新值是一次写入，而不是就地改字段，
 * 这样「谁在什么时候把主联系人换成了别人」留在 {@code updated_by} / {@code updated_at} 上。
 * 主联系人的唯一性不在这里判——它是 {@code ux_owner_contact_primary} 的职责，见
 * {@code OwnerContactRepository} 的类注释。
 *
 * @param id        标识，数据库生成
 * @param ownerId   所属货主标识；联系人不是独立聚合，归属创建后不可修改
 * @param name      联系人姓名，必填，最长 64 字符
 * @param roleType  角色，取 {@code CONTACT_ROLE} 字典的 value（如 {@code BUSINESS}），最长 20 字符
 * @param phone     联系电话，必填，最长 32 字符；列在库里可空，本类型不接受 {@code null}
 * @param email     邮箱，可为 {@code null}，最长 128 字符
 * @param isPrimary 是否主联系人；同一货主至多一条，由部分唯一索引保证
 * @param remark    备注，可为 {@code null}，最长 255 字符
 */
public record OwnerContact(
        long id,
        long ownerId,
        String name,
        String roleType,
        String phone,
        String email,
        boolean isPrimary,
        String remark) {

    /**
     * 校验读到的事实。
     *
     * @throws IllegalArgumentException 姓名、角色或电话为空，或任一字段超出列宽
     */
    public OwnerContact {
        requireFields(name, roleType, phone, email, remark);
    }

    /**
     * 校验联系人的字段不变量，创建（{@link OwnerContactDraft}）与重建（本记录）共用这一份实现。
     *
     * <p>包内可见而不是 {@code public}：规则一旦有公开入口，就会有人拿它当「校验工具」在别处调用，
     * 而它只判这五个字段，既不判角色是否取自字典（要查字典，属服务层），
     * 也不判主联系人是否已存在（属数据库唯一索引）。这与 {@link Owner#requireAttributes} 的处理一致。
     *
     * <p>宽度取自 {@code V007__owner_contact.sql} 的列定义（{@code varchar} 宽度），写在这里而不是
     * 在调用方：改列宽时只改这一处，否则「创建放过、重建拦下」或反之的错位会一直没有人发现。
     *
     * @param name     联系人姓名，必填
     * @param roleType 角色值，必填
     * @param phone    联系电话，必填
     * @param email    邮箱，可为 {@code null}
     * @param remark   备注，可为 {@code null}
     * @throws IllegalArgumentException 姓名、角色或电话为 {@code null} 或空白，或任一字段超出列宽
     */
    static void requireFields(String name, String roleType, String phone, String email, String remark) {
        requirePresent(name, "联系人姓名", 64);
        requirePresent(roleType, "联系人角色", 20);
        requirePresent(phone, "联系电话", 32);
        requireMaxLength(email, "联系人邮箱", 128);
        requireMaxLength(remark, "联系人备注", 255);
    }

    /**
     * 校验必填字段非空白且不超长。
     *
     * <p>判空白而不是只判 {@code null}：{@code NOT NULL} 拦不住空串，而空姓名与空电话在页面上
     * 就是一行空白，用户看到的是「数据在但没用」，那是最难被报上来的一类脏数据。
     *
     * @param value     字段值
     * @param label     字段的中文名，用于拼出可直接展示的错误信息
     * @param maxLength 列宽上限
     * @throws IllegalArgumentException 值为 {@code null} 或空白，或超出列宽
     */
    private static void requirePresent(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        requireMaxLength(value, label, maxLength);
    }

    /**
     * 校验字段不超出列宽；{@code null} 视为未填，不算超长。
     *
     * <p>只判上限不判格式：邮箱与电话的格式校验属于入口的声明式约束，放在领域模型里会让
     * 「能不能存」与「格式对不对」两件事共用一个抛点，将来放宽格式就得动领域模型。
     *
     * @param value     字段值，可为 {@code null}
     * @param label     字段的中文名
     * @param maxLength 列宽上限
     * @throws IllegalArgumentException 值超出列宽
     */
    private static void requireMaxLength(String value, String label, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(
                    label + "长度不能超过 " + maxLength + " 个字符，实际 " + value.length());
        }
    }
}

package com.yunwms.auth.application.audit;

import java.util.Set;

/**
 * 审计快照的键集白名单，测试侧唯一持有的一份。
 *
 * <p><b>为什么要单独写一份而不是引用生产代码里的常量：</b>生产侧若把键列表暴露成常量，
 * 测试再引用它就成了自证——往快照里顺手加一个 {@code passwordHash} 时，常量与断言一起变，
 * 测试永远绿。这里的每个键都是逐字写下来的，生产侧多一个键就会在下一条断言上立刻红。
 *
 * <p><b>为什么集中在这一个类：</b>十一个留痕点的单测都要断言「键集全等」，各写一份九元素的
 * 字面量会让「哪些键算在白名单里」变成十一处互相抄写的约定；抄漏一处时，那一处断言的力气
 * 就白费了。
 *
 * <p>键集全等用 AssertJ 的 {@code containsOnlyKeys} 表达：多一个键失败，少一个键同样失败。
 */
public final class AuditSnapshotKeys {

    /**
     * 用户快照的白名单键。
     *
     * <p>不含 {@code roleIds}：角色是一条条关联行，不是账号自身的字段。
     * 不含任何密码相关的值：哈希、明文、长度与首字符都不进审计表。
     */
    public static final Set<String> USER = Set.of(
            "loginName",
            "displayName",
            "subjectType",
            "ownerId",
            "email",
            "wecomUserId",
            "status",
            "dataScopeType",
            "mustChangePassword");

    /**
     * 分配角色快照的键集：只有角色编码集合。
     *
     * <p>单独一份而不是复用 {@link #USER}：这个留痕点要回答的是「改成了哪些角色」，
     * 而用户快照里没有角色；两者混用会让 {@code before} 与 {@code after} 变成同一个值。
     */
    public static final Set<String> USER_ROLE_ASSIGNMENT = Set.of("roleCodes");

    /** 角色快照的白名单键；{@code menuIds} 是集合，写入时已排序。 */
    public static final Set<String> ROLE = Set.of(
            "code",
            "name",
            "builtin",
            "status",
            "dataScopeType",
            "remark",
            "menuIds");

    private AuditSnapshotKeys() {
    }
}

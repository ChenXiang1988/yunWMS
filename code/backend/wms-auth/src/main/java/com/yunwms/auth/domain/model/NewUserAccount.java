package com.yunwms.auth.domain.model;

/**
 * 待落库的新账号：建号用例与仓储端口之间的写入模型。
 *
 * <p>为什么需要它：建号时数据库还没生成用户标识，{@link UserAccount} 无法表达「尚未落库」的状态
 * （它的 {@code id} 是必填且被登录链路当作既成事实使用）。用一个 id 为空或 0 的 {@code UserAccount}
 * 冒充「待插入」会让「已存在的账号」与「即将创建的账号」变成同一种类型，读代码的人无法从类型上分辨。
 *
 * <p>为什么这里不重复校验主体与货主的搭配：该规则的判定只有一处，即 {@link UserAccount} 的紧凑构造器
 * （脏数据防线）与 {@code sys_user.ck_user_subject}（库约束）。建号用例在构造本对象之前先校验，
 * 是为了给出「货主用户必须指定所属货主」这类用户能照着改的中文提示；若这里再判一次，
 * 规则就有了第三处实现，改口径时必然漏掉一处，而漏掉的那处只在脏数据出现时才暴露。
 *
 * <p>密码在这里只是哈希：明文只应停留在用例的内存里，任何跨层传递的类型都不携带它。
 *
 * @param loginName    登录名，员工由管理员指定，全局唯一且不区分大小写
 * @param displayName  展示名
 * @param subjectType  主体类型
 * @param ownerId      所属货主；内部员工为 {@code null}，货主账号必填
 * @param email        邮箱；内部员工必填（用于自助找回），货主账号可为 {@code null}
 * @param wecomUserId  企业微信 userid；内部员工用于应用消息推送，可为 {@code null}
 * @param passwordHash 由 {@code PasswordHasher#hash} 生成的哈希，不接受明文
 */
public record NewUserAccount(
        String loginName,
        String displayName,
        SubjectType subjectType,
        Long ownerId,
        String email,
        String wecomUserId,
        String passwordHash) {

    /**
     * 建号固定的初始状态：停用是后续操作，不是创建时的选择。
     *
     * <p>声明在聚合上而不是写死在写入口：审计快照要如实记下「当时落库的是什么」，
     * 若它自己再写一份 {@code ACTIVE}，两处就会各自漂移，而漂移只会在读日志时表现为一句假话。
     */
    public static final UserStatus INITIAL_STATUS = UserStatus.ACTIVE;

    /**
     * 建号固定为「需先改密」：初始密码只够换一次登录。
     *
     * <p>与 {@link #INITIAL_STATUS} 同一理由声明在这里：写入口与审计快照读同一个常量。
     */
    public static final boolean INITIAL_MUST_CHANGE_PASSWORD = true;
}

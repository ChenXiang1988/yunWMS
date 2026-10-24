package com.yunwms.auth.domain.model;

/**
 * 账号状态。
 *
 * <p>只有「启用 / 停用」两种。锁定是派生状态，由 `locked_until` 表达，不占 {@code status}——
 * 把锁定塞进状态列会让「停用」与「锁定」互相覆盖，解锁时不知道原来是不是停用。
 *
 * <p>数据库存 {@link #name()}，列上有 CHECK 约束兜底。
 */
public enum UserStatus {

    /** 可登录。 */
    ACTIVE,

    /** 已停用，拒绝登录。 */
    DISABLED;

    /**
     * 按数据库中的编码还原。
     *
     * @param code 状态编码，取值为枚举名
     * @return 对应的状态
     * @throws IllegalArgumentException 编码未知，说明数据已损坏，不静默按启用处理
     */
    public static UserStatus fromCode(String code) {
        for (UserStatus status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的账号状态编码：" + code);
    }
}

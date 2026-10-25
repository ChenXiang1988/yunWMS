package com.yunwms.auth.domain.port;

/**
 * 密码校验与生成能力。
 *
 * <p>领域层只关心「明文与哈希是否匹配」和「把明文变成哈希」，不关心用的是 BCrypt 还是别的算法，也不接触 Spring。
 *
 * <p>改密用例需要生成哈希，所以这里同时有 {@link #matches} 与 {@link #hash}：两者必须成对出现，
 * 只提供其一会让调用方自己找算法实现，哈希口径就分叉了。
 */
public interface PasswordHasher {

    /**
     * 校验明文密码与库中哈希是否匹配。
     *
     * @param rawPassword  用户提交的明文密码
     * @param passwordHash 库中保存的密码哈希
     * @return 匹配返回 {@code true}；哈希格式无法识别时按不匹配处理，由实现方记录告警
     */
    boolean matches(String rawPassword, String passwordHash);

    /**
     * 生成密码哈希。
     *
     * <p>同一明文每次生成的哈希都不同（加盐）：调用方不能靠比较哈希串判断「新旧密码是否相同」，
     * 必须用 {@link #matches} 拿新明文去比旧哈希。
     *
     * @param rawPassword 用户提交的明文密码，调用方需保证非空且已通过密码策略校验
     * @return 可直接落库的哈希串
     */
    String hash(String rawPassword);
}

package com.yunwms.auth.domain.port;

/**
 * 密码校验能力。
 *
 * <p>领域层只关心「明文与哈希是否匹配」，不关心用的是 BCrypt 还是别的算法，也不接触 Spring。
 *
 * <p>本接口目前只有校验：密码的生成（建号、重置密码、改密）属于后续用例，各自实现时再加，
 * 不在这里预埋一个暂时无人调用的方法。
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
}

package com.yunwms.auth.infrastructure.security;

import com.yunwms.auth.domain.port.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 用 BCrypt 校验密码。
 *
 * <p>不自研哈希：BCrypt 自带随机盐与可调代价，是密码存储的既定做法；这里只依赖
 * {@code spring-security-crypto} 这一个工具 jar，不引入 Spring Security 的过滤器链与自动配置。
 *
 * <p>代价因子保持默认的 10：登录是低频操作，10 在普通机器上约几十毫秒，适合私有部署的中小规模；
 * 调高会同时拖慢登录与压测，调低则失去抗暴力破解的意义。
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        // 哈希格式无法识别时由 BCrypt 实现方按不匹配处理并告警：损坏的数据不该变成「这个账号有异常」的探针
        return encoder.matches(rawPassword, passwordHash);
    }
}

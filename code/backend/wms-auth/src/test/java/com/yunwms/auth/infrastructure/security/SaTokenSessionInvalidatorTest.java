package com.yunwms.auth.infrastructure.security;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.mock.SaRequestForMock;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.yunwms.auth.domain.port.SessionInvalidator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会话失效实现的行为单测。
 *
 * <p>不起 Spring 上下文，用 Sa-Token 自带的模拟上下文当「一次请求」：每次进新上下文并把令牌写进请求头，
 * 这样被测实现取当前令牌时走的是与生产一致的读取链路（含 {@code Bearer} 前缀裁剪）。
 *
 * <p>钉住两件事，它们都无法靠调用计数断言出来：只失效其他令牌时，当前令牌必须仍然通过登录校验，
 * 且账号会话上的登录属性不能被一起抹掉（主体类型、货主归属、改密标记都挂在那里）；
 * 使全部会话失效时，所有已签发的令牌都必须立刻失效。
 */
@DisplayName("会话失效")
class SaTokenSessionInvalidatorTest {

    /** 与 {@code application.yml} 对齐：前端统一发 {@code Authorization: Bearer <token>}。 */
    private static final String TOKEN_NAME = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer";

    private static final long FIRST_CASE_USER = 9101L;
    private static final long SECOND_CASE_USER = 9102L;
    private static final String LOGIN_AT = "2026-01-01T00:00:00Z";

    private final SessionInvalidator sessionInvalidator = new SaTokenSessionInvalidator();

    @BeforeAll
    static void configureSaToken() {
        // 按生产配置构造：令牌名与前缀决定「当前令牌」怎么从请求里读出来，读错则保留当前会话无从谈起；
        // 关掉 cookie 读取与生产一致，令牌只走请求头
        SaManager.setConfig(new SaTokenConfig()
                .setTokenName(TOKEN_NAME)
                .setTokenPrefix(TOKEN_PREFIX)
                .setIsReadCookie(false));
    }

    @Test
    @DisplayName("保留当前会话：其他设备的令牌立刻失效，当前会话的身份信息仍在")
    void shouldInvalidateOnlyOtherSessions() {
        String currentDevice = login(FIRST_CASE_USER);
        String otherDevice = login(FIRST_CASE_USER);
        // 同一账号两次登录必须是两张不同的令牌，否则「踢掉其他会话」这件事根本无从验证
        assertThat(currentDevice).isNotEqualTo(otherDevice);

        inRequest(currentDevice, () -> {
            sessionInvalidator.invalidateOthersOfCurrent(FIRST_CASE_USER);
            return null;
        });

        // 发起请求的这台设备不受影响，且账号会话上的登录属性仍在：
        // 若实现改成 logout(loginId)，这里会连同主体类型、货主归属一起被删掉
        assertThat(isLoggedIn(currentDevice)).isTrue();
        assertThat(inRequest(currentDevice, () -> StpUtil.getSession().get(SessionAttributes.LOGIN_AT)))
                .isEqualTo(LOGIN_AT);
        // 另一台设备的下一次请求必须被拒，且拒绝方式就是未登录，由鉴权链路翻译成 401
        assertThat(isLoggedIn(otherDevice)).isFalse();
        assertThatThrownBy(() -> inRequest(otherDevice, () -> {
            StpUtil.checkLogin();
            return null;
        })).isInstanceOf(NotLoginException.class);
    }

    @Test
    @DisplayName("使全部会话失效：已签发的每张令牌都不能再通过登录校验")
    void shouldInvalidateAllSessions() {
        String currentDevice = login(SECOND_CASE_USER);
        String otherDevice = login(SECOND_CASE_USER);

        inRequest(currentDevice, () -> {
            sessionInvalidator.invalidateAllOf(SECOND_CASE_USER);
            return null;
        });

        // 连发起调用的这个会话也要断：它存在的意义是「口令已经失控，谁都别再进来」
        assertThat(isLoggedIn(currentDevice)).isFalse();
        assertThat(isLoggedIn(otherDevice)).isFalse();
    }

    /** 用一张令牌发一次请求，返回这次请求是否仍被当成已登录。 */
    private static boolean isLoggedIn(String tokenValue) {
        return inRequest(tokenValue, StpUtil::isLogin);
    }

    /** 模拟一次登录请求，返回签发出的令牌。 */
    private static String login(long userId) {
        return inRequest(null, () -> {
            StpUtil.login(userId);
            // 写一条登录属性：它落在账号会话上，后续用来判断账号会话有没有被误删
            StpUtil.getSession().set(SessionAttributes.LOGIN_AT, LOGIN_AT);
            return StpUtil.getTokenValue();
        });
    }

    /**
     * 在一次新请求里执行动作。
     *
     * <p>每次换新的模拟上下文：Sa-Token 会把刚签发的令牌暂存在请求上下文里，
     * 复用上下文会让「这次请求带的是哪张令牌」变得说不清。
     *
     * @param tokenValue 本次请求携带的令牌；{@code null} 表示不带令牌（登录请求）
     * @param action     请求内执行的动作
     * @return 动作的返回值
     */
    private static <T> T inRequest(String tokenValue, Supplier<T> action) {
        return SaTokenContextMockUtil.setMockContext(() -> {
            if (tokenValue != null) {
                SaRequestForMock request = (SaRequestForMock) SaHolder.getRequest();
                request.headerMap.put(TOKEN_NAME, TOKEN_PREFIX + " " + tokenValue);
            }
            return action.get();
        });
    }
}

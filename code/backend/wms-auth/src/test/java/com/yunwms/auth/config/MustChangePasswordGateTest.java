package com.yunwms.auth.config;

import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 待改密状态访问判定的单测。
 *
 * <p>规格只有一句话：「仅允许访问改密界面，其他业务接口返回未授权」。这句话在实现上对应
 * 一个默认拒绝的清单，因此每一种放行写法都要有对应用例——漏一种，用户在改密页就会被自己的拦截器锁死。
 */
@DisplayName("待改密状态的访问判定")
class MustChangePasswordGateTest {

    private static final String CHANGE_PASSWORD_PATH = "/api/auth/password";
    private static final String BUSINESS_PATH = "/api/owners";

    @Test
    @DisplayName("不需要改密：任何路径都放行")
    void shouldAllowEveryPathWhenPasswordChangeIsNotRequired() {
        assertThatCode(() -> MustChangePasswordGate.assertAllowed(BUSINESS_PATH, false))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("待改密：改密接口本身放行")
    void shouldAllowChangePasswordPath() {
        assertThatCode(() -> MustChangePasswordGate.assertAllowed(CHANGE_PASSWORD_PATH, true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("待改密：改密接口带尾斜杠同样放行")
    void shouldAllowChangePasswordPathWithTrailingSlash() {
        assertThatCode(() -> MustChangePasswordGate.assertAllowed(CHANGE_PASSWORD_PATH + "/", true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("待改密：改密接口带查询串同样放行")
    void shouldAllowChangePasswordPathWithQueryString() {
        assertThatCode(() -> MustChangePasswordGate.assertAllowed(CHANGE_PASSWORD_PATH + "?from=login", true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("待改密：业务接口被拒，错误码为 40104")
    void shouldRejectBusinessPath() {
        assertThatThrownBy(() -> MustChangePasswordGate.assertAllowed(BUSINESS_PATH, true))
                .isInstanceOfSatisfying(BizException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(ErrorCode.MUST_CHANGE_PASSWORD));
    }

    @Test
    @DisplayName("待改密：改密接口的变形路径不被误放行")
    void shouldRejectPathThatOnlyLooksLikeChangePasswordPath() {
        assertThatThrownBy(() -> MustChangePasswordGate.assertAllowed(CHANGE_PASSWORD_PATH + "/../owners", true))
                .isInstanceOfSatisfying(BizException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(ErrorCode.MUST_CHANGE_PASSWORD));
    }

    @Test
    @DisplayName("待改密：取不到路径时按拒绝处理")
    void shouldRejectWhenPathIsMissing() {
        assertThatThrownBy(() -> MustChangePasswordGate.assertAllowed(null, true))
                .isInstanceOfSatisfying(BizException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(ErrorCode.MUST_CHANGE_PASSWORD));
    }
}

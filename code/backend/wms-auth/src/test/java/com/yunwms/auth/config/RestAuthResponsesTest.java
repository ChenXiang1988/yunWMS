package com.yunwms.auth.config;

import com.yunwms.web.api.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 鉴权失败响应里「错误码 → HTTP 状态码」的映射单测。
 *
 * <p>不测序列化：正文由 Jackson 生成，写错会立刻在接口上暴露；而状态码写错是静默的——
 * 待改密一旦返回 200，前端拦截器会把请求当成功，用户会在一个只会报错的界面里打转。
 */
@DisplayName("鉴权失败的状态码映射")
class RestAuthResponsesTest {

    @Test
    @DisplayName("未登录与需先改密都返回 401，由错误码区分去向")
    void shouldMapBothUnauthenticatedCasesTo401() {
        assertThat(RestAuthResponses.httpStatus(ErrorCode.UNAUTHORIZED))
                .isEqualTo(401);
        assertThat(RestAuthResponses.httpStatus(ErrorCode.MUST_CHANGE_PASSWORD))
                .isEqualTo(401);
    }

    @Test
    @DisplayName("无权限返回 403，其余按 500")
    void shouldMapForbiddenAndUnexpected() {
        assertThat(RestAuthResponses.httpStatus(ErrorCode.FORBIDDEN))
                .isEqualTo(403);
        assertThat(RestAuthResponses.httpStatus(ErrorCode.INTERNAL))
                .isEqualTo(500);
    }
}

package com.yunwms.auth.interfaces.controller.request;

import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 请求标识解析器的单测：三种「空」的含义与非法值的错误码。
 *
 * <p>为什么要单独钉这几种情形：HTTP 层只能证明「某一种非法输入返回了 40000」，
 * 证明不了 null 与空列表被区分对待——而这两者一旦被合并，分配角色接口就会在
 * 「前端漏传字段」时静默清空该用户的全部角色；这条错误不会有任何报错，只会让某个人
 * 某天发现自己什么权限都没了。空列表合法、字段省略被拒的语义因此在解析层就必须分开。
 */
@DisplayName("请求标识解析")
class RequestIdsTest {

    @Test
    @DisplayName("单个标识：null 原样返回 null，合法十进制解析为 Long")
    void shouldParseOptionalSingleId() {
        assertThat(RequestIds.optionalId(null, "ownerId")).isNull();
        assertThat(RequestIds.optionalId("42", "ownerId")).isEqualTo(42L);
        // 粘贴来的标识常带首尾空白：那是传输噪音，不是业务值
        assertThat(RequestIds.optionalId(" 42 ", "ownerId")).isEqualTo(42L);
    }

    @Test
    @DisplayName("标识列表：null 返回 null，空列表返回空列表，逐项解析保持顺序")
    void shouldKeepNullAndEmptyListDistinct() {
        assertThat(RequestIds.optionalIds(null, "roleIds")).isNull();
        assertThat(RequestIds.optionalIds(List.of(), "roleIds")).isEmpty();
        assertThat(RequestIds.optionalIds(List.of("2", "3"), "roleIds")).containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("空白与空串：返回 40000，提示里带字段名")
    void shouldRejectBlankId() {
        assertBadRequest(() -> RequestIds.optionalId("  ", "ownerId"), "ownerId");
    }

    @Test
    @DisplayName("非十进制整数：返回 40000，提示里带字段名与实际值")
    void shouldRejectNonNumericId() {
        assertBadRequest(() -> RequestIds.optionalId("abc", "ownerId"), "ownerId", "abc");
    }

    @Test
    @DisplayName("列表里任一项非法：整批拒绝，不返回部分结果")
    void shouldRejectWholeListWhenAnyElementInvalid() {
        assertBadRequest(() -> RequestIds.optionalIds(List.of("2", "3x"), "menuIds"), "menuIds");
    }

    @Test
    @DisplayName("超出 Long 范围的字符串按非法处理，不抛 NumberFormatException 之外的异常")
    void shouldRejectOutOfRangeId() {
        String tooLarge = "99999999999999999999";
        assertBadRequest(() -> RequestIds.optionalId(tooLarge, "ownerId"), "ownerId");
    }

    /** 断言抛出的是带指定错误码的 {@link BizException}，且提示里含全部关键词。 */
    private static void assertBadRequest(Runnable action, String... messageParts) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class, ex -> {
                    assertThat(ex.errorCode())
                            .as("非法标识是调用方的输入问题，不能落到 50000")
                            .isEqualTo(ErrorCode.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains(messageParts);
                });
    }

    @Test
    @DisplayName("解析结果与入参不共享引用，调用方后续修改入参不会影响已解析结果")
    void shouldNotShareReferenceWithInput() {
        List<String> raw = new ArrayList<>(List.of("2"));
        List<Long> parsed = RequestIds.optionalIds(raw, "roleIds");

        raw.add("3");

        assertThat(parsed).containsExactly(2L);
    }
}

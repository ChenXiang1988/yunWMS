package com.yunwms.auth.application.service;

import com.yunwms.auth.domain.model.DataScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 请求级缓存的单测：同一请求只解析一次，清理之后重新解析。
 *
 * <p>清理这条必须有用例盯着：线程由容器复用，漏掉清理时下一个请求会读到上一个用户的范围，
 * 而那种缺陷只在并发或连接复用的时序下出现，本地顺序点几下完全复现不出来。
 */
@DisplayName("数据范围请求级缓存")
class DataScopeContextTest {

    private DataScopeResolver resolver;
    private DataScopeContext context;

    @BeforeEach
    void setUp() {
        resolver = mock(DataScopeResolver.class);
        context = new DataScopeContext(resolver);
    }

    @AfterEach
    void tearDown() {
        // 测试自身也不能把值留在当前线程上：它会被下一个用例所在的线程复用
        context.clear();
    }

    @Test
    @DisplayName("同一请求内重复读取只解析一次")
    void shouldResolveOncePerRequest() {
        given(resolver.resolve()).willReturn(DataScope.restricted(Set.of(1L)));

        assertThat(context.current()).isEqualTo(DataScope.restricted(Set.of(1L)));
        assertThat(context.current()).isEqualTo(DataScope.restricted(Set.of(1L)));
        verify(resolver, times(1)).resolve();
    }

    @Test
    @DisplayName("清理之后重新解析：角色停用与覆盖清除在下一个请求生效")
    void shouldResolveAgainAfterClear() {
        given(resolver.resolve()).willReturn(DataScope.all());

        assertThat(context.current()).isEqualTo(DataScope.all());
        context.clear();
        given(resolver.resolve()).willReturn(DataScope.none());

        assertThat(context.current()).isEqualTo(DataScope.none());
        verify(resolver, times(2)).resolve();
    }
}

package com.yunwms.master.application.query;

import com.yunwms.master.domain.port.OwnerNameReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 货主名称批量查询用例的单测：读取端口替换为 mock，不起 Spring、不连库。
 *
 * <p>要钉住两件事：
 * <ul>
 *   <li><b>空集合与 {@code null} 一次库都不查</b>——空的 {@code IN} 要么拼出非法 SQL，要么在拼装时
 *       被省略、退化成「查全部货主」。这一步只能在这一层短路，推给持久化实现就等于每加一个实现
 *       都要重新记得一次。用 {@code verifyNoInteractions} 而不是断言返回空映射，是因为「返回空
 *       但先查了一次全表」与「直接返回空」在返回值上完全相同；</li>
 *   <li><b>非空集合只发一次批量查询</b>——逐个标识查询会让 SQL 条数随页内行数增长，
 *       而那正是这次跨模块取数要避免的形态。</li>
 * </ul>
 *
 * <p>「已软删除的货主名称仍能解析」不在这里验证：口径写在 SQL 里，只有连库的集成测试
 * （{@code UserManagementIntegrationTest}）能证。
 */
@DisplayName("货主名称批量查询")
class OwnerNameLookupServiceTest {

    private OwnerNameReader ownerNameReader;
    private OwnerNameLookupService service;

    @BeforeEach
    void setUp() {
        ownerNameReader = mock(OwnerNameReader.class);
        service = new OwnerNameLookupService(ownerNameReader);
    }

    @Test
    @DisplayName("空集合与 null：返回空映射，且不发任何查询")
    void shouldShortCircuitOnEmptyIds() {
        assertThat(service.findNamesByIds(List.of())).isEmpty();
        assertThat(service.findNamesByIds(null)).isEmpty();

        verifyNoInteractions(ownerNameReader);
    }

    @Test
    @DisplayName("非空集合：一次批量查询，原样返回标识到名称的映射")
    void shouldQueryInOneBatch() {
        given(ownerNameReader.findNamesByIds(any())).willReturn(Map.of(7L, "已删除的货主"));

        Map<Long, String> names = service.findNamesByIds(List.of(7L, 8L));

        assertThat(names).containsExactly(Map.entry(7L, "已删除的货主"));
        // 精确校验一次调用，且入参是整批：逐条查会在这里报成「想调用 1 次，实际 2 次」
        verify(ownerNameReader).findNamesByIds(List.of(7L, 8L));
    }
}

package com.yunwms.auth.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 生效范围合并规则的单测：规格里「并集、ALL 吞并、空集无权」三条规则的落点。
 *
 * <p>这些规则是纯函数，因此可以脱离数据库逐条验证；调用方（解析用例）负责把配置展开成
 * {@link DataScope}，本类只回答「给定的若干范围合并之后是什么」。
 */
@DisplayName("数据范围合并")
class DataScopeTest {

    @Test
    @DisplayName("并集：多个限定范围合并成一份去重后的集合")
    void shouldUnionRestrictedScopes() {
        DataScope merged = DataScope.union(List.of(
                DataScope.restricted(Set.of(1L, 2L)),
                DataScope.restricted(Set.of(3L)),
                DataScope.restricted(Set.of(2L))));

        assertThat(merged).isEqualTo(DataScope.restricted(Set.of(1L, 2L, 3L)));
    }

    @Test
    @DisplayName("ALL 吞并其他范围，且与顺序无关")
    void shouldLetAllSwallowOthers() {
        assertThat(DataScope.union(List.of(
                DataScope.restricted(Set.of(1L)), DataScope.all())))
                .isEqualTo(DataScope.all());
        assertThat(DataScope.union(List.of(
                DataScope.all(), DataScope.restricted(Set.of(1L)))))
                .isEqualTo(DataScope.all());
        assertThat(DataScope.union(List.of(DataScope.all(), DataScope.none())))
                .isEqualTo(DataScope.all());
    }

    @Test
    @DisplayName("空明细的角色不贡献货主：并集仍等于另一个角色的范围")
    void shouldIgnoreEmptyRestrictedScope() {
        DataScope merged = DataScope.union(List.of(
                DataScope.restricted(Set.of()),
                DataScope.restricted(Set.of(3L))));

        assertThat(merged).isEqualTo(DataScope.restricted(Set.of(3L)));
    }

    @Test
    @DisplayName("并集为空即无权，不回退到全部货主")
    void shouldBeNoneWhenUnionIsEmpty() {
        assertThat(DataScope.union(List.of())).isEqualTo(DataScope.none());
        assertThat(DataScope.union(null)).isEqualTo(DataScope.none());
        assertThat(DataScope.union(List.of(DataScope.none()))).isEqualTo(DataScope.none());
        assertThat(DataScope.union(List.of(DataScope.restricted(Set.of())))).isEqualTo(DataScope.none());
    }

    @Test
    @DisplayName("限定范围复制成不可变集合：下游改不动请求级缓存的值")
    void shouldCopyOwnerIdsDefensively() {
        DataScope scope = DataScope.restricted(Set.of(1L, 2L));

        assertThat(scope).isInstanceOf(DataScope.Restricted.class);
        assertThat(((DataScope.Restricted) scope).ownerIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThatThrownBy(() -> ((DataScope.Restricted) scope).ownerIds().add(9L))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("类型编码按枚举名还原，未知编码抛异常而不是回落默认值")
    void shouldRejectUnknownScopeTypeCode() {
        assertThat(DataScopeType.fromCode("ALL")).isEqualTo(DataScopeType.ALL);
        assertThat(DataScopeType.fromCode("ASSIGNED")).isEqualTo(DataScopeType.ASSIGNED);
        assertThat(DataScopeType.fromCode("CUSTOM")).isEqualTo(DataScopeType.CUSTOM);
        assertThatThrownBy(() -> DataScopeType.fromCode("FOO"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DataScopeType.fromCode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("配置模型：非 CUSTOM 带明细属于损坏数据，当场失败")
    void shouldRejectOwnerIdsOnNonCustomSetting() {
        assertThatThrownBy(() -> new DataScopeSetting(DataScopeType.ALL, Set.of(1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataScopeSetting(null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        // CUSTOM 空明细是合法状态：结果为空集，即无权
        assertThat(new DataScopeSetting(DataScopeType.CUSTOM, Set.of()).ownerIds()).isEmpty();
    }
}

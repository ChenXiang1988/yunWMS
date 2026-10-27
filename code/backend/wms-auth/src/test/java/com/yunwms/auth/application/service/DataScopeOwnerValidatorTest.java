package com.yunwms.auth.application.service;

import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.port.OwnerExistenceQuery;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 数据范围配置校验的单测：类型与明细是否搭配、明细里的货主是否可用。
 *
 * <p>「货主是否可用」被替换成 mock：真实口径由 {@code wms-master} 定义（存在、未删除且启用），
 * 在集成测试里验证；这里钉住的是本类的判定顺序与错误码。
 */
@DisplayName("数据范围配置校验")
class DataScopeOwnerValidatorTest {

    private OwnerExistenceQuery ownerExistenceQuery;
    private DataScopeOwnerValidator validator;

    @BeforeEach
    void setUp() {
        ownerExistenceQuery = mock(OwnerExistenceQuery.class);
        validator = new DataScopeOwnerValidator(ownerExistenceQuery);
    }

    @Test
    @DisplayName("类型为空：拒绝")
    void shouldRejectMissingType() {
        BizException failure = failureOf(null, List.of());

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("数据范围类型不能为空");
        verifyNoInteractions(ownerExistenceQuery);
    }

    @Test
    @DisplayName("ALL 与 ASSIGNED 不接受指定货主明细")
    void shouldRejectOwnerIdsOnRuleScopes() {
        for (DataScopeType type : List.of(DataScopeType.ALL, DataScopeType.ASSIGNED)) {
            BizException failure = failureOf(type, List.of(1L));

            assertThat(failure.errorCode()).as("%s 带明细应被拒绝", type).isEqualTo(ErrorCode.BAD_REQUEST);
            assertThat(failure.getMessage()).contains(type.name());
        }
        // 纯入参判定，不该为此查库
        verifyNoInteractions(ownerExistenceQuery);
    }

    @Test
    @DisplayName("ALL 与 ASSIGNED 不带明细：通过")
    void shouldAcceptRuleScopesWithoutOwnerIds() {
        assertThatCode(() -> validator.requireConfigurable(DataScopeType.ALL, List.of())).doesNotThrowAnyException();
        assertThatCode(() -> validator.requireConfigurable(DataScopeType.ASSIGNED, null))
                .doesNotThrowAnyException();
        verifyNoInteractions(ownerExistenceQuery);
    }

    @Test
    @DisplayName("CUSTOM 空明细合法，且不查库")
    void shouldAcceptEmptyCustom() {
        assertThatCode(() -> validator.requireConfigurable(DataScopeType.CUSTOM, List.of()))
                .doesNotThrowAnyException();
        verifyNoInteractions(ownerExistenceQuery);
    }

    @Test
    @DisplayName("CUSTOM 明细里的货主全部可用：通过")
    void shouldAcceptAvailableCustomOwners() {
        given(ownerExistenceQuery.findExistingActiveIds(Set.of(1L, 2L))).willReturn(Set.of(1L, 2L));

        assertThatCode(() -> validator.requireConfigurable(DataScopeType.CUSTOM, List.of(1L, 2L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CUSTOM 明细里有不存在或未启用的货主：拒绝并点出具体标识")
    void shouldRejectUnavailableCustomOwners() {
        given(ownerExistenceQuery.findExistingActiveIds(Set.of(1L, 99L))).willReturn(Set.of(1L));

        BizException failure = failureOf(DataScopeType.CUSTOM, List.of(1L, 99L));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        // 只提示「有货主不可用」时管理员得自己在几十个勾选里找，因此必须带上标识
        assertThat(failure.getMessage()).isEqualTo("指定的货主不存在或未启用：[99]");
    }

    private BizException failureOf(DataScopeType type, List<Long> ownerIds) {
        try {
            validator.requireConfigurable(type, ownerIds);
        } catch (BizException e) {
            return e;
        }
        throw new AssertionError("期望配置被拒绝，但它通过了");
    }
}

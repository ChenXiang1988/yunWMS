package com.yunwms.auth.interfaces.controller.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 建角色请求体的契约钉：接口不能创建内置角色，也不能顺带改状态。
 *
 * <p>为什么值得单独钉一条：{@code builtin} 与 {@code status} 一旦出现在请求体里，
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} 就不再拦它们，而「接口能创建内置角色」等于绕开权限体系
 * 造一个超级管理员——这个漏洞不会有任何报错，只会在某天发现多了一个权限被动态展开成全部的角色。
 * 用「字段清单全等」而不是「不含 builtin」，是为了让新增字段也必须过这一关。
 *
 * <p>数据范围（{@code dataScopeType} / {@code ownerIds}）是<b>可选项</b>：省略时按
 * {@code CUSTOM} 空明细建角色，由管理端点随后配置。它们出现在清单里，是因为设计明确允许建角色时
 * 一次给全范围；而 {@code builtin} 与 {@code status} 仍然不在清单里，两者是任何时候都不该由接口决定的。
 */
@DisplayName("建角色请求体")
class CreateRoleRequestTest {

    @Test
    @DisplayName("只接收 code / name / remark / menuIds 与可选的数据范围，不接受 builtin、status")
    void shouldOnlyExposeCreationInputs() {
        List<String> components = Arrays.stream(CreateRoleRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(components)
                .as("建角色请求体多出的字段都会被当成入参，builtin 与 status 尤其不能出现")
                .containsExactly("code", "name", "remark", "menuIds", "dataScopeType", "ownerIds");
        assertThat(components).as("接口不能创建内置角色").doesNotContain("builtin");
        assertThat(components).as("新建角色一律启用，状态不是创建时的选择").doesNotContain("status");
    }
}

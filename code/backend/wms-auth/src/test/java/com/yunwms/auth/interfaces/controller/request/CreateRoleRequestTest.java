package com.yunwms.auth.interfaces.controller.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 建角色请求体的契约钉：接口不能创建内置角色，也不能顺带改状态与数据范围。
 *
 * <p>为什么值得单独钉一条：这三个字段一旦出现在请求体里，{@code FAIL_ON_UNKNOWN_PROPERTIES}
 * 就不再拦它们，而「接口能创建内置角色」等于绕开权限体系造一个超级管理员——
 * 这个漏洞不会有任何报错，只会在某天发现多了一个权限被动态展开成全部的角色。
 * 用「字段清单全等」而不是「不含 builtin」，是为了让新增字段也必须过这一关。
 */
@DisplayName("建角色请求体")
class CreateRoleRequestTest {

    @Test
    @DisplayName("只接收 code / name / remark / menuIds，不接受 builtin、status、dataScopeType")
    void shouldOnlyExposeCreationInputs() {
        List<String> components = Arrays.stream(CreateRoleRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(components)
                .as("建角色请求体多出的字段都会被当成入参，builtin 尤其不能出现")
                .containsExactly("code", "name", "remark", "menuIds");
    }
}

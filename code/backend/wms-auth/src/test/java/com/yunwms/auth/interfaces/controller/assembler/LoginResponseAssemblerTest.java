package com.yunwms.auth.interfaces.controller.assembler;

import com.yunwms.auth.application.dto.AuthenticatedSubject;
import com.yunwms.auth.domain.model.SubjectType;
import com.yunwms.auth.interfaces.controller.response.LoginResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录响应组装的单测：覆盖契约里两处容易被改坏的地方——可访问端由主体类型推导、标识以字符串传输。
 */
@DisplayName("登录响应组装")
class LoginResponseAssemblerTest {

    private final LoginResponseAssembler assembler = new LoginResponseAssembler();

    @Test
    @DisplayName("内部员工：只能进仓网端，不带货主标识")
    void shouldExposeConsoleOnlyForStaff() {
        LoginResponse response = assembler.toResponse(
                new AuthenticatedSubject("token-1", SubjectType.STAFF, null, "张三", false, List.of("CS_STAFF")));

        assertThat(response.subjectType()).isEqualTo("STAFF");
        assertThat(response.ownerId()).isNull();
        assertThat(response.roles()).containsExactly("CS_STAFF");
        assertThat(response.accessiblePortals()).containsExactly("console");
    }

    @Test
    @DisplayName("货主账号：只能进货主端，货主标识以字符串返回")
    void shouldExposePortalOnlyForOwner() {
        LoginResponse response = assembler.toResponse(
                new AuthenticatedSubject("token-2", SubjectType.OWNER, 9007199254740993L, "李四", false, List.of("OWNER_ADMIN")));

        assertThat(response.subjectType()).isEqualTo("OWNER");
        assertThat(response.ownerId()).isEqualTo("9007199254740993");
        assertThat(response.accessiblePortals()).containsExactly("portal");
    }

    @Test
    @DisplayName("需先改密：标记原样带到响应，前端据此直达改密页")
    void shouldCarryMustChangePasswordFlag() {
        LoginResponse pending = assembler.toResponse(
                new AuthenticatedSubject("token-3", SubjectType.STAFF, null, "张三", true, List.of()));
        LoginResponse changed = assembler.toResponse(
                new AuthenticatedSubject("token-4", SubjectType.STAFF, null, "张三", false, List.of()));

        assertThat(pending.mustChangePassword()).isTrue();
        assertThat(changed.mustChangePassword()).isFalse();
    }
}

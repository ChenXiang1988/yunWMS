package com.yunwms.auth.application.service;

import com.yunwms.auth.application.audit.AuditSnapshotKeys;
import com.yunwms.auth.application.command.CreateRoleCommand;
import com.yunwms.auth.application.dto.CreatedRole;
import com.yunwms.auth.domain.model.AuditAction;
import com.yunwms.auth.domain.model.AuditBizType;
import com.yunwms.auth.domain.model.AuditEntry;
import com.yunwms.auth.domain.model.DataScopeType;
import com.yunwms.auth.domain.model.NewRole;
import com.yunwms.auth.domain.port.AuditRecorder;
import com.yunwms.auth.domain.port.CurrentSession;
import com.yunwms.auth.domain.port.RoleRegistrar;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 建角色用例的单测。
 *
 * <p>端口替换为 mock，不起 Spring、不连库。这里能钉住的是「判定顺序」与「哪一步不该发生」：
 * 编码格式不合法时不该落库，名称为空时不该落库，库约束冲突必须翻译成前端能展示的错误码，
 * 而不是 50000。
 *
 * <p>编码的唯一性与菜单的存在性都不在本用例的判定范围内（服务层「先查再插」在并发下无效），
 * 因此这里验证的是「数据库抛出冲突之后如何翻译」。
 */
@DisplayName("建角色")
class RoleCreationServiceTest {

    private static final long OPERATOR_ID = 9L;
    private static final long NEW_ROLE_ID = 2L;
    private static final String CODE = "CS_STAFF";
    private static final String NAME = "客服";

    private RoleRegistrar roleRegistrar;
    private CurrentSession currentSession;
    private DataScopeOwnerValidator dataScopeOwnerValidator;
    private AuditRecorder auditRecorder;
    private RoleCreationService roleCreationService;

    @BeforeEach
    void setUp() {
        roleRegistrar = mock(RoleRegistrar.class);
        currentSession = mock(CurrentSession.class);
        // 校验器本身要查「货主是否可用」，那是集成测试的事；这里只关心用例把什么交给它、
        // 以及未指定范围时补的是哪个默认值
        dataScopeOwnerValidator = mock(DataScopeOwnerValidator.class);
        auditRecorder = mock(AuditRecorder.class);
        roleCreationService = new RoleCreationService(
                roleRegistrar, currentSession, dataScopeOwnerValidator, auditRecorder);
        given(currentSession.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
    }

    @Test
    @DisplayName("建角色成功：落库为裁剪后的编码与名称，响应回标识与编码")
    void shouldRegisterRole() {
        given(roleRegistrar.register(any(NewRole.class), anyLong())).willReturn(NEW_ROLE_ID);

        CreatedRole created = roleCreationService.create(command("  CS_STAFF  ", " 客服 ", " 对接货主 ",
                List.of(3L, 4L)));

        assertThat(created.id()).isEqualTo(NEW_ROLE_ID);
        assertThat(created.code()).isEqualTo(CODE);

        ArgumentCaptor<NewRole> captor = ArgumentCaptor.forClass(NewRole.class);
        verify(roleRegistrar).register(captor.capture(), eq(OPERATOR_ID));
        NewRole role = captor.getValue();
        assertThat(role.code()).isEqualTo(CODE);
        assertThat(role.name()).isEqualTo(NAME);
        // 不裁空白会建出 " CS_STAFF" 这种「看起来一样」的第二个编码，而唯一索引按原值判等
        assertThat(role.remark()).isEqualTo("对接货主");
        assertThat(role.menuIds()).containsExactlyInAnyOrder(3L, 4L);
    }

    @Test
    @DisplayName("建角色成功：menuIds 为 null 与空数组合并成空集合，表示不授权")
    void shouldAllowEmptyMenuIds() {
        given(roleRegistrar.register(any(NewRole.class), anyLong())).willReturn(NEW_ROLE_ID);

        roleCreationService.create(command(CODE, NAME, null, null));

        ArgumentCaptor<NewRole> captor = ArgumentCaptor.forClass(NewRole.class);
        verify(roleRegistrar).register(captor.capture(), anyLong());
        assertThat(captor.getValue().menuIds()).isEmpty();
    }

    @Test
    @DisplayName("建角色成功：同一菜单标识传两次是集合语义，落库前去重")
    void shouldDeduplicateMenuIds() {
        given(roleRegistrar.register(any(NewRole.class), anyLong())).willReturn(NEW_ROLE_ID);

        roleCreationService.create(command(CODE, NAME, "  ", Arrays.asList(3L, 3L, 4L)));

        ArgumentCaptor<NewRole> captor = ArgumentCaptor.forClass(NewRole.class);
        verify(roleRegistrar).register(captor.capture(), anyLong());
        assertThat(captor.getValue().menuIds()).isEqualTo(Set.of(3L, 4L));
        // 全空白的备注按未填处理：库里 remark = '' 与 remark is null 对使用者没有区别
        assertThat(captor.getValue().remark()).isNull();
    }

    @Test
    @DisplayName("编码为空：拒绝，不落库")
    void shouldRejectBlankCode() {
        BizException missing = failureOf(command(null, NAME, null, List.of()));
        assertThat(missing.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(missing.getMessage()).isEqualTo("角色编码不能为空");

        BizException blank = failureOf(command("   ", NAME, null, List.of()));
        assertThat(blank.getMessage()).isEqualTo("角色编码不能为空");

        verifyNoInteractions(roleRegistrar);
    }

    @Test
    @DisplayName("编码格式非法：拒绝并说明规则，不落库")
    void shouldRejectMalformedCode() {
        // 小写、数字开头、单字符、含非法字符、超长，五类各一例
        for (String malformed : List.of("cs_staff", "1STAFF", "C", "CS-STAFF", "C".repeat(33))) {
            BizException failure = failureOf(command(malformed, NAME, null, List.of()));

            assertThat(failure.errorCode()).as("编码 %s 应被拒绝", malformed).isEqualTo(ErrorCode.BAD_REQUEST);
            assertThat(failure.getMessage()).isEqualTo(NewRole.CODE_FORMAT_HINT);
        }
        verifyNoInteractions(roleRegistrar);
    }

    @Test
    @DisplayName("名称为空：拒绝，不落库")
    void shouldRejectBlankName() {
        BizException failure = failureOf(command(CODE, "  ", null, List.of()));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("角色名称不能为空");
        verifyNoInteractions(roleRegistrar);
    }

    @Test
    @DisplayName("编码已被占用：转成 40902，供前端在编码字段就地提示")
    void shouldTranslateDuplicateCode() {
        given(roleRegistrar.register(any(NewRole.class), anyLong()))
                .willThrow(constraintViolation("ux_sys_role_code"));

        BizException failure = failureOf(command(CODE, NAME, null, List.of()));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.ROLE_CODE_TAKEN);
        assertThat(failure.getMessage()).isEqualTo("角色编码已被占用（含已删除的角色）");
        // 库里一行都没留下，这次建档没有发生，也就不该留痕
        verifyNoInteractions(auditRecorder);
    }

    @Test
    @DisplayName("勾选的菜单不存在：外键冲突转成参数错误，而不是 409")
    void shouldTranslateMissingMenu() {
        given(roleRegistrar.register(any(NewRole.class), anyLong()))
                .willThrow(constraintViolation("sys_role_menu_menu_id_fkey"));

        BizException failure = failureOf(command(CODE, NAME, null, List.of(999L)));

        // 40000 而不是 409：入参引用了一个不存在的 id，改一个 id 就能过，
        // 而 409 段位留给「必须先去处理已有数据」的冲突
        assertThat(failure.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(failure.getMessage()).isEqualTo("指定的菜单或权限点不存在");
    }

    @Test
    @DisplayName("不认识的约束：原样抛出，不猜错误码")
    void shouldNotGuessUnknownConstraint() {
        DataIntegrityViolationException unexpected = constraintViolation("ck_role_scope");
        given(roleRegistrar.register(any(NewRole.class), anyLong())).willThrow(unexpected);

        // 猜一个错误码返回给用户，等于把「我们不知道自己违反了哪条规则」伪装成一次正常拒绝
        assertThatThrownBy(() -> roleCreationService.create(command(CODE, NAME, null, List.of())))
                .isSameAs(unexpected);
    }

    @Test
    @DisplayName("会话无效：按未登录拒绝，连校验都不做")
    void shouldRejectWhenSessionHasNoUser() {
        given(currentSession.currentUserId()).willReturn(OptionalLong.empty());

        BizException failure = failureOf(command(CODE, NAME, null, List.of()));

        assertThat(failure.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(roleRegistrar, auditRecorder);
    }

    @Test
    @DisplayName("未指定数据范围：按最保守的 CUSTOM 空明细落库")
    void shouldDefaultToEmptyCustomScope() {
        given(roleRegistrar.register(any(NewRole.class), anyLong())).willReturn(NEW_ROLE_ID);

        roleCreationService.create(command(CODE, NAME, null, List.of()));

        ArgumentCaptor<NewRole> captor = ArgumentCaptor.forClass(NewRole.class);
        verify(roleRegistrar).register(captor.capture(), anyLong());
        // 选 CUSTOM 而不是 ALL：两者选错的代价不对称，空明细最多让人发现「怎么看不到」，
        // 而 ALL 漏配可能几个月都没人注意到多看了别人的货主
        assertThat(captor.getValue().dataScopeType()).isEqualTo(DataScopeType.CUSTOM);
        assertThat(captor.getValue().ownerIds()).isEmpty();
        // 校验器仍要收到这一组默认值：它是三条配置入口共用的唯一判定点
        verify(dataScopeOwnerValidator).requireConfigurable(DataScopeType.CUSTOM, Set.of());
    }

    @Test
    @DisplayName("指定 CUSTOM 与货主：原样落库，并把类型与明细交给校验器")
    void shouldPassDataScopeToRegistrar() {
        given(roleRegistrar.register(any(NewRole.class), anyLong())).willReturn(NEW_ROLE_ID);

        roleCreationService.create(new CreateRoleCommand(CODE, NAME, null, List.of(),
                DataScopeType.CUSTOM, List.of(7L, 8L)));

        ArgumentCaptor<NewRole> captor = ArgumentCaptor.forClass(NewRole.class);
        verify(roleRegistrar).register(captor.capture(), anyLong());
        assertThat(captor.getValue().dataScopeType()).isEqualTo(DataScopeType.CUSTOM);
        assertThat(captor.getValue().ownerIds()).containsExactlyInAnyOrder(7L, 8L);
        verify(dataScopeOwnerValidator).requireConfigurable(DataScopeType.CUSTOM, Set.of(7L, 8L));
    }

    @Test
    @DisplayName("留痕：建角色成功写一条 ROLE/CREATE，对象标识与摘要都用角色编码")
    void shouldRecordCreationAudit() {
        given(roleRegistrar.register(any(NewRole.class), anyLong())).willReturn(NEW_ROLE_ID);

        roleCreationService.create(command("  CS_STAFF  ", " 客服 ", " 对接货主 ", List.of(4L, 3L)));

        AuditEntry entry = captureSingleAudit();
        assertThat(entry.bizType()).isEqualTo(AuditBizType.ROLE);
        assertThat(entry.action()).isEqualTo(AuditAction.CREATE);
        // 编码是程序引用角色的标识，也是管理端追溯时输入的那一个
        assertThat(entry.bizId()).isEqualTo(CODE);
        assertThat(entry.summary()).isEqualTo("新建角色 " + CODE);
        assertThat(entry.beforeValue()).isNull();
        assertThat(entry.afterValue()).containsOnlyKeys(AuditSnapshotKeys.ROLE);
        assertThat(entry.afterValue())
                .containsEntry("code", CODE)
                .containsEntry("name", NAME)
                .containsEntry("builtin", false)
                .containsEntry("status", "ACTIVE")
                .containsEntry("dataScopeType", "CUSTOM");
    }

    /**
     * 取回本次唯一一条审计内容。
     *
     * @return 提交给审计端口的日志内容
     */
    private AuditEntry captureSingleAudit() {
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRecorder).record(captor.capture());
        return captor.getValue();
    }

    private static CreateRoleCommand command(String code, String name, String remark, List<Long> menuIds) {
        return new CreateRoleCommand(code, name, remark, menuIds, null, null);
    }

    /**
     * 构造一个带约束名的完整性冲突，形如 PostgreSQL 抛出的
     * {@code ERROR: duplicate key value violates unique constraint "ux_sys_role_code"}。
     *
     * @param constraintName 约束名
     * @return Spring 翻译后的异常，其根因消息里带约束名
     */
    private static DataIntegrityViolationException constraintViolation(String constraintName) {
        return new DataIntegrityViolationException("建角色写入违反约束",
                new SQLException("ERROR: 违反约束 \"" + constraintName + "\""));
    }

    /** 断言建角色被拒绝并取回异常；意外成功时直接让测试失败，避免它被当成「没抛异常」的空白通过。 */
    private BizException failureOf(CreateRoleCommand command) {
        try {
            roleCreationService.create(command);
        } catch (BizException e) {
            return e;
        }
        throw new AssertionError("期望建角色被拒绝，但建角色成功了");
    }
}

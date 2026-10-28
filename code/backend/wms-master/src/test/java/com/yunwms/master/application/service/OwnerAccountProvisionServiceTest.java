package com.yunwms.master.application.service;

import com.yunwms.master.application.command.ProvisionOwnerAccountCommand;
import com.yunwms.master.application.dto.ProvisionedOwnerAccount;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerAddress;
import com.yunwms.master.domain.model.OwnerAuditEvent;
import com.yunwms.master.domain.model.OwnerStatus;
import com.yunwms.master.domain.model.SkuPrefix;
import com.yunwms.master.domain.port.AuditTrail;
import com.yunwms.master.domain.port.CurrentOperator;
import com.yunwms.master.domain.port.OwnerAccountLookup;
import com.yunwms.master.domain.port.OwnerAccountProvisioner;
import com.yunwms.master.domain.port.OwnerRepository;
import com.yunwms.web.api.ErrorCode;
import com.yunwms.web.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * 货主主账号开通用例的单测：端口全部替换为 mock，不起 Spring、不连库。
 *
 * <p>要钉住五件事：
 * <ul>
 *   <li><b>校验顺序</b>——「状态」排在「是否已有账号」之前。一个已终止的货主，管理员最该知道的是
 *       它已经终止；顺序反了，终止货主的重复提交会得到「已开通主账号」，把人引去查账号，
 *       而真正该做的是看货主为什么终止。这里用「状态被拒时不发生任何反查」把它钉死；</li>
 *   <li><b>两种 409 的文案不同</b>——「该货主已开通主账号，登录名为 X」与
 *       「登录名 X 已被其他账号占用」的处置动作不同，合并成一句时管理员分不清是自己重复点了
 *       还是数据坏了；</li>
 *   <li><b>登录名取自货主系统编码</b>，不接受调用方给：命令里根本没有登录名字段，
 *       这一点只能靠「端口收到的登录名等于 owner.code()」来证；</li>
 *   <li><b>被拒绝的请求不建号</b>——建号事后无法回退，因此每条拒绝路径都要断言没有调用建号端口；</li>
 *   <li><b>开号挂在该货主的时间线上</b>——审计的对象标识是货主编码而不是账号标识，
 *       快照里只有登录名与账号标识，没有密码。</li>
 * </ul>
 */
@DisplayName("货主主账号开通")
class OwnerAccountProvisionServiceTest {

    /** 操作人标识，取自会话；本用例不保留它，只要求存在。 */
    private static final long OPERATOR_ID = 7L;

    /** 目标货主标识。 */
    private static final long OWNER_ID = 5L;

    /** 目标货主的系统编码，同时也是主账号登录名。 */
    private static final String OWNER_CODE = "OWNER0005";

    /** 另一个货主的标识，用于构造「登录名被别的货主占了」。 */
    private static final long OTHER_OWNER_ID = 9L;

    /** 初始密码；本用例只验证它被原样交给建号端口，密码策略属对端用例。 */
    private static final String INITIAL_PASSWORD = "Owner@1234";

    /** 姓名，含两侧空白，用于验证交给端口之前已归一化。 */
    private static final String DISPLAY_NAME = "  张三  ";

    /** 建号端口返回的新账号标识。 */
    private static final long ACCOUNT_ID = 42L;

    private OwnerRepository ownerRepository;
    private OwnerAccountLookup ownerAccountLookup;
    private OwnerAccountProvisioner ownerAccountProvisioner;
    private CurrentOperator currentOperator;
    private AuditTrail auditTrail;
    private OwnerAccountProvisionService service;

    @BeforeEach
    void setUp() {
        ownerRepository = mock(OwnerRepository.class);
        ownerAccountLookup = mock(OwnerAccountLookup.class);
        ownerAccountProvisioner = mock(OwnerAccountProvisioner.class);
        currentOperator = mock(CurrentOperator.class);
        auditTrail = mock(AuditTrail.class);
        service = new OwnerAccountProvisionService(
                ownerRepository, ownerAccountLookup, ownerAccountProvisioner, currentOperator, auditTrail);

        given(currentOperator.currentUserId()).willReturn(OptionalLong.of(OPERATOR_ID));
        given(ownerAccountLookup.findOwnerIdByLoginName(OWNER_CODE)).willReturn(OptionalLong.empty());
        given(ownerAccountProvisioner.createMainAccount(anyLong(), anyString(), anyString(), anyString()))
                .willReturn(ACCOUNT_ID);
    }

    @Test
    @DisplayName("会话无效：拒绝，且不读货主、不反查、不建号")
    void shouldRejectWhenSessionIsMissing() {
        given(currentOperator.currentUserId()).willReturn(OptionalLong.empty());

        assertThatThrownBy(() -> service.provision(command(DISPLAY_NAME)))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verifyNoInteractions(ownerRepository, ownerAccountLookup, ownerAccountProvisioner);
    }

    @Test
    @DisplayName("货主不存在或已删除：40400，提示为「货主不存在」，不反查、不建号")
    void shouldRejectUnknownOwner() {
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.provision(command(DISPLAY_NAME)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    assertThat(e.getMessage()).isEqualTo("货主不存在");
                });

        verifyNoInteractions(ownerAccountLookup, ownerAccountProvisioner);
    }

    @Test
    @DisplayName("四个非合作中状态逐个拒绝：40900，提示带当前状态，且不反查、不建号")
    void shouldRejectNonActiveOwner() {
        for (OwnerStatus status : new OwnerStatus[]{
                OwnerStatus.DRAFT, OwnerStatus.POTENTIAL, OwnerStatus.SUSPENDED, OwnerStatus.TERMINATED}) {
            given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(owner(status)));

            assertThatThrownBy(() -> service.provision(command(DISPLAY_NAME)))
                    .as("状态 %s 不是合作中，必须被拒绝", status)
                    .isInstanceOfSatisfying(BizException.class, e -> {
                        assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                        assertThat(e.getMessage())
                                .as("提示必须说清当前状态，否则管理员不知道该先做什么")
                                .contains(status.name())
                                .contains("ACTIVE")
                                .contains("合作中");
                    });
        }

        // 状态判定必须先于「是否已有账号」：否则终止货主的重复提交会被报成「已开通主账号」
        verify(ownerAccountLookup, never()).findOwnerIdByLoginName(anyString());
        verify(ownerAccountProvisioner, never()).createMainAccount(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("该货主已有主账号：40900，提示带已有登录名，不建第二个")
    void shouldRejectDuplicateProvision() {
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.ACTIVE)));
        given(ownerAccountLookup.findOwnerIdByLoginName(OWNER_CODE)).willReturn(OptionalLong.of(OWNER_ID));

        assertThatThrownBy(() -> service.provision(command(DISPLAY_NAME)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(e.getMessage())
                            .as("管理员要靠这句话确认「刚才那次其实成功了」，因此必须带出登录名")
                            .contains("已开通主账号")
                            .contains(OWNER_CODE);
                });

        verify(ownerAccountProvisioner, never()).createMainAccount(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("登录名归属其他货主：40900，提示是「其他账号占用」而不是「已开通」，不建号")
    void shouldRejectLoginNameTakenByAnotherOwner() {
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.ACTIVE)));
        given(ownerAccountLookup.findOwnerIdByLoginName(OWNER_CODE)).willReturn(OptionalLong.of(OTHER_OWNER_ID));

        assertThatThrownBy(() -> service.provision(command(DISPLAY_NAME)))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(e.getMessage())
                            // 两种 409 的文案必须能区分：这一句的处置是「查清登录名被谁占了」
                            .contains("其他账号占用")
                            .contains(OWNER_CODE)
                            .doesNotContain("已开通主账号");
                });

        verify(ownerAccountProvisioner, never()).createMainAccount(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("姓名为空或全空白：40000，且不建号")
    void shouldRejectBlankDisplayName() {
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.ACTIVE)));

        for (String blank : new String[]{null, "", "   "}) {
            assertThatThrownBy(() -> service.provision(command(blank)))
                    .as("姓名 [%s] 必须被拒绝", blank)
                    .isInstanceOfSatisfying(BizException.class, e -> {
                        assertThat(e.errorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
                        assertThat(e.getMessage()).contains("姓名");
                    });
        }

        verify(ownerAccountProvisioner, never()).createMainAccount(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("成功：登录名取自货主编码、货主标识正确、姓名已去两侧空白、密码原样交给建号端口")
    void shouldProvisionWithOwnerCodeAsLoginName() {
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.ACTIVE)));

        ProvisionedOwnerAccount result = service.provision(command(DISPLAY_NAME));

        assertThat(result.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.loginName())
                .as("登录名由系统按货主系统编码生成，不接受调用方指定")
                .isEqualTo(OWNER_CODE);
        verify(ownerAccountProvisioner).createMainAccount(eq(OWNER_ID), eq(OWNER_CODE), eq("张三"), eq(INITIAL_PASSWORD));
        // 开号不是货主档案的写操作：对货主只有那一次读，不写任何字段、也不改状态
        verify(ownerRepository).findById(OWNER_ID);
        verifyNoMoreInteractions(ownerRepository);
    }

    @Test
    @DisplayName("审计：开号写一条 OWNER_ACCOUNT_OPENED，挂货主编码，after 含登录名与账号标识")
    void shouldRecordAccountProvision() {
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.ACTIVE)));

        service.provision(command(DISPLAY_NAME));

        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.captor();
        ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.captor();
        verify(auditTrail).record(
                eq(OwnerAuditEvent.OWNER_ACCOUNT_OPENED), eq(OWNER_CODE), before.capture(), after.capture());

        // 对象标识取货主编码：从货主详情追溯时要能串起「建档 → 改状态 → 开号」整条链
        assertThat(before.getValue()).as("开号不是对货主档案的修改，没有变更前值").isNull();
        assertThat(after.getValue())
                .containsEntry("loginName", OWNER_CODE)
                // 标识按字符串记录：与接口层「标识一律以字符串传输」同口径
                .containsEntry("accountId", String.valueOf(ACCOUNT_ID));
    }

    @Test
    @DisplayName("审计：被拒绝的开号不写审计（账号与留痕都不该出现）")
    void shouldNotRecordWhenProvisionIsRejected() {
        given(ownerRepository.findById(OWNER_ID)).willReturn(Optional.of(owner(OwnerStatus.DRAFT)));

        assertThatThrownBy(() -> service.provision(command(DISPLAY_NAME)))
                .isInstanceOf(BizException.class);

        verifyNoInteractions(auditTrail);
    }

    /**
     * 构造一次开通请求：货主标识与密码固定，只有姓名随用例变化。
     *
     * <p>货主当前状态不在这里给——它来自库里的那条记录，由 {@code findById} 的返回值决定。
     *
     * @param displayName 主账号姓名
     * @return 开通入参
     */
    private static ProvisionOwnerAccountCommand command(String displayName) {
        return new ProvisionOwnerAccountCommand(OWNER_ID, displayName, INITIAL_PASSWORD);
    }

    /**
     * 库中已存在的那条货主记录，供 {@code findById} 返回。
     *
     * @param status 当前状态
     * @return 已存在的货主，编码固定为 {@link #OWNER_CODE}
     */
    private static Owner owner(OwnerStatus status) {
        return new Owner(
                OWNER_ID,
                OWNER_CODE,
                "深圳某某贸易",
                "某某贸易",
                null,
                "EXT-001",
                new SkuPrefix("AMZ2"),
                "BRAND",
                "深圳某某贸易有限公司",
                status,
                new OwnerAddress("CN", "CN-SH", "深圳", "南山区", "518000", "科技园路 1 号"),
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                OPERATOR_ID,
                Instant.parse("2026-01-01T00:00:00Z"),
                OPERATOR_ID);
    }
}

package com.yunwms.app;

import com.yunwms.auth.application.service.DataScopeResolver;
import com.yunwms.auth.domain.model.DataScope;
import com.yunwms.master.domain.model.NewOwner;
import com.yunwms.master.domain.model.Owner;
import com.yunwms.master.domain.model.OwnerAddress;
import com.yunwms.master.domain.model.OwnerStatus;
import com.yunwms.master.domain.model.SkuPrefix;
import com.yunwms.master.domain.port.OwnerCodeGenerator;
import com.yunwms.master.domain.port.OwnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * 货主持久化与 {@code V033} 权限点的端到端验证。
 *
 * <p>要证的是单测证不了的几件事：XML 里的列名与条件真的能在 PostgreSQL 上跑通、
 * 插入后按标识读回的事实与写入一致、初始状态确实由调用方传的值落库（不是列默认值）、
 * 两条存在性判定的 {@code deleted_at} 口径与各自的唯一索引一致，以及跨模块装配
 * （取号端口 → 取号引擎）在真实容器里确实接上了。
 *
 * <p>本类不验证字典规则：{@code OwnerDictionaryValidator} 的判定在 {@code wms-master} 的单测里
 * 用假字典覆盖，这里再跑一遍只是把同一批断言搬到更慢的环境里。
 *
 * <p>数据不清理：整个测试 JVM 共用一个库，各用例因此各自持有独立的业务键
 * （SKU 前缀与外部码都带唯一索引），不共享同一行数据——共用一行会让「单独跑一个用例」
 * 与「整类一起跑」得到不同结果，而这类差异往往要等到某次重跑才暴露。
 */
@DisplayName("货主持久化与权限点")
class OwnerPersistenceIntegrationTest extends AbstractIntegrationTest {

    /** 操作人标识；{@code created_by} 无外键，取一个固定值即可。 */
    private static final long OPERATOR_ID = 1L;

    /**
     * 生效数据范围固定为「全部货主」。
     *
     * <p><b>为什么必须是 mock：</b>列表与计数语句已经接入数据权限过滤，而本类的调用发生在请求之外，
     * 解析器按「没有登录上下文」给出「无范围」，那两条语句会一律返回空——那测到的不是 SQL 映射，
     * 而是「没有调用方上下文」。真实的范围行为在 {@code OwnerDataScopeStaffIntegrationTest} 里用
     * MockMvc 覆盖，本类只需一个能看见全部货主的上下文。
     */
    @MockitoBean
    private DataScopeResolver dataScopeResolver;

    @Autowired
    private OwnerRepository ownerRepository;

    @Autowired
    private OwnerCodeGenerator ownerCodeGenerator;

    @BeforeEach
    void useFullDataScope() {
        given(dataScopeResolver.resolve()).willReturn(DataScope.all());
    }

    @Test
    @DisplayName("V033：客户管理菜单与三个权限点就位，且挂在正确的父节点下")
    void shouldSeedOwnerMenusAndPermissions() {
        Long moduleMenuId = menuId("M", "/master");
        Long ownerMenuId = menuId("C", "/master/owners");

        assertThat(queryMenuParentId(moduleMenuId))
                .as("客户管理是与系统管理同级的顶层目录")
                .isNull();
        assertThat(queryMenuParentId(ownerMenuId))
                .as("货主档案挂在客户管理下")
                .isEqualTo(moduleMenuId);

        for (String perms : List.of("system:owner:list", "system:owner:create", "system:owner:update")) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM sys_menu WHERE perms = ? AND menu_type = 'F'", Integer.class, perms);
            assertThat(count).as("权限点 %s 应存在且唯一", perms).isEqualTo(1);
            Long permsParentId = jdbcTemplate.queryForObject(
                    "SELECT parent_id FROM sys_menu WHERE perms = ?", Long.class, perms);
            assertThat(permsParentId).as("权限点 %s 应挂在货主档案下", perms).isEqualTo(ownerMenuId);
        }
    }

    @Test
    @DisplayName("写入货主后按标识读回：字段一致、状态为传入的草稿、审计列由库填充")
    void shouldReadBackInsertedOwner() {
        String code = ownerCodeGenerator.next();

        long id = ownerRepository.insert(
                newOwner("集成测试货主-读回", "TGX2", "EXT-TGX-0001"), code, OwnerStatus.DRAFT, OPERATOR_ID);

        Owner loaded = ownerRepository.findById(id).orElseThrow();
        assertThat(loaded.code()).isEqualTo(code);
        assertThat(code).as("编码形如 OWNER0001").matches("^OWNER\\d{4}$");
        assertThat(loaded.name()).isEqualTo("集成测试货主-读回");
        assertThat(loaded.shortName()).isEqualTo("测试简称");
        assertThat(loaded.externalCode()).isEqualTo("EXT-TGX-0001");
        assertThat(loaded.skuPrefix().value()).isEqualTo("TGX2");
        assertThat(loaded.ownerType()).isEqualTo("BRAND");
        assertThat(loaded.settlementEntity()).isEqualTo("集成测试结算主体");
        assertThat(loaded.address().country()).isEqualTo("US");
        assertThat(loaded.address().province()).isEqualTo("US-CA");
        assertThat(loaded.address().postalCode()).isEqualTo("95110");
        // 状态由调用方传入，不再依赖列默认值：草稿入口显式落 DRAFT
        assertThat(loaded.status()).isEqualTo(OwnerStatus.DRAFT);
        // created_at / updated_at 由 DEFAULT now() 填充，应用侧不传
        assertThat(loaded.createdAt()).isNotNull();
        assertThat(loaded.updatedAt()).isNotNull();
        assertThat(loaded.createdBy()).isEqualTo(OPERATOR_ID);
    }

    @Test
    @DisplayName("初始状态按传入值落库：传 ACTIVE 读回 ACTIVE，而不是列默认值 DRAFT")
    void shouldPersistInitialStatusPassedByCaller() {
        long id = ownerRepository.insert(
                newOwner("集成测试货主-直接提交", "TGX5", "EXT-TGX-0004"),
                ownerCodeGenerator.next(), OwnerStatus.ACTIVE, OPERATOR_ID);

        assertThat(ownerRepository.findById(id).orElseThrow().status())
                .as("「直接提交」入口落 ACTIVE；写入若被略过，这里会读到列默认值 DRAFT")
                .isEqualTo(OwnerStatus.ACTIVE);
    }

    @Test
    @DisplayName("初始状态为空：直接拒绝，不静默落到列默认值 DRAFT")
    void shouldRejectNullInitialStatus() {
        // 号已经取到但不会落库，这是预期：守卫在建档之前失败，取号本身是独立提交的副作用
        String code = ownerCodeGenerator.next();

        assertThatThrownBy(() -> ownerRepository.insert(
                newOwner("集成测试货主-空状态", "TGX6", "EXT-TGX-0005"), code, null, OPERATOR_ID))
                .as("空状态若被放过，MyBatis-Plus 会整列略过，落成列默认值 DRAFT 而不报错")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("列表与计数：按关键字、状态、客户类型筛选，口径与写入一致")
    void shouldFilterPageAndCount() {
        String name = "集成测试货主-筛选";
        String code = ownerCodeGenerator.next();
        ownerRepository.insert(
                newOwner(name, "TGX3", "EXT-TGX-0002"), code, OwnerStatus.DRAFT, OPERATOR_ID);

        long count = ownerRepository.count(OwnerStatus.DRAFT, "BRAND", name);
        List<Owner> page = ownerRepository.findPage(OwnerStatus.DRAFT, "BRAND", name, 10, 0);

        assertThat(count).isEqualTo(1);
        assertThat(page).hasSize(1);
        assertThat(page.get(0).code()).isEqualTo(code);

        // 条件之间是「与」：换一个不匹配的类型必须查不到，否则上面的命中不能证明筛选生效
        assertThat(ownerRepository.count(OwnerStatus.DRAFT, "FORWARDER", name)).isZero();
    }

    @Test
    @DisplayName("存在性判定：前缀按全表口径、外部码按未删除口径、空外部码不算冲突")
    void shouldReflectUniqueIndexScopes() {
        long id = ownerRepository.insert(
                newOwner("集成测试货主-判重", "TGX4", "EXT-TGX-0003"),
                ownerCodeGenerator.next(), OwnerStatus.DRAFT, OPERATOR_ID);

        assertThat(ownerRepository.existsBySkuPrefix("TGX4"))
                .as("前缀是全表唯一索引，已写入即被占用")
                .isTrue();
        assertThat(ownerRepository.existsByExternalCode("EXT-TGX-0003")).isTrue();
        assertThat(ownerRepository.existsBySkuPrefix("ZZZ9")).isFalse();
        assertThat(ownerRepository.existsByExternalCode(null))
                .as("外部码允许为空，空与空不算冲突")
                .isFalse();

        // 软删除之后两条口径必须分叉：前缀仍被占着（全表唯一索引），外部码放行（部分唯一索引）。
        // 若两者都被判为「可用」，用户提交时会撞上 ux_owner_sku_prefix，拿到的是一句无法自查的库错误
        jdbcTemplate.update("UPDATE owner SET deleted_at = now() WHERE id = ?", id);

        assertThat(ownerRepository.existsBySkuPrefix("TGX4"))
                .as("软删除不释放 SKU 前缀，与全表唯一索引一致")
                .isTrue();
        assertThat(ownerRepository.existsByExternalCode("EXT-TGX-0003"))
                .as("软删除后外部码可被新货主使用，与部分唯一索引一致")
                .isFalse();
        assertThat(ownerRepository.findById(id))
                .as("已删除的货主与不存在同口径")
                .isEmpty();
    }

    @Test
    @DisplayName("V036：删除货主的权限点就位，且挂在货主档案下")
    void shouldSeedOwnerDeletePermission() {
        // 删除单独成点（不复用 system:owner:update）：两项的失效后果不同，合并会让「能改电话」
        // 等于「能删客户」。这条断言钉住 V036 的落点与父节点，路径拼错时这里会立刻红
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM sys_menu WHERE perms = 'system:owner:delete' AND menu_type = 'F'",
                Integer.class);
        assertThat(count).as("system:owner:delete 应存在且唯一").isEqualTo(1);

        Long parentId = jdbcTemplate.queryForObject(
                "SELECT parent_id FROM sys_menu WHERE perms = 'system:owner:delete'", Long.class);
        assertThat(parentId)
                .as("删除货主应挂在货主档案（/master/owners）下")
                .isEqualTo(menuId("C", "/master/owners"));
    }

    @Test
    @DisplayName("状态写入与软删除：只改状态 / 置 deleted_at，且已删除的行不会被改活")
    void shouldUpdateStatusAndSoftDelete() {
        long id = ownerRepository.insert(
                newOwner("集成测试货主-流转写入", "TGH2", "EXT-TGH-0001"),
                ownerCodeGenerator.next(), OwnerStatus.DRAFT, OPERATOR_ID);

        ownerRepository.updateStatus(id, OwnerStatus.ACTIVE, 42L);

        Owner afterUpdate = ownerRepository.findById(id).orElseThrow();
        assertThat(afterUpdate.status()).isEqualTo(OwnerStatus.ACTIVE);
        // updated_by 必须跟着改：状态是谁改的只能从这里看出来（审计日志属 2.8）
        assertThat(afterUpdate.updatedBy()).isEqualTo(42L);

        assertThat(ownerRepository.softDelete(id, 42L)).as("第一行写入成功").isTrue();
        assertThat(ownerRepository.findById(id))
                .as("软删除之后按标识读不到，与「不存在」同口径")
                .isEmpty();
        assertThat(ownerRepository.softDelete(id, 42L))
                .as("重复删除命中零行：调用方据此回 404，而不是回一个看不出来发生过的成功")
                .isFalse();

        // 已软删除的行不能被改活：updateStatus 的 WHERE 里带 deleted_at IS NULL，这两条语句
        // 是手写 SQL，MyBatis-Plus 的逻辑删除不会替它们补条件
        ownerRepository.updateStatus(id, OwnerStatus.SUSPENDED, 42L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM owner WHERE id = ?", String.class, id))
                .as("已删除的货主不得被状态流转改活")
                .isEqualTo("ACTIVE");
    }

    /**
     * 构造一条待写入的货主。
     *
     * <p>唯一键由调用方给：每个用例各用一组，互不重叠。编码不在这里生成——
     * 它由取号引擎按 {@code OWNER} 规则发出，用例需要拿到实际值才能断言读回结果。
     *
     * @param name         客户名称
     * @param skuPrefix    SKU 前缀，需满足 4 位大写字母或数字且不含 0/O/1/I
     * @param externalCode 外部码
     * @return 待落库的货主
     */
    private static NewOwner newOwner(String name, String skuPrefix, String externalCode) {
        return new NewOwner(
                name,
                "测试简称",
                "Integration Test Owner A",
                externalCode,
                new SkuPrefix(skuPrefix),
                "BRAND",
                "集成测试结算主体",
                new OwnerAddress("US", "US-CA", "San Jose", "Santa Clara", "95110", "1 Infinite Loop"),
                "集成测试备注");
    }

    /**
     * 按类型与路径取菜单标识。
     *
     * @param menuType 菜单类型：M / C / F
     * @param path     前端路由路径
     * @return 菜单标识
     */
    private Long menuId(String menuType, String path) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE menu_type = ? AND path = ?", Long.class, menuType, path);
    }

    /**
     * 取父节点标识。
     *
     * @param menuId 菜单标识
     * @return 父节点标识；顶层目录为 {@code null}
     */
    private Long queryMenuParentId(Long menuId) {
        return jdbcTemplate.queryForObject(
                "SELECT parent_id FROM sys_menu WHERE id = ?", Long.class, menuId);
    }
}

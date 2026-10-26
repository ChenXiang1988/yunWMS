package com.yunwms.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 货主账号计数端口的单测。
 *
 * <p>要证的是这条链路口径里的两件事：<b>停用的账号仍计入</b>（计数条件里没有 {@code status}），
 * <b>已逻辑删除的账号不计入</b>（{@code sys_user} 的 {@code deleted_at} 落在 MyBatis-Plus 的逻辑删除
 * 字段上，因此计数语句会被自动补上 {@code deleted_at IS NULL}）。
 *
 * <p>为什么不起库：{@code wms-auth} 的测试没有数据库依赖，加一套只为本用例服务的容器不划算。
 * 语句在真实 PostgreSQL 上的效果——停用账号被数进去、{@code deleted_at} 非空的被排除——由
 * {@code wms-app} 的 {@code OwnerLifecycleIntegrationTest}（影响清单用例）端到端覆盖，
 * 两条测试分工：这里盯「条件有没有被悄悄加上别的过滤」，那边盯「真实库上数出来是几」。
 *
 * <p>为什么要镜像一次逻辑删除配置：{@code logic-delete-field: deletedAt} 的权威定义在
 * {@code wms-app} 的 {@code application.yml}，本模块的测试 classpath 上没有那个文件。不镜像的话
 * 表信息里不会有逻辑删除字段，这条断言就变成了「什么都没检查」。镜像之后它能拦住真正的问题：
 * 实体上的 {@code deletedAt} 被改名或删掉时，全局配置指向的字段就找不到，计数会把已删除的账号
 * 也算进去——那种失效在编译期与真实库上都看不出来，只会在影响清单上少数或多算。
 */
@DisplayName("货主账号计数")
class OwnerAccountQueryImplTest {

    private static final long OWNER_ID = 7001L;

    private SysUserMapper sysUserMapper;
    private OwnerAccountQueryImpl query;

    /**
     * 按 {@code application.yml} 的逻辑删除配置登记 {@code sys_user} 的表信息。
     *
     * <p>不能省：计数条件要按实体属性和列名对应关系拼，不登记就没有 lambda 缓存，条件根本拼不出来。
     *
     * <p>先 {@code remove} 再登记：表信息是静态缓存，同一 JVM 里别的测试类可能已用不含逻辑删除的
     * 配置登记过同一个实体，不先清掉就会拿到那份缓存，本类的断言随执行顺序时真时假。
     */
    @BeforeAll
    static void registerTableInfoWithLogicDelete() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        GlobalConfigUtils.setGlobalConfig(configuration, logicDeleteConfig());
        TableInfoHelper.remove(SysUserEntity.class);
        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(configuration, ""), SysUserEntity.class);
    }

    /** 清掉本类登记的表信息，避免把带逻辑删除的配置留给后续测试类。 */
    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(SysUserEntity.class);
    }

    @BeforeEach
    void setUp() {
        sysUserMapper = mock(SysUserMapper.class);
        query = new OwnerAccountQueryImpl(sysUserMapper);
    }

    @Test
    @DisplayName("停用账号仍计入：计数条件只有 owner_id，不按状态筛")
    void shouldCountDisabledAccountsBecauseOnlyOwnerIdIsFiltered() {
        given(sysUserMapper.selectCount(any())).willReturn(2L);

        long count = query.countByOwnerId(OWNER_ID);

        assertThat(count).isEqualTo(2L);
        // 加了 status 条件就等于把停用的账号排除掉：管理员会以为终止没有账号要处理
        assertThat(capturedWrapperSql()).contains("owner_id").doesNotContain("status");
        // ck_user_subject 已保证 owner_id 非空即货主账号，再按 subject_type 过滤是第二处定义
        assertThat(capturedWrapperSql()).doesNotContain("subject_type");
    }

    @Test
    @DisplayName("已删除账号不计入：deleted_at 是逻辑删除字段，计数语句自动带上 deleted_at IS NULL")
    void shouldExcludeDeletedAccountsThroughLogicDelete() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(SysUserEntity.class);

        assertThat(tableInfo.isWithLogicDelete()).isTrue();
        assertThat(tableInfo.getLogicDeleteFieldInfo().getColumn()).isEqualTo("deleted_at");
        assertThat(tableInfo.getLogicDeleteSql(false, true)).contains("deleted_at IS NULL");
    }

    @Test
    @DisplayName("计数没有返回结果：失败而不是返回 0，避免影响清单静默少算整批账号")
    void shouldFailWhenCountHasNoResult() {
        given(sysUserMapper.selectCount(any())).willReturn(null);

        assertThatThrownBy(() -> query.countByOwnerId(OWNER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(String.valueOf(OWNER_ID));
    }

    /** 与 {@code wms-app} 的 {@code application.yml} 一致的逻辑删除配置：字段、未删除取值、删除取值。 */
    private static GlobalConfig logicDeleteConfig() {
        return new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig()
                .setLogicDeleteField("deletedAt")
                .setLogicNotDeleteValue("null")
                .setLogicDeleteValue("now()"));
    }

    @SuppressWarnings("unchecked")
    private String capturedWrapperSql() {
        ArgumentCaptor<Wrapper<SysUserEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(sysUserMapper).selectCount(captor.capture());
        return captor.getValue().getSqlSegment();
    }
}

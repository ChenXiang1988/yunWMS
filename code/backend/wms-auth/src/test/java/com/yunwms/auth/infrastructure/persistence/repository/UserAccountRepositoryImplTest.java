package com.yunwms.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yunwms.auth.infrastructure.persistence.entity.SysUserEntity;
import com.yunwms.auth.infrastructure.persistence.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 登录主体仓储的单测。
 *
 * <p>只验证「归零时要一并清掉失败窗口起点」这条写语句的形状：不起 Spring 上下文、不连库。
 * 语句在真实库上的效果（窗口过期后计数从 1 重新开始、成功登录后下一轮重新计时）由登录链路的手工验证覆盖。
 */
@DisplayName("登录主体仓储")
class UserAccountRepositoryImplTest {

    private static final long USER_ID = 1001L;
    private static final Instant NOW = Instant.parse("2025-01-01T08:00:00Z");
    private static final String NEW_PASSWORD_HASH = "$2a$10$0123456789012345678901234567890123456789012345678901";

    private SysUserMapper sysUserMapper;
    private UserAccountRepositoryImpl repository;

    /**
     * {@code LambdaUpdateWrapper} 的列名解析依赖 MyBatis-Plus 的表信息缓存，正常由容器启动扫描实体时建立。
     * 本测试不起容器，需要先手工登记一次，否则 {@code set} 拼不出列名，被测语句根本无法成形。
     */
    @BeforeAll
    static void registerTableInfo() {
        TableInfoHelper.initTableInfo(
                new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), SysUserEntity.class);
    }

    @BeforeEach
    void setUp() {
        sysUserMapper = mock(SysUserMapper.class);
        repository = new UserAccountRepositoryImpl(sysUserMapper);
    }

    @Test
    @DisplayName("成功登录：写登录时间、计数归零，并把失败窗口起点一并置空")
    void shouldClearFailureWindowOnSuccessfulLogin() {
        given(sysUserMapper.update(isNull(), any())).willReturn(1);

        repository.recordSuccessfulLogin(USER_ID, NOW);

        assertThat(capturedSetSql())
                .contains("last_login_at", "failed_attempts", "locked_until", "last_failed_at");
    }

    @Test
    @DisplayName("锁定过期清理：计数归零，并把失败窗口起点一并置空")
    void shouldClearFailureWindowWhenExpiredLockIsCleared() {
        repository.clearExpiredLock(USER_ID, NOW);

        assertThat(capturedSetSql())
                .contains("failed_attempts", "locked_until", "last_failed_at");
    }

    @Test
    @DisplayName("改密：新哈希与「需先改密」标记在同一条语句里写入")
    void shouldWriteHashAndClearMarkInOneStatement() {
        given(sysUserMapper.update(isNull(), any())).willReturn(1);

        repository.updatePassword(USER_ID, NEW_PASSWORD_HASH, NOW);

        // 拆成两条语句时，中间失败会留下「标记已清、密码没变」的状态，初始密码从此不再被拦
        assertThat(capturedSetSql())
                .contains("password_hash", "password_updated_at", "must_change_password", "updated_at");
    }

    @Test
    @DisplayName("改密命中零行：账号已不存在，不能报告成功")
    void shouldFailWhenPasswordUpdateHitsNoRow() {
        given(sysUserMapper.update(isNull(), any())).willReturn(0);

        assertThatThrownBy(() -> repository.updatePassword(USER_ID, NEW_PASSWORD_HASH, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @SuppressWarnings("unchecked")
    private String capturedSetSql() {
        ArgumentCaptor<Wrapper<SysUserEntity>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(sysUserMapper).update(isNull(), captor.capture());
        return captor.getValue().getSqlSet();
    }
}

package com.yunwms.auth.infrastructure.datascope;

import com.yunwms.auth.domain.model.DataScope;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据范围 SQL 片段的单测：拦截器最终追加什么条件，完全由本类决定。
 *
 * <p>这里钉住的是三件在不连库时就能定死的事：
 * <ol>
 *   <li><b>只改已登记的语句</b>——未登记、或同一条语句里的其他表，一律返回 {@code null}，
 *       而且<b>连范围缓存都不去取</b>（取一次就等于为无关语句付三条查询的代价）；</li>
 *   <li><b>三种范围各自的片段</b>——{@code ALL} 不加条件、{@code NONE} 恒假、{@code RESTRICTED} 是
 *       带限定列的 {@code IN}；空集合在类型上不存在，因此没有「空集合被写成不加条件」的分支；</li>
 *   <li><b>标识以数值字面量输出</b>——不引号、不拼接：范围来自库，但它同样不该有机会变成 SQL 文本。</li>
 * </ol>
 *
 * <p>真实库上的行为（条件真的生效、列表与计数同范围、他方详情 404）由
 * {@code OwnerDataScopeIntegrationTest} 验证；本类只回答「片段长什么样」。
 */
@DisplayName("数据范围 SQL 片段")
class DataScopePermissionHandlerTest {

    private static final String COUNT_STATEMENT =
            "com.yunwms.master.infrastructure.persistence.mapper.OwnerMapper.countByFilter";
    private static final String UNREGISTERED_STATEMENT =
            "com.yunwms.master.infrastructure.persistence.mapper.OwnerMapper.selectByIdForUpdate";

    private DataScope scope;
    private DataScopePermissionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DataScopePermissionHandler(() -> scope);
    }

    @Test
    @DisplayName("未登记的语句：不追加条件，也不去取范围")
    void shouldIgnoreUnregisteredStatement() {
        assertThat(handlerForFailingSupplier().getSqlSegment(ownerTable(), null, UNREGISTERED_STATEMENT))
                .isNull();
    }

    @Test
    @DisplayName("已登记语句里的其他表：不追加条件，也不去取范围")
    void shouldIgnoreTablesOtherThanRegistered() {
        Table otherTable = new Table("sku");
        otherTable.setAlias(new Alias("s"));

        assertThat(handlerForFailingSupplier().getSqlSegment(otherTable, null, COUNT_STATEMENT)).isNull();
    }

    @Test
    @DisplayName("全部货主：不追加条件")
    void shouldAddNothingForAllScope() {
        scope = DataScope.all();

        assertThat(handler.getSqlSegment(ownerTable(), null, COUNT_STATEMENT)).isNull();
    }

    @Test
    @DisplayName("无范围：追加恒假条件 1 = 0，而不是「不加条件」")
    void shouldAddAlwaysFalseConditionForNoneScope() {
        scope = DataScope.none();

        Expression condition = handler.getSqlSegment(ownerTable(), null, COUNT_STATEMENT);

        // 无权必须留下痕迹：返回 null 会在插件里等价于「全部可见」，那是越权方向
        assertThat(condition).isNotNull();
        assertThat(condition.toString()).isEqualTo("1 = 0");
    }

    @Test
    @DisplayName("限定范围：追加带别名与数值标识的 IN 条件")
    void shouldAddInConditionForRestrictedScope() {
        scope = DataScope.restricted(Set.of(9L, 7L));

        Expression condition = handler.getSqlSegment(ownerTable(), null, COUNT_STATEMENT);

        assertThat(condition).isNotNull();
        // 标识按升序输出：同一份范围每次生成同一条 SQL，日志与执行计划才可比较
        assertThat(condition.toString()).isEqualTo("o.id IN (7, 9)");
        // 数值字面量而不是带引号的字符串：范围读自库，但它不该有机会变成 SQL 文本
        assertThat(condition.toString()).doesNotContain("'");
    }

    @Test
    @DisplayName("没有别名时用表名限定：多表语句里的条件不会指代不明")
    void shouldQualifyWithTableNameWhenAliasAbsent() {
        scope = DataScope.restricted(Set.of(7L));

        Expression condition = handler.getSqlSegment(new Table("owner"), null, COUNT_STATEMENT);

        assertThat(condition.toString()).isEqualTo("owner.id IN (7)");
    }

    @Test
    @DisplayName("每次调用都新建表达式节点：复用同一个实例会让一条语句的改写影响另一条")
    void shouldBuildFreshExpressionPerCall() {
        scope = DataScope.restricted(Set.of(7L));

        Expression first = handler.getSqlSegment(ownerTable(), null, COUNT_STATEMENT);
        Expression second = handler.getSqlSegment(ownerTable(), null, COUNT_STATEMENT);

        assertThat(first).isNotSameAs(second);
        assertThat(first.toString()).isEqualTo(second.toString());
    }

    /**
     * 一个「一旦取值就失败」的处理器，用来证明未登记的语句不会被解析。
     *
     * <p>用断言失败的取值函数而不是 mock 的 {@code verify(never())}：这条约束的失效后果是
     * 「每条无关 SQL 多三次查询」，用会炸的桩能让失效当场暴露，而不是靠某处漏写校验。
     *
     * @return 处理器
     */
    private static DataScopePermissionHandler handlerForFailingSupplier() {
        Supplier<DataScope> failing = () -> {
            throw new AssertionError("未登记的语句不该去取数据范围");
        };
        return new DataScopePermissionHandler(failing);
    }

    /**
     * 造一条与 {@code OwnerMapper.xml} 同形的表引用（{@code FROM owner o}）。
     *
     * @return 带别名的表
     */
    private static Table ownerTable() {
        Table table = new Table("owner");
        table.setAlias(new Alias("o"));
        return table;
    }
}

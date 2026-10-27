package com.yunwms.auth.infrastructure.datascope;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.yunwms.auth.domain.model.DataScope;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * 把当前请求的生效数据范围翻译成 SQL 的 {@code where} 片段。
 *
 * <p><b>只管已登记的语句：</b>清单见 {@link OwnerScopedStatements}。未登记的语句在这里直接返回
 * {@code null}，插件于是原样放行。这条短路同时解决三件事：
 * <ol>
 *   <li>不误伤——写路径、范围配置校验、含已删除货主的名称查询都读同一张表，但口径与可见性不同；</li>
 *   <li>不白花代价——解析范围最多要读三张表，对全仓每一条 {@code select} 都解析一次是纯浪费；</li>
 *   <li>不成环——解析范围自身要发查询，那些语句会再次进入本方法，只有「未登记即返回」才能保证
 *       它们不会又触发一次解析。</li>
 * </ol>
 *
 * <p><b>三种范围的映射（与规格逐条对应）：</b>
 * <ul>
 *   <li>{@link DataScope.All}：不追加条件，返回 {@code null}；</li>
 *   <li>{@link DataScope.None}：{@code 1 = 0}，结果集为空。这是「未登录 / 无启用角色 / 并集为空」的
 *       唯一落点——把「没配置」解释成「全部」是越权方向，且不会有任何报错；</li>
 *   <li>{@link DataScope.Restricted}：{@code alias.id IN (...)} 或子表的 {@code alias.owner_id IN (...)}。
 *       空集合在类型上就不存在（{@code DataScope.restricted} 已归一成 {@code None}），
 *       因此这里不需要再判一次空集合——那种判定的漏写方向同样是「不加条件」。</li>
 * </ul>
 *
 * <p><b>拼接方式：</b>货主标识一律用 {@link LongValue} 输出成数值字面量，不拼字符串；表名与列名
 * 来自清单里的常量，不来自请求。范围本身读自库，这里仍然不给它任何进入 SQL 文本的通道。
 *
 * <p><b>每次都新建表达式节点：</b>JSqlParser 的节点会被挂进语句树并在改写过程中被修改，
 * 复用同一个实例会让一次改写影响另一条语句（甚至不断累积条件）。
 *
 * <p><b>别名：</b>有别名带别名，没有别名用表名。两处都不带时，多表语句里的条件会指代不明——
 * 那会变成一次 SQL 报错，而不是一次静默的越权。
 *
 * <p><b>本类只依赖领域类型，不依赖请求级缓存的实现：</b>范围以 {@link Supplier} 传入，由装配处决定
 * 从哪里取。这样 {@code infrastructure} 不反向依赖应用层服务，本类也能脱离 Spring 与请求上下文单测。
 *
 * <p><b>为什么是「取值函数」而不是一个范围对象：</b>范围解析器要读库，读库要 Mapper，Mapper 要
 * {@code SqlSessionFactory}，而本处理器正是 {@code SqlSessionFactory} 创建时要收齐的插件之一——
 * 构造期直接持有解析结果或其来源会形成一条闭合的依赖环，容器启动即失败。改成在语句改写时取值，
 * 依赖环被推迟到「容器已就绪」之后，装配顺序不再需要被记住。
 */
public class DataScopePermissionHandler implements MultiDataPermissionHandler {

    private final Supplier<DataScope> dataScopeSupplier;

    /**
     * @param dataScopeSupplier 取当前请求的生效范围；同一请求内只解析一次，未登录时为「无范围」。
     *                          直到改写某条已登记语句时才会被调用
     */
    public DataScopePermissionHandler(Supplier<DataScope> dataScopeSupplier) {
        this.dataScopeSupplier = dataScopeSupplier;
    }

    /**
     * 取某条语句里某张表的过滤片段。
     *
     * @param table             SQL 里出现的表；未登记的表返回 {@code null}
     * @param where             已有条件，插件负责与返回值合并，本方法不参与合并
     * @param mappedStatementId MyBatis 语句标识，用于查清单
     * @return 要追加的条件；不需要追加时返回 {@code null}
     */
    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        OwnerScopedStatements.ScopedTable scopedTable = OwnerScopedStatements.find(mappedStatementId);
        if (scopedTable == null || !scopedTable.table().equalsIgnoreCase(table.getName())) {
            // 未登记，或同一条语句里被 join 进来的其他表：不追加。这里必须先于范围解析返回，理由见类注释
            return null;
        }
        return condition(dataScopeSupplier.get(), qualified(table, scopedTable.ownerColumn()));
    }

    /**
     * 把一个生效范围翻译成条件片段。
     *
     * @param scope       生效范围
     * @param ownerColumn 已限定表名（或别名）的货主列
     * @return 要追加的条件；{@link DataScope.All} 时为 {@code null}
     */
    private static Expression condition(DataScope scope, String ownerColumn) {
        return switch (scope) {
            case DataScope.All all -> null;
            // 恒假条件而不是「返回 null + 调用方自己判」：无条件与「无条件可用」在 SQL 里长得一样，
            // 让无权这条路径也必须留下痕迹，才不会某天被顺手优化成「不加条件更简单」
            case DataScope.None none -> new EqualsTo(new LongValue(1), new LongValue(0));
            case DataScope.Restricted restricted -> inCondition(ownerColumn, restricted.ownerIds());
        };
    }

    /**
     * 生成 {@code 列 IN (标识...)}。
     *
     * <p>标识先排序：同一份范围每次生成的 SQL 相同，日志与执行计划才可比较；集合本身是无序的
     * （{@code Set.copyOf}），不排序会让同一条查询每次打印出不同的语句。
     *
     * @param ownerColumn 已限定表名（或别名）的货主列
     * @param ownerIds    可见的货主标识，非空
     * @return IN 条件
     */
    private static Expression inCondition(String ownerColumn, Collection<Long> ownerIds) {
        List<LongValue> literals = ownerIds.stream().sorted().map(LongValue::new).toList();
        return new InExpression(new Column(ownerColumn), new ParenthesedExpressionList<>(literals));
    }

    /**
     * 得到带限定名的列。
     *
     * @param table       语句里的表
     * @param ownerColumn 判定归属的列名
     * @return 形如 {@code o.id} 或 {@code owner.id} 的列名
     */
    private static String qualified(Table table, String ownerColumn) {
        String qualifier = table.getAlias() != null ? table.getAlias().getName() : table.getName();
        return qualifier + "." + ownerColumn;
    }
}

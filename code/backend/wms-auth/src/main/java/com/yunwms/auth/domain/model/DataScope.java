package com.yunwms.auth.domain.model;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 生效的数据范围：一次请求真正能看见哪些货主。
 *
 * <p>与 {@link DataScopeType} 的区别：那个是<b>配置</b>（「范围怎么算」），本类型是<b>算完的结果</b>。
 * 两者刻意不合并——{@link DataScopeType#ASSIGNED} 与 {@link DataScopeType#CUSTOM} 算完都可能得到
 * 「某几个货主」，而查询过滤只关心那几个标识，不关心它们是怎么来的。
 *
 * <p>三种形态：
 * <ul>
 *   <li>{@link All}：不加维度条件；</li>
 *   <li>{@link None}：结果集为空（{@code 1 = 0}）。未登录、无启用角色、并集为空都落在这一档；</li>
 *   <li>{@link Restricted}：只含这些货主，<b>集合非空</b>；「一个都没有」由 {@link None} 表达。</li>
 * </ul>
 *
 * <p>两种「什么都没有」的写法只允许一种存在：空集合的限定范围会被 {@link #restricted} 归一成
 * {@link None}，而 {@link Restricted} 的构造器直接拒绝空集合。留着两套编码时，查询过滤侧
 * 迟早要为「空集合该不该拼条件」再判一次，而那种判定的错误方向是越权。
 *
 * <p><b>不提供「未配置即放行」的兜底</b>：规格要求空范围的结果集为空。把 {@link None} 与
 * {@link All} 做成两个显式取值，而不是用「空集合 + 一个布尔开关」表达，是因为写过滤条件时
 * 「忘了判空集合」会静默变成「不加条件」——那正是越权的那一侧。
 */
public sealed interface DataScope {

    /** 全部货主：查询不加维度条件。 */
    record All() implements DataScope {
    }

    /** 无任何可见货主：查询条件恒假。 */
    record None() implements DataScope {
    }

    /**
     * 限定货主集合，集合必须非空。
     *
     * @param ownerIds 可见的货主标识，不能为空集合，构造后不可变
     */
    record Restricted(Set<Long> ownerIds) implements DataScope {

        /**
         * 规范化并校验明细集合。
         *
         * <p>复制成不可变集合：范围是请求级缓存的值，被下游顺手改一笔会让同一请求里两次读取
         * 得到不同结果，而那种不一致事后无法复现。
         *
         * <p>空集合被拒绝而不是当成「等于 {@link None}」：同一件事只允许一种表达，
         * 否则过滤侧要额外判一次空集合，而漏判的方向是「不加条件」，即越权。
         *
         * @throws IllegalArgumentException {@code ownerIds} 为 {@code null} 或空集合，
         *                                  应改用 {@link DataScope#none()}
         * @throws NullPointerException     {@code ownerIds} 含 {@code null} 元素，属于调用方编程错误
         */
        public Restricted {
            if (ownerIds == null || ownerIds.isEmpty()) {
                throw new IllegalArgumentException("限定范围的货主集合不能为空，无权限请使用 DataScope.none()");
            }
            ownerIds = Set.copyOf(ownerIds);
        }
    }

    /**
     * 全部货主。
     *
     * @return {@link All} 实例
     */
    static DataScope all() {
        return new All();
    }

    /**
     * 无任何可见货主。
     *
     * @return {@link None} 实例
     */
    static DataScope none() {
        return new None();
    }

    /**
     * 限定货主集合；空集合归一成 {@link None}。
     *
     * <p>这是唯一推荐的构造入口：调用方（解析用例）经常拿着一个可能为空的集合，
     * 让它自己先判空只会让「忘了判」的那一处变成空集合的范围，而空集合与无权在过滤侧
     * 本该是同一件事。
     *
     * @param ownerIds 可见的货主标识，可为 {@code null} 或空集合
     * @return 非空集合返回 {@link Restricted}；{@code null} 或空集合返回 {@link None}
     */
    static DataScope restricted(Collection<Long> ownerIds) {
        if (ownerIds == null || ownerIds.isEmpty()) {
            return none();
        }
        return new Restricted(Set.copyOf(ownerIds));
    }

    /**
     * 把多个范围合并成生效范围，实现规格里的并集语义。
     *
     * <p>合并规则只有三条，按优先级排列：
     * <ol>
     *   <li>只要有一个 {@link All}，结果就是 {@link All}——规格要求「全部货主」吞并其他取值；</li>
     *   <li>否则把各 {@link Restricted} 的明细求并集；</li>
     *   <li>并集为空（含输入为空、只有 {@link None}、或只有空明细的 {@link Restricted}）时为
     *       {@link None}，即「没有配置就没有权限」。</li>
     * </ol>
     *
     * <p><b>调用方负责先把 {@link DataScopeType#ASSIGNED} 展开成 {@link Restricted}：</b>那一步要查库，
     * 而本方法是纯函数。放在这里会把「并集」与「实时推导」两件事绑死，也让本方法无法脱离数据库测试。
     *
     * @param scopes 待合并的范围，可为 {@code null}（按空集合处理，结果 {@link None}）
     * @return 合并后的生效范围，恒不为 {@code null}
     */
    static DataScope union(Collection<DataScope> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return none();
        }
        Set<Long> ownerIds = new LinkedHashSet<>();
        for (DataScope scope : scopes) {
            if (scope instanceof All) {
                // 提前返回：并集里出现「全部」之后，再关心明细就没有意义了
                return all();
            }
            if (scope instanceof Restricted restricted) {
                ownerIds.addAll(restricted.ownerIds());
            }
        }
        // 空并集必须是「无权」而不是「全部」：这是规格「空范围 fail-closed」在合并这一步的落点，
        // 由 restricted(...) 把空集合归一成 None
        return restricted(ownerIds);
    }
}

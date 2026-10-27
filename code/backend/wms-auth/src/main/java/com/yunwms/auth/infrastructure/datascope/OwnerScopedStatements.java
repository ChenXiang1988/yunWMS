package com.yunwms.auth.infrastructure.datascope;

import java.util.Map;

/**
 * 需要按当前请求的数据范围改写 SQL 的语句清单：语句标识 → 表名与货主列。
 *
 * <p><b>为什么是一份显式清单，而不是按表名全量启用：</b>数据权限插件挂在
 * {@code SqlSessionFactory} 上，作用范围是全仓的每一条语句。按表名匹配（例如「凡是 from owner
 * 的语句都加条件」）会把同一张表的写路径、配置校验与名称批量查询一起改掉——那几处的口径
 * 刻意与可见性不同（前缀判重要含已删除的货主、范围配置要能校验任意货主是否存在），
 * 改错之后没有任何编译期信号，只会让某些接口诡异地查不到数据。
 *
 * <p><b>为什么不按 Mapper 方法上的注解标注：</b>注解类型必须落在认证模块与主数据模块都能引用的
 * 地方。放本模块则 {@code wms-master} 要反向依赖 {@code wms-auth}（{@code AGENTS.md} 禁止跨模块
 * 直接依赖）；放 {@code wms-web} 则违反该模块只允许五个协议类型的白名单；为一条注解新开模块
 * 更不划算。字符串常量是唯一不新增任何编译依赖的表达方式。
 *
 * <p><b>代价与兜底：</b>被登记的 Mapper 方法改名时这里不会编译失败，语句会静默退回「不过滤」。
 * 因此 {@code OwnerDataScopeStaffIntegrationTest} 用真实库与 MockMvc 验证列表、详情与终止影响清单
 * 的范围行为：改名之后那条用例会立刻红，而不是等某次越权被发现。
 *
 * <p><b>新增登记的前置条件：</b>目标表的 {@code owner} 列必须能被无条件追加
 * （{@code alias.id IN (...)} 或 {@code alias.owner_id IN (...)}），且该语句的调用方
 * 需要的正是「当前用户可见的那些行」。两者缺一就不该登记——把条件加到一条「必须看全表」的
 * 语句上，等价于把那个功能改坏。
 */
final class OwnerScopedStatements {

    /**
     * 货主 Mapper 的语句标识前缀。
     *
     * <p>用字符串而不是引用 Mapper 类型：引用会引入 {@code auth → master} 的编译依赖，
     * 那正是本清单要避免的东西（见类注释）。
     */
    private static final String OWNER_MAPPER =
            "com.yunwms.master.infrastructure.persistence.mapper.OwnerMapper.";

    /**
     * 已登记的语句。
     *
     * <p>列表的计数与分页必须<b>同时</b>登记：只登记其中一个时 {@code total} 与 {@code records}
     * 会来自两个不同的范围，前端会看到「总共有 30 条，当前页 0 条」这类无法解释的页面。
     */
    private static final Map<String, ScopedTable> TABLES = Map.of(
            OWNER_MAPPER + "countByFilter", new ScopedTable("owner", "id"),
            OWNER_MAPPER + "selectPageByFilter", new ScopedTable("owner", "id"),
            // 详情与终止影响清单共用这一条：两者都必须按可见性给 404，否则可以借计数探测他方货主
            OWNER_MAPPER + "selectVisibleById", new ScopedTable("owner", "id"));

    private OwnerScopedStatements() {
    }

    /**
     * 查某条语句是否需要改写。
     *
     * @param mappedStatementId MyBatis 的语句标识；为 {@code null} 时视为未登记
     * @return 该语句要过滤的表与货主列；未登记时为 {@code null}
     */
    static ScopedTable find(String mappedStatementId) {
        return mappedStatementId == null ? null : TABLES.get(mappedStatementId);
    }

    /**
     * 一条语句要过滤的表，以及表示「这一行属于哪个货主」的列。
     *
     * <p>货主表自身用主键列，子表用 {@code owner_id}：把两者都收在同一个类型里，
     * 后续接入 SKU / 仓库表时只需在清单里加一行，不需要在 handler 里加分支。
     *
     * @param table       表名，与 SQL 里的表名比对（大小写不敏感）
     * @param ownerColumn 判定归属的列名
     */
    record ScopedTable(String table, String ownerColumn) {
    }
}

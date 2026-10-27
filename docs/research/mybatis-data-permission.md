# MyBatis-Plus 数据权限依赖调研

- 调研时间：2026-10-27
- 时效：本文记录当时版本；升级 MyBatis-Plus 前必须重新核对。
- 用途：`enforce-owner-data-scope` 的技术选型记录，不描述业务行为。

## 结论

采用 `mybatis-plus-jsqlparser:3.5.9` + `DataPermissionInterceptor`。

- 版本与项目现有 `mybatis-plus.version=3.5.9` 完全一致。
- 许可证：`mybatis-plus-jsqlparser` 为 Apache-2.0；其传递的 JSqlParser 5.0 为 LGPL-2.1/Apache-2.0 双许可，本项目按 Apache-2.0 使用。
- 选择理由：官方扩展、与现有 MyBatis-Plus 同版本、无需引入第二套 SQL 解析器。
- 替代方案：
  - 自定义 `StatementHandler`：可控但需要自行解析/改写 SQL，维护成本更高。
  - 在业务 Service 手写 owner 条件：会散落、易漏，且无法覆盖 count/records 一致性。
  - 其他数据权限插件：引入第二套生态，通常许可证与维护状态更差。

## 使用边界

- 只对显式 statement 清单生效；未登记语句不解析范围、不追加条件。
- 条件由 `LongValue` 输出 ownerId 字面量。值来自数据库且为数值，不经字符串拼接；但无法追加 JDBC 参数映射。
- 所有 SELECT 仍会被 JSqlParser 解析并重新渲染，即使未登记。当前规模可接受；若出现性能热点，再评估在拦截器入口短路。
- 升级 MyBatis-Plus 时必须同步升级本依赖，不能只升核心包。

## 验证

- `mvnw -B clean verify` 全绿。
- 集成测试确认列表 count/records、详情与终止影响按范围过滤。
- 未做生产数据量下的 SQL 解析性能压测。

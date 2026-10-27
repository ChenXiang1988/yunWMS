# Design

## Context

- auth `OwnerNameQuery` 的契约：按 ID 批量取名称，**不按 deleted_at 过滤**。
- master 现有 `findById`/`findPage` 都排除软删除，且返回完整聚合并做不变量校验；不能直接复用。
- auth 与 master 不能互相依赖；`wms-app` 是唯一同时可见双方的模块。

## Goals / Non-Goals

**Goals:**

- 删除 auth 内直接读 `owner` 表的 SQL。
- 用户列表对已软删除货主仍显示名称。
- 保持一次批量查询，不退化为 N 次。

**Non-Goals:**

- 不改货主列表/详情查询口径。
- 不删除 `owner` 外键。
- 不改变用户列表 API 字段。

## Decisions

### D1. master 暴露 Application 查询能力

- 选择：`wms-master/application/query/OwnerNameLookup` 接口 + `OwnerNameLookupService` 实现，方法 `Map<Long,String> findNamesByIds(Collection<Long>)`。
- 理由：能力提供方暴露 Application 接口，符合 `docs/coding-project.md:39`；app 只依赖接口。
- 备选：app 直接查 master 表。否决，只是移动越界。

### D2. 轻量投影，不重建聚合

- 选择：master 新增 `OwnerNameRow` 查询投影和 XML `selectNamesByIds`，返回 `id,name`，不加载完整 Owner。
- 理由：只显示名称，不需要状态/地址；避免软删除或不完整历史数据触发聚合校验失败。
- 备选：`findById` 循环。否决，N+1，且排除软删除。

### D3. app bridge 实现 auth 端口

- 选择：`wms-app` 新增 `OwnerNameQueryAdapter implements OwnerNameQuery`，委托 `OwnerNameLookup`。
- 理由：auth 与 master 互不引用；bridge 是唯一合法装配点。
- 备选：auth 定义 Application 接口由 master 实现。否决，会让 auth 依赖 master 类型或反向依赖。

### D4. 删除债务与例外

- 删除 auth 的 Mapper、XML、Row、Impl。
- 删除 `table-ownership.json` 中 `wms-auth -> owner` 例外；`TableOwnershipRegistry` 的死例外检查会强制清理。
- 删除不再使用的 auth mapper 文件后，重新跑全量测试。

## Risks / Trade-offs

- **空集合查询**：必须短路为空 Map，避免 SQL `IN ()`。
- **软删除语义**：SQL 明确不过滤 `deleted_at`，Javadoc 与测试固定该口径。
- **Bean 装配**：删除 auth 实现后，用户列表查询仍注入 auth 端口；bridge 必须被 Spring 扫描到。
- **表所有权门禁**：删除例外后若还有 auth SQL 会立即失败，这是预期保护。

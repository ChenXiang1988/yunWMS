# Design

## Context

- 表结构已具备：`sys_role.data_scope_type`、`sys_user.data_scope_type`、`sys_data_scope_owner`、`owner_assignee`。
- `platform-administration` 规格已定义三范围、两层配置、多角色并集、ALL 吞并、角色停用即时失效、空范围无权限、货主账号强制隔离、超管不受限。
- 当前无数据范围领域模型、配置接口、拦截器；货主列表/详情由 `OwnerQueryService` 直接查全量。
- `CurrentSession` 只暴露 userId，未读取会话中的 `subjectType` / `ownerId`。
- 全仓没有 `MybatisPlusInterceptor`；MyBatis-Plus 3.5.9 的 JSqlParser 已拆包。

## Goals / Non-Goals

**Goals:**

- 配置可写、可回显、可校验。
- 生效范围按请求实时计算，角色停用/移除/覆盖清除立即生效。
- 查询过滤统一走 MyBatis 拦截器，业务 Service 不手写 owner 条件。
- 货主账号强制隔离，跨货主详情 404。
- 无配置 fail-closed。

**Non-Goals:**

- 系统管理页数据范围配置 UI（4.3）。
- SKUID 按货主分桶（2.6b）。
- 菜单动态路由和按钮权限（4.4）。
- 为所有表一次性接入；先覆盖货主域。

## Decisions

### D1. 独立配置端点，不改资料更新语义

- `PUT /api/console/roles/{id}/data-scope`：`{dataScopeType, ownerIds}`，整批替换角色范围。
- `PUT /api/console/users/{id}/data-scope`：`{dataScopeType, ownerIds}`；`dataScopeType=null` 清除用户覆盖，回落角色默认。
- 角色创建请求可带可选 `dataScopeType`/`ownerIds`；未传时为 `CUSTOM` 空范围，保持保守默认。
- 角色详情和用户详情返回 `dataScopeType`/`ownerIds`。
- 不把字段塞进 `UpdateRoleRequest` / `UpdateUserRequest`：避免“资料编辑漏传字段就把数据范围清空”。既有资料更新契约不变。
- `ownerIds` 字符串 ID，复用 `RequestIds.optionalIds`。

备选：并入现有更新请求。否决，部分更新与全量替换语义会互相污染。

### D2. 配置不变量

- `ALL`/`ASSIGNED`：`ownerIds` 必须为空；非空返回 40000。
- `CUSTOM`：允许空数组；空集合不贡献任何货主，符合“空明细的角色不吞并其他角色”。
- CUSTOM 的 owner 必须存在、未删除且启用；不存在/停用返回 40000。
- OWNER 主体不允许配置用户级覆盖（强制隔离），尝试设置返回 40000。
- 角色 `SUPER_ADMIN` 的 ALL 是初始化数据；内置角色仍不可编辑，不能通过端点改成非 ALL。
- 明细替换与角色/用户行更新同一事务；`sys_data_scope_owner` 无 `deleted_at`，物理 `DELETE` 后批量 INSERT。

### D3. 生效范围模型

```text
DataScope =
  ALL
  NONE
  RESTRICTED(ownerIds)
```

- OWNER 主体：`RESTRICTED({session.ownerId})`，忽略角色和用户配置。
- STAFF 用户覆盖非空：只按用户覆盖计算，不回退角色。
- 否则取启用角色并集：
  - 任一 ALL → ALL；
  - ASSIGNED → 查询 `owner_assignee` 得到当前用户负责的 ownerId（主/副都算）；
  - CUSTOM → 读取该角色明细；
  - 空 CUSTOM 不贡献；
  - 无启用角色/并集为空 → NONE。
- ASSIGNED 每次请求实时查询，不写静态名单。
- 角色停用、角色移除、用户覆盖清除均在下一请求生效。

### D4. 请求级缓存

- `DataScopeContext` 使用 ThreadLocal 缓存，懒解析。
- Web 过滤器在请求结束时 `finally` 清理；没有登录上下文时解析为 NONE。
- 不跨请求缓存，保证即时失效。
- 同一请求的角色、CUSTOM 明细、ASSIGNED 负责人查询各做一次批量查询。

### D5. MyBatis 过滤

- 父 pom 管理并在需要改写的模块引入 `mybatis-plus-jsqlparser:3.5.9`。
- 注册 `MybatisPlusInterceptor` + `DataPermissionInterceptor`，handler 使用 `MultiDataPermissionHandler`。
- 通过 Mapper 方法上的 `@DataScope(table = "owner", ownerColumn = "id")` 标注，handler 根据 mappedStatementId 反射读取注解；未标注的方法不处理。
- 条件：
  - ALL：无追加。
  - NONE：`1 = 0`。
  - RESTRICTED：`alias.id IN (...)` / `alias.owner_id IN (...)`；空集合同样 `1 = 0`。
- 支持表别名；ID 为数值绑定，不拼接用户输入。
- 货主列表 `countByFilter` 与 `selectPageByFilter` 必须同时标注，保证 records/total 一致。
- 详情新增专用查询方法并标注；不复用编辑/状态流转的无过滤 `findById`。
- 终止影响查询也校验数据范围，避免通过计数接口探测他方货主。
- 后续 SKU/仓库表只登记表名与 owner 列。

### D6. 跨模块货主校验

- auth 定义 `OwnerExistenceQuery` 端口：批量返回存在、未删除、启用的 ownerId。
- master 暴露 Application 能力；`wms-app` bridge 适配 auth 端口。
- 不复用 `OwnerNameLookup`：它的口径刻意包含软删除货主，不能用于新配置校验。

### D7. 错误处理

- 非法类型、非 CUSTOM 带 ownerIds、owner 不存在/停用 → 40000。
- 角色/用户不存在 → 40400。
- 内置角色修改 → 40900。
- 无范围/未登录查询 → 空集，不回退全部。

## Risks / Trade-offs

- **每请求查询角色/负责人**：多几次小查询；换取角色停用即时生效。可用请求级缓存减少重复。
- **MyBatis-Plus JSqlParser 兼容性**：需固定 3.5.9；先写最小 SQL 集成测试验证。若不兼容，改自定义 `StatementHandler`。
- **大 CUSTOM 集合**：`IN` 列表可能变长；当前管理端规模可接受，后续可改临时表/子查询。
- **OWNER 主体**：角色配置可能看起来存在但不生效；接口拒绝配置，避免误导。
- **无请求上下文**：MCP/定时任务查询货主会得到 NONE；当前没有此类任务，后续需要显式系统上下文。

## Migration

无数据库迁移。仅新增 API、代码和测试。

# 交付记录：enforce-owner-data-scope

## 目标与实际交付

- 目标：完成 `implement-owner` 2.3/2.4/2.5，实现货主维度数据范围配置、生效值解析、MyBatis 过滤与货主账号强制隔离。
- 实际：三范围配置、用户覆盖、多角色并集、ASSIGNED 实时推导、ALL 吞并、空范围 fail-closed、货主列表/详情/终止影响过滤全部落地。

新增/修改要点：

- 领域：`DataScopeType`、`DataScope`、`DataScopeResolver`、`DataScopeContext`。
- 配置：角色/用户独立数据范围端点，CUSTOM 明细整批替换；用户 `null` 清除覆盖。
- 跨模块：master active-only 货主存在性与负责人查询；`wms-app` bridge；auth 不直读 owner/owner_assignee。
- 过滤：`mybatis-plus-jsqlparser:3.5.9` + `DataPermissionInterceptor`；显式 statement 清单；列表 count/page、详情、终止影响。
- 测试：431 个后端测试；新增 16 配置集成用例 + 11 过滤集成用例 + 单元测试。

## 关键决策

- **独立配置端点**：不改 `UpdateRoleRequest`/`UpdateUserRequest`，避免资料编辑漏传字段清空范围。
- **显式 statement 清单**：不用 `@DataScope` 注解，避免 master→auth 或 wms-web 白名单冲突。
- **OWNER 强制**：货主账号解析为 `RESTRICTED({ownerId})`，角色 ALL 也不生效。
- **ASSIGNED 实时**：每次请求按 `owner_assignee` 查询，不写静态名单。
- **空范围**：`NONE` → `1=0`；CUSTOM 空集合法且不贡献；不回落 ALL。
- **CUSTOM owner 口径**：必须存在、未删除、`ACTIVE`。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| `./mvnw -B clean verify` | BUILD SUCCESS |
| wms-auth | 216 tests |
| wms-master | 85 tests |
| wms-app | 130 tests |
| 合计 | 431 tests，0 失败 |
| 配置集成测试 | 角色 8 + 用户 8 |
| 过滤集成测试 | 员工 10 + 货主账号 1 |
| SQL 证据 | count/page/detail 均出现 `o.id IN (...)`；未登记 sku count 未改 |
| OpenAPI/inventory | 27 operations，源 commit `fe45410` |
| OpenSpec validate | change 合法 |

## 未验证项

- 未做前端系统管理页配置 UI（4.3）。
- 未做写路径的数据范围过滤：编辑/状态/删除仍按 id 定位。知道 id 的内部员工仍可能写他方货主。
- 未做生产数据量下的 SQL 解析性能压测。
- 未覆盖异步/定时任务上下文；当前无此类调用，默认 NONE。
- 未把 `owner` 的“启用”口径扩展到 DRAFT/POTENTIAL/SUSPENDED；当前仅 ACTIVE 可用于 CUSTOM 配置。

## 已知风险

- DataPermissionInterceptor 会解析所有 SELECT；未登记语句不追加条件，但仍有解析开销。
- ownerId 以数值字面量进入 SQL；来源为数据库、无字符串拼接，但无法追加 JDBC 参数映射。
- CUSTOM 的 IN 列表随货主数增长。
- 登记 statement 改名不会编译失败；集成测试会兜底。
- `sys_user.data_scope_type` 没有数据库 CHECK，非法值由写入路径和读取时枚举校验保证。

## 后续建议

- 另开 change 收口货主写路径的数据范围或明确“知道 id 即可写”的授权模型。
- 系统管理页 4.3 接入角色/用户数据范围配置。
- 若 SQL 解析成为热点，在拦截器入口对未登记语句短路。

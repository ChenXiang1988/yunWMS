# Tasks

## 1. 领域与持久化

- [ ] 1.1 新增 `DataScopeType`、`DataScope`、`DataScopeOwnerRegistrar`、`DataScopeQuery` 与 `OwnerExistenceQuery` 端口。
- [ ] 1.2 新增 `sys_data_scope_owner` 实体/Mapper/仓储，支持按角色/用户整批替换和读取；无 `deleted_at`。
- [ ] 1.3 新增 master active-only 批量货主存在性能力，新增 `wms-app` bridge；auth 不直读 owner。

## 2. 配置 API

- [ ] 2.1 角色创建支持可选 `dataScopeType/ownerIds`，新增 `PUT /api/console/roles/{id}/data-scope`，角色详情回显。
- [ ] 2.2 新增 `PUT /api/console/users/{id}/data-scope`，null 清除覆盖；用户详情回显 `ownerIds`。
- [ ] 2.3 校验：ALL/ASSIGNED 不接受 ownerIds；CUSTOM 空集合法；owner 必须存在且启用；OWNER 主体拒绝覆盖；写入同事务。

## 3. 生效范围

- [ ] 3.1 扩展 `CurrentSession` 读取 subjectType/ownerId；实现请求级 `DataScopeContext` 与清理过滤器。
- [ ] 3.2 实现 `DataScopeResolver`：OWNER 强制、用户覆盖优先、启用角色并集、ALL 吞并、ASSIGNED 实时、空集 NONE。
- [ ] 3.3 单元测试覆盖覆盖优先、继承、并集、ALL、混合、空 CUSTOM、停用、全停用、OWNER 强制。

## 4. 查询过滤

- [ ] 4.1 引入 `mybatis-plus-jsqlparser:3.5.9`，注册 `DataPermissionInterceptor` 与 `@DataScope` handler。
- [ ] 4.2 货主列表 count/page、详情专用查询、终止影响接入注解；records/total 同范围，他方详情 404。
- [ ] 4.3 货主账号强制按 session.ownerId 过滤，即使角色 ALL；未登录/无范围返回空集。

## 5. 验证与交付

- [ ] 5.1 新增真实 PostgreSQL 集成测试：ALL/CUSTOM/ASSIGNED/覆盖/并集/停用/空范围/OWNER 隔离/超管/分页一致。
- [ ] 5.2 生成 OpenAPI/inventory，运行 `./mvnw -B clean verify`。
- [ ] 5.3 运行只读交付审计，写 `completion.md`，勾选 `implement-owner` 2.3/2.4/2.5。

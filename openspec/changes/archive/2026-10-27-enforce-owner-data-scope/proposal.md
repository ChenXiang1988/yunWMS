# Proposal

## Why

`platform-administration` 规格已经定义货主维度的数据权限，但后端只落了表结构：没有配置入口、生效范围解析和查询过滤。货主管理页已接真实 API，当前所有登录用户都能看到全部货主，角色范围配置也不会生效。需要完成 `implement-owner` 的 2.3/2.4/2.5。

## What Changes

- 角色与用户增加数据范围配置：`ALL / ASSIGNED / CUSTOM`，用户可覆盖角色默认。
- 新增生效范围解析：启用角色并集、ALL 吞并、ASSIGNED 实时按负责人推导、空范围 fail-closed。
- 新增货主存在性校验端口，经 `wms-app` bridge 调用 master，auth 不直读 `owner`。
- 货主列表/详情/终止影响接入 MyBatis 数据权限拦截器；货主账号强制只按所属货主过滤。
- 新增真实 PostgreSQL 集成测试。
- 勾选 `implement-owner` 2.3/2.4/2.5。

**BREAKING**：角色/用户新增独立数据范围端点；既有资料更新接口语义不变。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。实现既有 `platform-administration` 规格。

## Impact

- **代码**：wms-auth 领域/应用/接口/持久化，wms-master 查询能力，wms-app bridge/MyBatis 配置，集成测试
- **依赖**：新增 `mybatis-plus-jsqlparser` 3.5.9（Apache-2.0）
- **数据库**：无迁移；使用现有 `sys_role`、`sys_user`、`sys_data_scope_owner`、`owner_assignee`
- **API**：新增角色/用户数据范围配置端点，详情补字段；OpenAPI 重新生成
- **前端**：不改；系统管理页属于 4.3

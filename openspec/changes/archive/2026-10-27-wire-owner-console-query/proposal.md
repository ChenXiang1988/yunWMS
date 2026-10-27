# Proposal

## Why

仓网端客户页仍是三行静态 mock：四态小写、联系人/手机号伪字段、无请求、无分页。后端已提供真实货主列表和字典接口，需先接入列表、筛选与服务端分页，并建立最小前端测试基础。

## What Changes

- 新增货主 API 与纯展示模型。
- 客户页改为真实列表：关键词、客户类型、五态、服务端分页、加载/空态。
- 客户类型从 `OWNER_TYPE` 字典读取。
- `StatusTag` 扩展支持后端五态，保留现有小写状态。
- 增加纯函数 node:test，不引入新依赖。
- 删除 mock 联系人和手机号列。

**BREAKING**：无。仅替换静态页面数据源。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。

## Impact

- **新增**：`apps/network-console/src/api/owner.ts`、`dict.ts`、`views/customer/owner-model.ts`、筛选组件、测试
- **修改**：`views/customer/index.vue`、`packages/ui-web/src/components/StatusTag.vue`、应用 package.json test 脚本
- **不变**：后端、数据库、路由、布局

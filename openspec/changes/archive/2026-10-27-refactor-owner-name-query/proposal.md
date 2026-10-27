# Proposal

## Why

`wms-auth` 的 `OwnerNameMapper` 直接读取 `wms-master` 的 `owner` 表，是已登记临时债务。架构审计确认删除条件当时未满足：master 没有“按 ID 批量、id+name 投影、包含软删除”的查询。现在需要先补 master 能力，再经 `wms-app` bridge 替换并删除 auth 侧 SQL。

## What Changes

- master 新增 `OwnerNameLookup` Application 能力和实现：按 ID 批量返回 id+name，不排除 `deleted_at`。
- `wms-app` 新增 bridge，实现 auth 的 `OwnerNameQuery`，委托 master 能力。
- 删除 auth 的 `OwnerNameMapper`、XML、Row、实现类。
- 删除表所有权清单里的 `wms-auth -> owner` 例外。
- 增加测试：已软删除货主名称仍能在用户列表显示；auth 不再有 owner SQL。

**BREAKING**：无。用户列表行为不变。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。用户列表字段和软删除显示口径不变。

## Impact

- **新增**：master 批量名称查询、app bridge、测试
- **删除**：auth 直读 owner 的 Mapper/XML/Row/实现
- **修改**：表所有权例外、可能的用户查询装配
- **不变**：数据库、API、前端

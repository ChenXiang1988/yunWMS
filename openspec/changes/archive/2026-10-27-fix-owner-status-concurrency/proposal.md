# Proposal

## Why

`audit-architecture-boundaries` 的真实并发验证已复现状态覆盖：

- ACTIVE 同时终止与暂停：首轮 6/20、重跑 12/20 最终为 SUSPENDED；
- SUSPENDED 同时终止与恢复：9/20 最终为 ACTIVE；
- 重复终止 20/20 正常。

根因是 `OwnerStatusChangeService` 先读状态、再在无事务、无锁、无版本条件的 UPDATE 中写状态。终止返回成功后仍可能被后续暂停/恢复覆盖，违反“终止为终态”。

## What Changes

- 货主状态流转用例加事务；
- 读取当前货主时使用 `SELECT ... FOR UPDATE` 锁住目标行；
- 在锁内完成状态判定、终止确认与更新；
- 增加真实 PostgreSQL + 双线程并发回归测试，覆盖终止 vs 暂停、终止 vs 恢复、重复终止；
- 不新增数据库列，不修改 API 请求/响应。

**BREAKING**：无。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。现有“状态流转合法性”和“终止为终态”不变，本变更只是让并发下也成立。

## Impact

- **修改**：`OwnerStatusChangeService`、`OwnerRepository`、`OwnerRepositoryImpl`、`OwnerMapper`、`OwnerMapper.xml`
- **新增**：并发集成测试
- **不变**：数据库迁移、API 契约、前端、其他用例
- **验证依据**：`openspec/changes/archive/2026-10-27-audit-architecture-boundaries/completion.md`

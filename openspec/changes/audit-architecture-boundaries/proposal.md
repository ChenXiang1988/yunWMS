# Proposal

## Why

首轮架构评审发现，仓库的模块方向在 Java/POM 层成立，但架构证据分散在 Mapper、DDL、测试和文档中：跨模块表访问没有机器门禁，`docs/coding-project.md` 的 ArchUnit 承诺大于实际覆盖，货主状态流转的并发风险尚未验证，API ID 类型与前端页面口径不一致，README 和工具说明也已漂移。

本变更先固化可追溯的架构事实和决策，再修正已确认的文档与工具问题。它不改变业务行为，为并发的独立修复、契约统一、ArchUnit 加固和跨模块债务清偿提供验收基线。

调研与证据基线见 `docs/research/architecture-boundary-snapshot-2026-10-27.md`（本变更生成）。

## What Changes

- 建立模块、表、外键、Mapper 的一体化访问清单，明确 `OwnerNameMapper` 与两个 `owner_id` 外键的处置。
- 建立 `docs/coding-project.md` MUST 约束与自动门禁的对照矩阵。
- 形成货主状态流转并发验证方案，覆盖终止 vs 暂停、终止 vs 恢复、重复终止；实际运行由调用方执行。
- 核对 OpenAPI、后端请求类型和前端类型，确认 ID 传输、货主五态和客户列表字段的最终口径。
- 修正 `code/backend/tools/verify_constraints.py` 的非法 SKU 前缀夹具。
- 修正根 README、前后端 README、API 台账说明中的过期状态。
- 为 `migrate-network-console` 补充完成记录与归档前置条件；在运行验证闭环前不归档。
- 在 `completion.md` 记录调用方待执行命令、未验证项和后续 change 建议。

**BREAKING**：无。没有业务代码、数据库迁移、API 契约或测试断言变更。

### 本期不做

- 不修复货主状态流转并发问题；确认复现后另开 `fix-owner-status-concurrency`。
- 不把请求体 ID 从 `Long` 改为字符串；另开 `align-api-id-contract`。
- 不新增 ArchUnit 规则、Mapper 跨表检查或前端 lint；另开 `harden-architecture-guards`。
- 不迁移 `OwnerNameMapper`；另开 `refactor-owner-name-query`。
- 不删除两个 `owner_id` 外键。
- 不修改 `docs/api/openapi.json`、`docs/api/inventory.md` 或任何生成物。
- 不运行 Maven、pnpm、Python 测试、数据库或应用。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。

本变更只记录架构事实、决策与文档/工具修正，不改变系统规格行为，因此在 `.openspec.yaml` 设置 `skip_specs: true`。

## Impact

- **新增**：`openspec/changes/audit-architecture-boundaries/`、`docs/research/architecture-boundary-snapshot-2026-10-27.md`
- **修改**：`code/backend/tools/verify_constraints.py`、`README.md`、`code/backend/README.md`、`code/frontend/README.md`、`docs/api/README.md`
- **OpenSpec 状态**：新增 `openspec/changes/migrate-network-console/completion.md`；该 change 的历史 `proposal.md` 不改
- **不变**：`code/backend/**/*.java`、`code/backend/**/src/main/resources/db/migration/**`、`code/frontend/**`、测试断言、API 生成物

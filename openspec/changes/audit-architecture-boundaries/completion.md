# 交付记录：audit-architecture-boundaries

## 目标与实际交付

- 基线：`main@865d2ae4be3fa5e4ec47d07994ab0e252eb3c264`
- 分支：`change/audit-architecture-boundaries`
- 目标：完成架构边界盘点、MUST 门禁审计、并发与契约验证方案，并修正已确认的文档/工具漂移。
- 实际：完成上述审计与文档/工具修正；未修改 Java、SQL、Vue/TypeScript、业务测试或 API 生成物。

交付物：

- `openspec/changes/audit-architecture-boundaries/proposal.md`
- `openspec/changes/audit-architecture-boundaries/design.md`
- `openspec/changes/audit-architecture-boundaries/audit.md`
- `openspec/changes/audit-architecture-boundaries/tasks.md`
- `docs/research/architecture-boundary-snapshot-2026-10-27.md`
- `openspec/changes/migrate-network-console/completion.md`

修改文件：

- `code/backend/tools/verify_constraints.py`
- `README.md`
- `code/backend/README.md`
- `code/frontend/README.md`
- `docs/api/README.md`

## 关键设计

- D1：完整证据放快照，结论放 audit，决策放 design。
- D2：`OwnerNameMapper` 保留为待替换债务；master 需先提供“批量 id+name、包含软删除”的能力。
- D3：`sys_user.owner_id`、`sys_data_scope_owner.owner_id`、`owner_assignee.user_id` 三个跨模块外键保留。
- D4：不在本轮新增 ArchUnit 规则；门禁缺口另开 change。
- D5：并发使用人工接口验证，不提交当前会红或不稳定的测试。
- D6：JSON 请求/响应 ID 最终为字符串；路径参数可保留 int64。当前请求体 5 个字段待修。
- D7：只修正文档与工具，不回改历史 proposal，不手改生成物。
- D8：README 不再作为架构事实来源。

## 验证结果

本轮已完成的静态验证：

- `openspec validate audit-architecture-boundaries`：通过，`skip_specs` 生效。
- 快照覆盖 9 个 Mapper XML、24 张表、29 条外键，与迁移/资源统计一致。
- `verify_constraints.py`：SETUP 5 条、CASES 63 条，与修改前数量一致；非法 SKU 前缀检查为 0，旧 `AMZ1/UNL1` 为 0。
- API 说明与 `inventory.md` 的 25 个 operation、源 commit `2d7ccc9` 一致。
- 后端 README 的迁移数量为 36。
- 前端 README 不再包含“本地模拟”“尚未接后端”。
- `migrate-network-console` 仍为 complete、未归档，且已有 `completion.md`。
- 改动路径仅限 README、Python 校验工具、审计文档与 OpenSpec 记录；未触碰 Java、SQL、Vue/TypeScript、测试断言或 API 生成物。

## 验证结果（调用方授权执行）

| 项 | 命令 | 预期 | 实际 |
|---|---|---|---|
| 后端 | `cd code/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./mvnw -B verify` | 编译、单测、ArchUnit、Testcontainers 全部通过 | 通过；331 个测试（auth 160 / master 83 / app 88），0 失败，0 错误 |
| 前端 | `cd code/frontend && pnpm -r typecheck && pnpm -r test && pnpm -r build` | 类型检查、现有测试、构建通过 | 通过；3 个令牌测试通过，两个应用构建成功 |
| api-inventory | `cd code/tools/api-inventory && python3 -m unittest discover -s tests` | 全部通过 | 通过；45 个测试 |
| cad-parser | `cd code/tools/cad-parser && python3 -m unittest discover -s tests` | 全部通过 | 通过；13 个测试 |
| DDL 约束 | 全量迁移后 `python3 code/backend/tools/verify_constraints.py` | 63/63 通过 | 通过；63/63 |

注意：本机默认 Java 为 26，首次后端测试因 Mockito inline mock 与 JDK 26 不兼容失败；改用 Homebrew JDK 21.0.11 后通过。项目声明基线为 Java 21，该失败属于环境版本问题，不是代码回归。

DDL 首次运行 60/63，暴露脚本原有的固定用户 ID 与 `OWNER` 种子规则冲突；已改为按登录名取用户、使用 `TEST_RULE`，重跑 63/63。

## 并发验证结果

在隔离 PostgreSQL 16 容器和临时后端实例上执行，每个场景 20 轮。

| 场景 | 两个响应 | 最终状态 | updated_at / updated_by | 结论 |
|---|---|---|---|---|
| ACTIVE：终止 vs 暂停 | 终止成功 20/20，部分轮次两个请求均返回成功 | 首轮 6/20、重跑 12/20 最终为 SUSPENDED | 未记录（接口不返回 updated_by，临时实例已清理） | **复现**：终止成功后可被暂停覆盖 |
| SUSPENDED：终止 vs 恢复 | 终止成功 20/20，部分轮次两个请求均返回成功 | 9/20 最终为 ACTIVE | 未记录（同上） | **复现**：终止成功后可被恢复覆盖 |
| ACTIVE：重复终止 | 两个请求均成功 | 20/20 最终为 TERMINATED | 未记录（同上） | 通过：重复终止保持幂等 |

判定基线：终止返回成功后最终状态不得被暂停或恢复覆盖；重复终止应两次成功且最终为 TERMINATED。`updated_at` 已通过详情接口观察但未逐轮留档；`updated_by` 未查询数据库。

## 未验证项

- 未做浏览器视觉走查，因此 `migrate-network-console` 不归档。
- 未重新生成 OpenAPI 与 inventory。
- 未检查完整 Git 历史，无法确认历史迁移是否曾被修改。

## 已知风险

- 货主状态流转并发覆盖已实机复现：ACTIVE 终止 vs 暂停出现 6/20、12/20 次覆盖；SUSPENDED 终止 vs 恢复出现 9/20 次覆盖；重复终止通过。需要独立 `fix-owner-status-concurrency`。
- `OwnerNameMapper` 仍是 auth 读取 master 表的债务；直接替换会造成软删除货主名称显示回归。
- 三个跨模块外键保证引用存在，但不保证货主未软删除，业务可用性仍需应用层判断。
- 请求体 ID 类型仍与契约规范冲突，属于后续 `align-api-id-contract`。
- 架构门禁只覆盖模块方向，其他 MUST 仍靠人工。
- `AMZ1` 仍在接口示例和 `product-center` 规格中出现，已登记为后续文档/契约清扫项。
- 并发验证记录了响应和最终状态，但没有逐轮保留 `updated_at`/`updated_by`；后续自动化回归测试应补。`

## 后续 change

- `refactor-owner-name-query`
- `fix-owner-status-concurrency`
- `align-api-id-contract`
- `harden-architecture-guards`
- `wire-owner-console-contract`

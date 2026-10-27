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

## 调用方待执行验证

以下均未在本轮执行：

| 项 | 命令 | 预期 | 实际 |
|---|---|---|---|
| 后端 | `cd code/backend && ./mvnw -B verify` | 编译、单测、ArchUnit、Testcontainers 全部通过 | 待回填 |
| 前端 | `cd code/frontend && pnpm -r typecheck && pnpm -r test && pnpm -r build` | 类型检查、现有测试、构建通过 | 待回填 |
| api-inventory | `cd code/tools/api-inventory && python3 -m unittest discover -s tests` | 全部通过 | 待回填 |
| cad-parser | `cd code/tools/cad-parser && python3 -m unittest discover -s tests` | 全部通过 | 待回填 |
| DDL 约束 | 全量迁移后 `python3 code/backend/tools/verify_constraints.py` | 63/63 通过 | 待回填 |

## 并发验证结果

执行方法见 `audit.md` 第 4 节；实际结果需由调用方回填。

| 场景 | 两个响应 | 最终状态 | updated_at / updated_by | 结论 |
|---|---|---|---|---|
| ACTIVE：终止 vs 暂停 | 待回填 | 待回填 | 待回填 | 待执行 |
| SUSPENDED：终止 vs 恢复 | 待回填 | 待回填 | 待回填 | 待执行 |
| ACTIVE：重复终止 | 待回填 | 待回填 | 待回填 | 待执行 |

判定基线：终止返回成功后最终状态不得被暂停或恢复覆盖；重复终止应两次成功且最终为 TERMINATED。

## 未验证项

- 未运行后端、前端、Python 测试或构建。
- 未启动应用做真实 HTTP 调用和并发验证。
- 未执行数据库迁移或 DDL 约束脚本。
- 未重新生成 OpenAPI 与 inventory。
- 未做浏览器视觉走查，因此 `migrate-network-console` 不归档。
- 未检查完整 Git 历史，无法确认历史迁移是否曾被修改。

## 已知风险

- 静态代码显示货主状态流转存在读后写窗口，终止可能被并发暂停/恢复覆盖；需调用方并发验证确认。
- `OwnerNameMapper` 仍是 auth 读取 master 表的债务；直接替换会造成软删除货主名称显示回归。
- 三个跨模块外键保证引用存在，但不保证货主未软删除，业务可用性仍需应用层判断。
- 请求体 ID 类型仍与契约规范冲突，属于后续 `align-api-id-contract`。
- 架构门禁只覆盖模块方向，其他 MUST 仍靠人工。
- `verify_constraints.py` 仅做静态夹具修正，实际数据库执行结果尚未确认。

## 后续 change

- `refactor-owner-name-query`
- `fix-owner-status-concurrency`
- `align-api-id-contract`
- `harden-architecture-guards`
- `wire-owner-console-contract`

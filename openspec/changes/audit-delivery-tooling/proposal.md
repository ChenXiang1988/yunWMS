# Proposal

## Why

上一轮交付审计暴露了两个问题：审计子 Agent 仍要手工统计文件数、提交行数和资源数量，既慢又容易把旧稿数字写进报告；同时它曾在仓库外创建临时文件，违反只读边界。审计需要稳定的机器证据，而不是更长的自然语言提示。

## What Changes

- 新增只读审计脚本 `code/tools/audit-delivery/audit_delivery.py`，输出 JSON：
  - anchor、HEAD、changed files；
  - 每笔提交增删行数与是否超 400 行；
  - 路径白名单与禁止扩展名；
  - 生成物是否被改动；
  - OpenSpec validate/list 状态；
  - Mapper、迁移、表、OpenAPI operation 等关键计数。
- 新增脚本单元测试，使用 Python 标准库，不引入依赖。
- 更新 `docs/agents.md` 的审计输入与输出模板，明确两段式审计、阻塞/建议/无法判定分级、禁止临时文件。
- 更新 `.codebuddy/agents/delivery-auditor.md`，要求优先调用脚本、复核脚本结果而不是手工计数。

**BREAKING**：无。工具只读，不改变业务代码、数据库、API 或前端。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。

本变更只增加审计工具与流程文档，不改变系统行为，因此 `.openspec.yaml` 设置 `skip_specs: true`。

## Impact

- **新增**：`code/tools/audit-delivery/audit_delivery.py`、`code/tools/audit-delivery/tests/`
- **修改**：`docs/agents.md`、`.codebuddy/agents/delivery-auditor.md`
- **不变**：Java、SQL 迁移、Vue/TypeScript、API 生成物、测试断言

# Design

## Context

见 `proposal.md`。本轮只做审计证据工具，不改变被测代码。

## Goals / Non-Goals

**Goals:**

- 用一条只读命令生成审计所需的稳定事实。
- 输出可直接交给 `delivery-auditor` 的 JSON，并支持人和 CI 读取。
- 把“两段式审计”和“禁止临时文件”写入流程文档。

**Non-Goals:**

- 不自动修改审计结果或替主 Agent 下结论。
- 不运行 Maven、pnpm、Python 业务测试或数据库。
- 不在本 change 接入 CI；CI 接入属于 `harden-architecture-guards`。

## Decisions

### D1. 使用 Python 标准库，单一脚本

- 选择：`argparse`、`subprocess`、`json`、`pathlib`、`re`，不新增依赖。
- 理由：仓库已有 Python 工具和测试；避免 shell 兼容性与额外依赖。
- 备选：Bash。否决，JSON 与结构化统计难维护；Node。否决，重复已有生态。

### D2. 输入 anchor 与规则，不自作判断

- 选择：必须显式传 `--anchor`；`--allow` 可重复，定义允许路径前缀/通配；`--forbid-ext` 定义禁止扩展名；`--generated` 定义生成物路径。
- 理由：审计范围和豁免必须由任务包给出，脚本不能自行推断。
- 备选：读取 change 的 proposal 自动解析白名单。否决，文本解析不可靠。

### D3. 输出 JSON，不写文件

- 选择：只向 stdout 输出 JSON；不创建缓存、不写临时文件。
- 理由：审计子 Agent 的硬边界是只读；输出由主 Agent 决定是否落盘。
- 备选：直接写 `audit.json`。否决，会让持有脚本的人获得写权限。

### D4. 统计以源码/命令为准

- 选择：
  - 提交行数来自 `git log --numstat`；
  - Mapper XML 数量与语句数来自文件扫描；
  - 迁移数量来自 `db/migration/V*.sql`；
  - OpenAPI operation 数来自 `docs/api/openapi.json` 的 path × method；
  - OpenSpec 状态来自只读 CLI `openspec list --json` 与 `openspec validate --change`。
- 理由：上一轮出现 26/25 的人工计数误差；数字必须能复算。
- 备选：从 README 读取数字。否决，README 正是漂移来源。

### D5. 两段式审计

- 提交前：`--anchor main --staged`，检查 staged diff 是否符合预期。
- 提交后：`--anchor main`，检查整条分支和每笔提交。
- 理由：先审 staged 能在提交前修正；后审覆盖提交拆分。
- 备选：只审最终 diff。否决，发现不了单笔超限。

### D6. 输出固定分级

- 脚本只输出 `blocking` 与 `unknowns`，不替 auditor 判断建议项。
- `blocking`：路径越界、禁止扩展名、生成物改动、单笔超 400、OpenSpec 校验失败。
- `unknowns`：命令不可用、读取失败或无法验证的事实。
- auditor 在报告里补充 `advisory`（建议项），主 Agent 负责裁决。
- 理由：事实与判断分离，避免把脚本算出的数字和人的建议混成一个“不通过”。

### D7. 工具链变更不触发开源调研

本变更只增加仓库内审计脚本与文档，不引入第三方依赖或新功能域，因此不单独产出 `docs/research/` 调研；依据是 `AGENTS.md` 的调研门禁面向新功能域与技术选型。

## Risks / Trade-offs

- **脚本自身成为事实错误来源** → 单测覆盖路径、行数、计数与 JSON 结构；脚本只在报告中给事实，不给结论。
- **不同操作系统 `git` 输出差异** → 固定 `--numstat -z` 解析，禁止依赖列排版。
- **OpenSpec CLI 不可用** → 输出 `unknown`，不阻断其他检查。
- **脚本被误用为写工具** → 明确只读，所有文件访问为读；测试只使用临时目录，生产运行不写文件。

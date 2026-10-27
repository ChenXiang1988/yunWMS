# 交付记录：audit-delivery-tooling

## 目标与实际交付

- 目标：把交付审计所需的文件、提交、路径、生成物、OpenSpec 和关键计数变成只读机器证据。
- 实际：新增 Python 标准库脚本与单元测试；更新审计规范与 delivery-auditor prompt。

新增：

- `code/tools/audit-delivery/audit_delivery.py`
- `code/tools/audit-delivery/tests/test_audit_delivery.py`

修改：

- `docs/agents.md`
- `.codebuddy/agents/delivery-auditor.md`

## 关键设计

- 只向 stdout 输出 JSON，不写文件。
- `--anchor`、`--allow`、`--forbid-ext`、`--generated` 由调用方显式给出，脚本不推断白名单。
- 支持 `--staged` 提交前审计与 `anchor..HEAD` 提交后审计。
- `blocking`、`unknowns` 输出，主 Agent 负责最终裁决。
- 生成物规则为空表示“没有生成物”，允许路径规则为空表示“全部允许”，二者语义不同。

## 验证结果

| 检查 | 命令 | 实际 |
| --- | --- | --- |
| 单元测试 | `cd code/tools/audit-delivery && python3 -m unittest discover -s tests` | 10 tests，OK |
| staged 审计 | `python3 code/tools/audit-delivery/audit_delivery.py --anchor main --staged ...` | 8 个文件在白名单内，blocking 空 |
| 计数 | 同上 | mapperXml 9、mapperStatements 25、migrations 36、tables 24、openapiOperations 25 |
| OpenSpec | 同上 `--change audit-delivery-tooling` | validate 通过，unknown 空 |

## 未验证项

- 未在 CI 中执行；接入 CI 留在 `harden-architecture-guards`。
- 未覆盖提交后审计的完整分支运行；本轮使用 staged 模式验证。调用方可在提交后运行 `--anchor main`。
- 不读取 unstaged/untracked 文件；使用前必须先 `git add` 或完成提交。
- 不运行构建、测试、数据库或浏览器验证。

## 已知风险

- 路径 glob 使用 `fnmatch` 语义；跨多层目录应写 `dir/**`。
- 脚本只提供事实，不判断业务正确性；审计结论仍由 auditor 与主 Agent 产出。
- `openspec` CLI 不可用时输出 unknown，不会伪造通过。

# 审计记录

- change: `refactor-owner-name-query`
- anchor: `5a5166570a04e514385a0a59e383e13deeca6061`（`git merge-base main HEAD`）
- 审计时间：2026-10-27
- 审计方式：`delivery-auditor` 只读 + `audit_delivery.py` 机器证据

## 逐项结论

| 检查项 | 结论 | 证据 |
| --- | --- | --- |
| 路径白名单 | 通过 | 23 个文件均在 master、auth、app bridge、测试清单与 change 工件内 |
| 单次提交 ≤400 行 | 通过 | 293、274、163 行 |
| 迁移与代码分离 | 通过 | 无 SQL、无迁移 |
| 违规依赖 | 通过 | 无依赖清单改动 |
| 前端/生成物 | 通过 | 无前端、无生成物 |
| 测试断言 | 通过 | 既有断言无删除；新增软删除名称用例 |
| auth 直读表 | 通过 | Mapper/XML/Row/Impl 已删除，`grep FROM owner` 无结果 |
| 表所有权例外 | 通过 | `exceptions` 为空，Mapper 门禁通过 |
| 机器证据 | 通过 | `blocking=[]`、`unknowns=[]` |

## 存量违约

| 提交 | 违规 | 处置 |
| --- | --- | --- |
| 本分支 | 无 | 不适用 |

## 结论

**通过。** clean verify 355 tests 的报告数字与 surefire 文件一致；审计未复跑构建。工作区另有一个与本次无关的未跟踪文件，未纳入提交。

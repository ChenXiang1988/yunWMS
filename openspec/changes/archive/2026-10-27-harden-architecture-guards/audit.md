# 审计记录

- change: `harden-architecture-guards`
- anchor: `e5d0c5ecee7545a9b47e479ff20106a0a484cbab`（`git merge-base main HEAD`）
- 审计时间：2026-10-27
- 审计方式：`delivery-auditor` 只读 + `audit_delivery.py` 机器证据

## 逐项结论

| 检查项 | 结论 | 证据 |
| --- | --- | --- |
| 路径白名单 | 通过 | 15 个文件在测试、前端脚本、CI 与 change 工件内；无业务代码、无迁移 |
| 单次提交 ≤400 行 | 通过 | 316、343、307、111、289、26、173 |
| 迁移与代码分离 | 通过 | 无 SQL / 迁移 |
| 违规依赖 | 通过 | 无依赖清单变化 |
| 测试断言 | 通过 | 令牌测试改为引用 primitives 真源，目标不变；其余为新增测试 |
| 前端色值/UI 库 | 通过 | `pnpm check:ui` 通过 |
| ArchUnit/Mapper 门禁 | 通过 | 352 tests；5 条 ArchUnit；24 表清单；反向验证能让门禁变红 |
| 机器证据 | 通过 | `blocking=[]`、`unknowns=[]` |

## 存量违约

| 提交 | 违规 | 处置 |
| --- | --- | --- |
| 本分支 | 无 | 不适用 |

## 结论

**通过。** GitHub Actions 未实跑；CI 命令路径与 working-directory 已静态核对。反向验证由实现者执行并报告，审计未复跑构建。

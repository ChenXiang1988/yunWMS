# Tasks

## 1. 审计工件

- [x] 1.1 校验 `proposal.md`、`design.md`、`audit.md`、`tasks.md` 与快照存在且链接互相可达，验证：`openspec validate audit-architecture-boundaries` 通过，`rg` 能命中所有引用路径。
- [x] 1.2 复核快照至少覆盖 24 张表、29 条外键、9 个 Mapper XML，验证：表格行数与迁移/资源统计一致。

## 2. 校验工具

- [x] 2.1 将 `verify_constraints.py` 中违反 `V035` 的 `AMZ1/UNL1` 及关联 SKUID、期望值替换为合法且保持原测试意图的夹具，验证：脚本源码不再包含被排除字符 `0/O/1/I` 的 SKU 前缀，且不存在 `AMZ1/UNL1`。
- [x] 2.2 核对修改后的脚本所有合法/非法用例数量与目标未减少，验证：逐条对照原文件，仅字面量变化，不删除断言。

## 3. 文档状态

- [x] 3.1 更新 `docs/api/README.md` 的接口数、源 commit 说明与“未重新生成”状态，验证：与 `inventory.md` 的 25 个 operation 和 `2d7ccc9` 一致。
- [x] 3.2 更新根 README、后端 README、前端 README 的已实现/未完成状态，验证：每条声明都能在 Controller、迁移数量或前端源码找到反证消除。
- [x] 3.3 为 `migrate-network-console` 补 `completion.md`，记录实现完成、调用方待验证与暂不归档条件，验证：不移动 change 目录，`openspec list --json` 仍显示 complete 而非已归档。

## 4. 交付记录

- [x] 4.1 在 `completion.md` 写入实际改动、调用方待执行命令、三组并发验证结果占位和未验证项，验证：文件覆盖 proposal 要求的全部交付字段。
- [ ] 4.2 运行只读交付审计，核对路径白名单、是否误改 Java/SQL/Vue/测试断言、单次提交行数，验证：审计结论无“不通过”项或已修正。

## 5. 调用方验证（不自动执行）

- 后端执行 `cd code/backend && ./mvnw -B verify`。
- 前端执行 `cd code/frontend && pnpm -r typecheck && pnpm -r test && pnpm -r build`。
- 在两个 Python 工具目录分别执行 `python3 -m unittest discover -s tests`。
- 全量迁移后执行 `python3 code/backend/tools/verify_constraints.py`。
- 按 `audit.md` 第 4 节执行三组并发验证并回填结果。

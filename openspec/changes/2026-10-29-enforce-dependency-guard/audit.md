# 交付审计

审计时间：2026-10-29。审计范围：分支 `change/2026-10-29-enforce-dependency-guard` 的 7 笔提交
（`214b3d6`..`0ad07d0`，基线 `main` 的 `0ec27d1`）。审计方式：只读，未改任何文件。

## 1. 结论

代码与提交全部合规，**无违规项**。审计中发现 4 处需裁决的事项已裁决（第 3 节），
2 处文档偏差已补记（第 4 节）。

| 检查项 | 结论 |
| --- | --- |
| 路径越界 | 合规：12 个文件全部落在声明范围内，无业务代码、无其他 pom、无 `apps/**` 与 `packages/**` 的 package.json、无数据库迁移 |
| 单次提交 ≤400 行 | 合规：343 / 97 / 11 / 181 / 381 / 2 / 8 |
| 数据库迁移与代码分离 | 不适用：本轮无任何 `.sql` |
| GPL / AGPL / SSPL | 合规：新增 `maven-enforcer-plugin` 3.6.3 为 Apache-2.0 构建期插件，不进依赖树与产物；Python 工具零第三方依赖 |
| 前端 UI 门禁 | 不适用：前端唯一改动是 `scripts.build` 一行；`element-plus` 在 `code/**` 下 0 命中 |
| 修改测试断言 | 合规：无既有测试被改，`tests/test_check.py` 为新增 |
| 一次提交一件事 | 合规 |

## 2. 独立复核的一处判断

**「清单先于脚本提交」是否造成不可运行中间态** —— 判断成立，且该顺序是唯一安全的方向：

- `781bbd9`（只有清单）时刻，全仓无 `dependency-guard/allowlist.json` 的消费者
  （`git grep allowlist` 命中的是 `audit-delivery` 的变量名与 `docs/agents.md` 的字符串，均非消费者），
  纯数据文件不参与任何构建路径；
- 反向排列会**真**出问题：`check_dependencies.py` 无条件读取同目录清单，缺失即崩溃退出非 0；
- 该拆分也是行数所迫：181 + 381 = 562 > 400，合成一笔即违规。

## 3. 裁决记录

| # | 事项 | 裁决 |
| --- | --- | --- |
| 1 | JSqlParser 为 LGPL / Apache-2.0 双许可，是否落入 AGENTS.md 禁止范围 | **可接受**。AGENTS.md 明文禁止的是 GPL / AGPL / SSPL，LGPL 不在其列；按 Apache-2.0 路径使用，且全仓 SQL 改写只走这一个解析器。已在 design 补记 |
| 2 | 构建插件 `maven-enforcer-plugin` 自身未进 `allowlist.json` | **不纳入本 change**。脚本只解析依赖段，不解析 `<build><plugins>`；写进清单会造成「清单有、代码未用」的假警告。留作后续 change |
| 3 | `design.md` 的 `allowlist.yaml` 残留与扫描范围窄于实现 | **补记不改原文**（design 只增不改）。已追加「实现后的补充」一节，见 design 末尾 |
| 4 | `tasks.md` 提交拆分表写 6 笔、3.3 未勾 | **已修正**为实际 7 笔并补勾 |

## 4. 未独立复验的部分（如实列出）

- **后端构建与测试、前端 build / typecheck / check:ui 本次审计未执行**（构建属审计明令禁止项）。
  tasks.md 中「enforcer Rule 0-2 全 passed / BUILD SUCCESS / 自检 9 例 OK / check:ui 通过」
  采自主 Agent 自述，审计未复验。
- **OpenSpec CLI 不可用**：不在 PATH，带 `--change` 的校验未执行，改为 git 侧取证。
- **审计实际执行过的只读验证**：`python3 code/tools/dependency-guard/check_dependencies.py`
  → `✔ 依赖准入核对通过（0 条警告）`，EXIT=0；`maven-wrapper.properties` 实测 3.9.9，
  满足 `requireMavenVersion [3.9.9,)`。

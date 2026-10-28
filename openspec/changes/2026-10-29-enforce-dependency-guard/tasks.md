# Tasks

行为依据：`AGENTS.md`「依赖准入」与 `openspec/config.yaml` 许可约束段；方案依据
`openspec/changes/2026-10-29-enforce-dependency-guard/design.md`，调研依据
`docs/research/dependency-guard.md`。

本变更不改业务行为。任务按依赖顺序排列，前置任务未完成不得开工。

## 1. 调研与变更文档

- [x] 1.1 完成依赖护栏调研并落 `docs/research/dependency-guard.md`，验证：覆盖 R1-R6，
      候选表标注未实测项，结论三选一明确
- [x] 1.2 写 `proposal.md` / `design.md` / 本文件，验证：四条改动与调研结论一一对应

## 2. 后端构建期拦截

- [x] 2.1 父 `pom.xml` 引入 `maven-enforcer-plugin` 3.6.3，绑 `validate` 阶段；
      规则：`requireJavaVersion [21,)`、`requireMavenVersion [3.9.9,)`、
      `bannedDependencies`（`searchTransitive=true`，黑名单见 design D1，逐条写依据注释）
      —— 容器内 `./mvnw -B validate` 通过（Maven 3.9.9 / Java 21.0.12.1，5 个工程 Rule 0-2 全 passed）；
      反向：临时加 `com.mysql:mysql-connector-j` 后在 `wms-auth` 报 `banned via the exclude/include list`，已还原并复跑通过
- [x] 2.2 `wms-auth/pom.xml` 为 `spring-boot-starter-data-redis` 补注释，说明 Valkey 客户端定位
      与会话只存基本类型 —— 纯注释 +11 行，未改坐标与版本

## 3. 依赖准入工具与清单

- [x] 3.1 新增 `code/tools/dependency-guard/check_dependencies.py`：解析后端 pom 与前端
      package.json 的直接依赖，与 `allowlist.json` 比对，未登记即失败并给出登记指引
      —— 验证：当前代码全通过（EXIT=0、0 警告）；用临时 root 加未登记依赖得 EXIT=1 并打印登记指引
- [x] 3.2 编写 `allowlist.json`，登记现有全部直接依赖（坐标 / 版本 / 许可证 / 登记理由）
      —— 验证：正向核对 0 警告，说明清单与实际依赖逐条对齐；许可证逐项实测
      （pom licenses 段 / GitHub LICENSE / jar 内 META-INF/LICENSE.txt / npm registry）
- [ ] 3.3 补 `tests/test_check.py` 与 `README.md`（含「如何登记一个新依赖」）
      —— 验证：`python -m unittest discover -s tests` 通过

## 4. 前端与 CI

- [x] 4.1 根 `package.json` 的 `build` 前置 `check:ui`
      —— `pnpm run build` 端到端实测：日志显示先跑 check:ui 再递归构建，两应用均成功，EXIT=0；
      `node scripts/check-ui-boundaries.mjs` 独立通过（2 应用 / 3 共享包 / 77 源文件）
- [x] 4.2 CI tools job 增加依赖准入步骤与该目录 unittest
      —— 两步新增于安装 Python 之后；命令与本地实测命令一致

## 5. 验收

- [ ] 5.1 全量验证：后端 `./mvnw -B verify`、前端 typecheck / build / check:ui、Python 工具测试
      —— 验证：全部通过，无存量误报
- [ ] 5.2 只读审计并写 `completion.md`
      —— 验证：路径未越界、单次提交 ≤400 行、无业务行为与数据库改动

## 提交拆分（一次提交一件事，代码与文档分开）

| # | 提交 | 内容 |
| --- | --- | --- |
| 1 | `docs(research)` | 调研文档 + change 四件套 |
| 2 | `build(backend)` | enforcer 插件 |
| 3 | `docs(backend)` | data-redis 注释 |
| 4 | `feat(tools)` | 依赖准入工具与清单 |
| 5 | `build(frontend)` | build 前置 check:ui |
| 6 | `ci` | CI 步骤 |

## 本期不做（明确登记）

- 菜单层级硬编码、`router-link` / `<a>` 包裹菜单项、间距圆角字号令牌的检查
  —— 属同一护栏主题，未在本次确认范围内
- 前端传递依赖的许可证扫描 —— 依赖 `pnpm licenses`，本机 9.11 与 CI 11.25 版本差异未验证
- CVE 扫描 —— 需外网下载漏洞库，与「秒级离线」冲突

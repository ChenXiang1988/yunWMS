# Proposal

## Why

技术选型的约束目前只覆盖到「架构边界」与「前端 UI 边界」两处，两处都有机器执行：
ArchUnit 跑在 `mvn verify`，`check-ui-boundaries.mjs` 跑在 CI 前端 job。

唯独**依赖准入**没有执行点。AGENTS.md 要求引入依赖前确认「许可证、最近维护时间、可否被现有依赖替代」，
`openspec/config.yaml` 点名禁止 GPL/AGPL/SSPL 依赖（XXL-Job、JeeWMS、MySQL、Redis 7.4+），
但这些约束只对读过文档的人生效——加一个新依赖不会让任何东西变红。

后果已经在代码里留下一处证据：`wms-auth/pom.xml` 引入 `spring-boot-starter-data-redis`
时没有任何注释，而同一文件里 `mybatis-plus-jsqlparser` 那段把许可证与选型理由写得很完整。
服务端实际跑的是 Valkey 8、客户端是 Apache-2.0，合规；但依赖名是 redis，
与 `config.yaml`「Redis 7.4+ 禁止」的表述直接冲突，且没有任何地方说明这一点。
下一个读到这行 pom 的执行者（人或 AI）只能靠猜。

调研结论见 `docs/research/dependency-guard.md`。

## What Changes

- 后端父 pom 引入 `maven-enforcer-plugin`：`bannedDependencies` 按坐标拦截点名黑名单
  （含传递依赖），`requireJavaVersion` / `requireMavenVersion` 钉住构建环境。
- 新增 `code/tools/dependency-guard/`：Python 准入清单核对（零第三方依赖），
  比对后端 pom 与前端 package.json 的直接依赖与清单，未登记即失败；含单元测试。
- 新增准入清单 `allowlist.json`，登记现有全部直接依赖的坐标、许可证与登记理由，
  作为该工具与「依赖准入三问」的唯一真源。
- `wms-auth/pom.xml` 为 `spring-boot-starter-data-redis` 补注释，说明它是 Valkey 客户端、
  不是 Redis 服务端引入，消除与 `config.yaml` 的表观冲突。
- 前端根 `build` 脚本前置 `check:ui`，使其在 `pnpm build` 路径上也必然执行，不只依赖 CI 独立步骤。
- CI 的 tools job 增加依赖准入工具步骤。

**BREAKING**：无业务行为变更。存量依赖全部在清单内，护栏不会阻断当前构建；
此后新增依赖未登记会让构建失败，这是本变更的目的。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。

## Impact

- **新增**：`code/tools/dependency-guard/`（脚本 + 清单 + 测试）、`docs/research/dependency-guard.md`
- **修改**：`code/backend/pom.xml`、`code/backend/wms-auth/pom.xml`、`code/frontend/package.json`、`.github/workflows/ci.yml`
- **不变**：业务代码、数据库、API、前端页面、既有 ArchUnit 与 UI 边界规则

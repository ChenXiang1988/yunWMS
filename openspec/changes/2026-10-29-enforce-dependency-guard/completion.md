# 完成说明

## 1. 改了哪些文件

| 文件 | 改动 |
| --- | --- |
| `code/backend/pom.xml` | +97：引入 `maven-enforcer-plugin` 3.6.3，绑 `validate`；`requireJavaVersion [21,)`、`requireMavenVersion [3.9.9,)`、`bannedDependencies`（`searchTransitive=true`，点名黑名单逐条带依据注释） |
| `code/backend/wms-auth/pom.xml` | +11：`spring-boot-starter-data-redis` 补注释，说明是 Valkey 客户端而非 Redis 服务端 |
| `code/tools/dependency-guard/check_dependencies.py` | +176：准入核对主脚本，零第三方依赖 |
| `code/tools/dependency-guard/allowlist.json` | +181：后端 17 项、前端 11 项的准入登记，含许可证与登记理由 |
| `code/tools/dependency-guard/tests/test_check.py` | +145：9 条自检 |
| `code/tools/dependency-guard/README.md` | +60：用法与「如何登记一个新依赖」 |
| `code/frontend/package.json` | 1 行：`build` 前置 `check:ui` |
| `.github/workflows/ci.yml` | +8：tools job 增加准入核对与自检两步 |
| `docs/research/dependency-guard.md` | +109：调研（R1-R6） |
| `openspec/changes/2026-10-29-enforce-dependency-guard/` | proposal / design / tasks / audit / 本文件 |

共 7 笔提交，343 / 97 / 11 / 181 / 381 / 2 / 8 行，均 ≤400。

## 2. 怎么验证的

| 项 | 方式 | 结果 |
| --- | --- | --- |
| 后端 enforcer | 容器内 `./mvnw -B validate`（Maven 3.9.9 / Java 21.0.12.1） | 5 个工程 Rule 0-2 全 passed |
| 后端反向验证 | 临时加 `com.mysql:mysql-connector-j` | `wms-auth` 报 `banned via the exclude/include list`，已还原并复跑通过 |
| 后端既有门禁 | 容器内 `./mvnw -B test` 跑三个架构测试类 | ArchUnit 7 用例通过，BUILD SUCCESS |
| 准入核对正向 | `python check_dependencies.py` | EXIT=0，0 警告（说明清单与实际依赖逐条对齐） |
| 准入核对反向 | 临时 root 加未登记依赖 | EXIT=1，报错含登记指引 |
| 工具自检 | `python -m unittest discover -s tests` | 9 例 OK |
| 前端 | `pnpm run build`（端到端）、`pnpm typecheck`、`node scripts/check-ui-boundaries.mjs` | 均 EXIT=0，日志显示 check:ui 先执行 |
| 许可证 | 逐项实测：pom `licenses` 段 / GitHub LICENSE / jar 内 `META-INF/LICENSE.txt` / npm registry | Flyway 拆 jar 实测为 Apache-2.0；sa-token、springdoc、swagger 注解的 pom 无 licenses 段，改从 LICENSE 文件取 |

**未做到的验证**（如实说明）：完整 `./mvnw -B verify` 的集成测试未在本机跑——
Testcontainers 需要 docker-in-docker，本机未配置；enforcer 绑 `validate` 在其之前，由 CI 覆盖。

## 3. 遗留问题

1. **enforcer 只能按坐标拦截，不能按许可证字段拦截**。挡得住被点名的构件，
   挡不住一个没被点名过的 AGPL 库。这一层由准入清单兜底：任何新依赖都要登记，登记即触发三问。
   该边界已写在父 pom 的插件注释里，避免后人误以为加了 enforcer 就等于许可证安全。
2. **构建插件未纳入准入登记**：`maven-enforcer-plugin` 自身不在清单内，脚本不解析 `<build><plugins>`。
   扩展需新增一整类扫描与清单段，留作后续 change。
3. **前端传递依赖的许可证未扫描**：依赖 `pnpm licenses`，本机 9.11 与 CI 11.25 版本差异未验证。
4. **`check-ui-boundaries.mjs` 的检查范围未扩展**：菜单层级硬编码、`router-link` / `<a>` 包裹菜单项、
   间距圆角字号令牌这三类在 `frontend-ui` 规则里有、脚本里没有，不属本次确认的范围。
5. **`a0d76e6` 已达 381 行**，距 400 仅 19 行；同类工具再增长需提前拆笔。

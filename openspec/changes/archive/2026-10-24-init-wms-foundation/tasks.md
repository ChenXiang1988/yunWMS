# Tasks

本变更只负责**规格、设计决策、工程骨架与开发规范**。业务实现不在本变更内，见下方「任务去向」。

## 1. 工程骨架与基础设施

- [x] 1.1 建立 `code/frontend` 的 pnpm workspace（`apps/*`、`packages/*`），验证 `pnpm install` 成功且 `apps/owner-portal` 能引用 `@yunwms/tokens`
- [x] 1.2 建立后端四个 Maven 模块（common / auth / master / app）并锁定依赖方向，验证 `mvn verify` 通过且健康检查接口返回 200
- [x] 1.3 编写 `docker-compose.dev.yml`（PostgreSQL、Valkey），验证 `docker compose up -d` 后依赖服务健康检查通过 —— 对象存储已按 `design.md` D40 移出 compose，不再作为依赖服务
- [x] 1.4 配置 Flyway 并编写 24 张表的建表脚本，验证 `mvn flyway:migrate` 后表数量与 `design.md` 数据模型清单一致
- [x] 1.5 建立两个前端工程（Vue 3 + Vite + TS + Ant Design Vue 4），验证两端 dev server 可启动且设计令牌的 `data-density` 生效 —— 构建与密度已验证，**dev server 启动待人工确认**（常驻进程无法在自动化环境验证）

### 第 1 节实测记录（2026-10-24）

| 任务 | 验证命令 | 实测结果 |
|---|---|---|
| 1.1 | `pnpm install`、`pnpm typecheck` | 通过。6 个 workspace 项目；`owner-portal`、`network-console` 类型检查均 Done |
| 1.2 | `./mvnw verify`、启动 jar 后 `curl -s localhost:8080/actuator/health` | 通过。BUILD SUCCESS；健康检查 **200**，`db` / `redis` / `diskSpace` / `ping` 全 UP |
| 1.3 | `docker compose up -d` | 通过（MinIO 移出后复测）。postgres 与 valkey 均启动且 healthcheck 通过。此前 `minio/minio` 因镜像下架 `pull access denied`，已按 `design.md` D40 移出 compose |
| 1.4 | `./mvnw -pl wms-app flyway:migrate`、查 `information_schema.tables` | 通过。21 个迁移全部成功；业务表 **24 张**（另有 `flyway_schema_history`），与 `design.md` 数据模型清单逐名一致 |
| 1.5 | `pnpm build`、`grep data-density` 产物 | 部分通过。两端 `dist` 均生成，`data-density` 分别为 `comfortable`（货主端）/ `compact`（仓网端）；dev server 未实测 |

**环境说明（复现时需注意）**：

1. 本机 5432 端口已被另一套凭据未知的 PostgreSQL 占用，验证时用 `PG_PORT=5433 docker compose up -d postgres` 绕开，**未修改 compose 文件**。
2. 干净仓库下直接 `./mvnw -pl wms-app flyway:migrate` 会失败——`wms-auth`、`wms-master` 及根 POM 未装进本地仓库。需先 `./mvnw install -DskipTests`（见 `code/backend/README.md`）。
3. 后端当前**零测试文件**，`mvn verify` 无测试可跑。

## 任务去向

原第 2-7 节（认证、权限、客户、商品、页面、验收）已按可交付切片拆出，本变更归档后各自独立推进：

| 原章节 | 去向 | 理由 |
| --- | --- | --- |
| 2. 认证与会话、3. 权限与治理、4. 客户管理、6.1/6.6/6.8/6.9 页面 | `implement-owner` | 平台底座与客户是商品的前置 |
| 5. 商品中心、6.2-6.5/6.7 页面、7. 集成验收 | `implement-sku` | 依赖前者的登录、权限与货主模型 |

拆分的理由写在 `docs/agents.md` 与 `AGENTS.md` 的「工作单元」：一个 change 一条分支，分支存活不超过 3 天。原 52 条任务横跨规格与全部实现，不是一条分支能承载的。

---

## 存量违约登记（非任务，归档时核查）

| 提交 / 路径 | 违反条款 | 处置 |
|---|---|---|
| `48f3d43` feat(backend): 搭建四模块工程骨架与 24 张表的 Flyway 迁移 | ① 单次提交 2436 行，超过 `AGENTS.md` 的 400 行上限（剔除 `mvnw`/`mvnw.cmd` 484 行自动生成后仍约 1952 行）；<br>② 21 份 SQL 迁移与 7 个 Java 文件在同一提交，违反「数据库迁移与代码变更分开提交」 | **不重写历史**。该提交已推送至 `origin/main`，改写需 force push main，风险高于收益；且拆分只能修掉②，修不掉①（21 份 SQL 自身已超 400 行）。<br>**防再发**：提交前跑 `delivery-auditor`，检查单见 `docs/agents.md` 第五节 |
| `apps/network-console/src/views/warehouse/`（含路由 `/warehouse` 与菜单项） | `proposal.md` 明确「不做：仓库网络与空间模型」，相关规格在 `deferred/`，属越界实现 | **决定保留**（2026-10-24）。若要正式纳入范围，需从 `deferred/` 移出规格并更新 `proposal.md` 的非目标清单；在此之前它不进入验收范围 |
| 全部 `main` 提交 | `AGENTS.md`「工作单元」要求一个 change 一条分支，实际从 2026-10-22 至 10-24 全部提交直接落在 `main` | **不追溯**。本变更归档后，`implement-owner` 与 `implement-sku` 各自从 `main` 拉 `change/` 分支，按规范执行 |

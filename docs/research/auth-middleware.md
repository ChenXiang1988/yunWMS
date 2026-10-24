# 认证中间件选型调研

**主题**：`wms-auth` 的认证框架选型
**日期**：2026-10-24
**时效**：本文为 2026-10-24 的快照，记录当时状态；后续变化以仓库现状为准，本文不回改。
**触发原因**：`openspec/changes/init-wms-foundation/design.md` D11 指定 Sa-Token，实际 `code/backend/wms-auth/pom.xml` 引入的是 `spring-boot-starter-security`，全仓无 `sa-token` 命中。设计与实现分叉，需裁决。
**范围**：只回答认证框架的选型，不含 TOTP 第二因子（`dev.samstevens.totp` 与框架无关，不受本次裁决影响）。

> 本调研为**轻量版**：不做完整 R1-R6 的同类系统检索，只针对两项硬需求做能力核对，并核对许可证与活跃度。原因是认证框架的候选集很小且明确（Sa-Token / Spring Security），无需广撒网。

---

## R1 需求要点

来自 `design.md` D11 与 `tasks.md` 第 2 节：

| 需求 | 来源 | 是否硬需求 |
|---|---|---|
| **双主体登录**：员工登录名 + 货主编码或子账号登录名，两套账号互不干扰 | `tasks.md` 2.1 | 是 |
| **管理端踢人下线**：旧令牌失效后立即返回 401 | `tasks.md` 2.4 | 是 |
| 密码策略、登录失败锁定（5 次错锁 30 分钟） | `tasks.md` 2.2 | 否（与框架无关，自实现） |
| 首次登录强制改密 | `tasks.md` 2.3 | 否（业务层） |
| 令牌签发与校验、会话管理 | `tasks.md` 2.x 通篇 | 是 |
| 与 MyBatis 拦截器数据范围方案共存 | `design.md` D9/D10 | 是（框架不得独占鉴权入口） |

**约束**：技术栈 Java 21 + Spring Boot 3.x（`openspec/config.yaml`）；零授权预算；GPL/AGPL/SSPL 一律不得引入。

---

## R2 检索记录

| 检索词 | 时间 | 来源 |
|---|---|---|
| `Sa-Token 多账号体系 踢人下线 kickout StpUtil` | 2026-10-24 | sa-token.cc 官方文档 v1.22.0 / v1.28.0 / 最新版 |
| `Sa-Token 多账号体系 StpLogic 自定义 StpUserUtil 双主体` | 2026-10-24 | sa-token.cc 官方「多账号认证」章节、GitHub dromara/sa-token |
| `sa-token GitHub dromara 开源协议 Apache 最近版本 维护` | 2026-10-24 | sa-token.cc 首页、GitHub dromara/sa-token |

---

## R3 候选比较

### 候选 A：Sa-Token（`design.md` D11 原定）

| 维度 | 结论 |
|---|---|
| 功能覆盖度 | **双主体登录：原生支持**。官方「多账号认证」模型，`new StpLogic("user")` 可自定义第二套账号体系，两套账号的 tokenName、配置相互隔离。<br>**踢人下线：原生支持**。`StpUtil.kickout(loginId)`、`kickoutByTokenValue`、`logoutByLoginId`、账号封禁（含永久封禁），且区分「强制注销」与「踢人下线」两种语义（后者会给出「已被踢下线」的场景值 -5）。<br>另有多端登录、同端互斥、七天免登录、自动续签、Redis 集成 |
| 活跃度 | 当前 v1.46.0；2020 年至今持续迭代；Gitee 48,800+ star（文档统计日 2026-01-25）、GitHub 18,523 star |
| 许可证 | **Apache-2.0**。允许商用、闭源分发，无 GPL 风险 |
| 依赖与可维护性 | 轻量级，单一 starter 依赖；中文文档、中文注释、中文社区 |
| 栈兼容性 | JDK 1.8+、Spring Boot 2.0+，提供 `sa-token-spring-boot4-starter`；与 MyBatis 拦截器方案无冲突 |
| 二次开发成本 | 低。双主体与踢人两项均为开箱能力，不必自建 |

### 候选 B：Spring Security（当前实现）

| 维度 | 结论 |
|---|---|
| 功能覆盖度 | 认证、鉴权、会话管理能力完备，但**双主体登录需自建**（多 `AuthenticationProvider` 或按主体类型路由的 `UserDetailsService`）；**踢人下线需自建**（`SessionRegistry` 或自建 Token 服务）。两项均非开箱即用 |
| 活跃度 | Spring 官方项目，随 Spring Boot 发版，长期稳定 |
| 许可证 | Apache-2.0 |
| 依赖与可维护性 | 依赖体量大于 Sa-Token；与 Spring 生态深度耦合 |
| 栈兼容性 | 与 Spring Boot 3.x 原生集成，无兼容问题 |
| 二次开发成本 | 中。两项硬需求都要自己实现并有测试覆盖 |

### 候选 C：Keycloak / Casdoor

`design.md` D11 已否决：**首期只有单系统，独立部署身份服务不划算**。本次不重新评估。

---

## R4 结论

**选 A：回到 Sa-Token，改实现而非改设计。**

> **状态：已确认（2026-10-24）**。团队对 Spring Security 无强依赖或偏好，按 A 执行。
> 实现迁移（换依赖、改 `SecurityConfig`、建两套 `StpLogic`）属业务功能实现，单开 change 与分支，不在 `chore/agent-orchestration` 内做。

理由：

1. 两项硬需求 Sa-Token 均原生支持，Spring Security 均需自建——这是本次调研唯一的关键差异。
2. `design.md` D11 已有决策、理由与被否备选，记录完整；实现偏离设计**没有任何书面理由**。
3. 认证任务（2.x）尚未实现，此刻改回的成本最低：换依赖 + 调整 `SecurityConfig` 即可，无存量登录逻辑要迁移。
4. 与已定的 MyBatis 拦截器数据范围方案（D9/D10）不冲突，Sa-Token 不独占鉴权入口。

**若改实现，需同步做的事**：

- `wms-auth/pom.xml` 换依赖（注意 Spring Boot 3.x 用 `sa-token-spring-boot-starter`，Boot 4.x 才用 `spring-boot4-starter`）
- 移除或改造现有 `SecurityConfig.java`（含 `/actuator/health` 放行规则需迁移）
- 双主体用两套 `StpLogic`，tokenName 加后缀避免冲突
- TOTP 第二因子保持 `dev.samstevens.totp`，不受影响

---

## R5 风险与待确认

| # | 事项 | 类型 |
|---|---|---|
| 1 | 团队熟悉度：若团队对 Spring Security 有强依赖或明确偏好，可反向选 B（改 design），但必须把被否原因写进 `design.md` D11 | **已确认（2026-10-24）**：无强依赖或偏好，按 A 执行 |
| 2 | Sa-Token 为国产框架，文档与社区以中文为主；若后续有非中文团队接手，维护成本会上升 | 风险 |
| 3 | 引入 Sa-Token 后需核对与 Spring Security 的依赖是否彻底移除，避免两套鉴权并存 | 风险 |
| 4 | 本次未评估 Sa-Token 的 SSO/OAuth2.0 能力——首期单系统用不到，若未来多系统需重新调研 | 边界 |

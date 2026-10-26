# 缓存与 Valkey 用法调研

**主题**：Valkey（Redis 兼容）在本项目中的定位、选型与首期落地范围
**日期**：2026-10-26
**时效**：本文为 2026-10-26 的快照，记录当时状态；后续变化以仓库现状为准，本文不回改。
**触发原因**：`docker-compose.dev.yml` 已起 Valkey 容器、`wms-auth/pom.xml` 已引入 `spring-boot-starter-data-redis`，但全仓零使用（见 `docs/research/ai-coding-sustainability.md:98`）。会话仍是 Sa-Token 进程内存态，存在「重启即全员掉线」与「无法多实例」两个缺口，需要裁决要不要用、用来做什么。
**范围**：只回答「Valkey 承担哪些职责、首期做哪一块、其余为什么不做」。不覆盖消息队列选型、不覆盖前端缓存。

---

## R1 需求要点

### 功能范围

| 候选用途 | 现状 | 是否本期 |
|---|---|---|
| 会话存储（登录态） | Sa-Token 默认内存 `SaTokenDao`，重启即失效；容器只有单实例，暂未暴露问题 | 是 |
| 读缓存（字典、权限点、菜单树、仓库/货主基础信息） | 全部直查 PostgreSQL；当前数据量小，未测到瓶颈 | 否 |
| 登录与接口限流 | 账号锁定已由数据库承担（`FailedAttemptRecorder`，见其类注释）；IP 维度无限流 | 否 |
| 幂等/防重（提交类接口） | 无；重复提交靠前端按钮禁用 | 否 |
| 分布式锁（波次生成、任务指派、库存预占入口） | 无；并发正确性目前靠数据库条件更新 | 否 |
| 延迟/超时任务 | 无（任务模块尚未建） | 否 |

### 关键特征

- 单租户、私有部署、单机起步；并发量级为仓库作业人员级（数十到数百在线），不是互联网高并发。
- 会话需支持「管理员踢人下线」（`openspec/changes/archive/2026-10-24-init-wms-foundation/tasks.md` 2.4），因此需要一份可被服务端主动失效的会话记录，纯无状态令牌不满足。
- 库存、单据、主数据是业务真值，唯一真源必须是 PostgreSQL。
- PDA 端会话存活时间长（一个班次 8 小时以上），中途掉线会打断扫码作业。

### 边界与非目标

- 不把库存可扣减数量放进缓存。
- 不用 Valkey 做分布式事务、消息总线、发号器。
- 不做 Redis Cluster（单租户数据量与并发都不需要）。
- 不引入 Redisson 等第二套客户端抽象。

### 现存约束

- `openspec/config.yaml:15`：GPL / AGPL / SSPL 一律不得引入，明列 Redis 7.4+；零商业授权预算。
- 技术栈已声明 Valkey（`openspec/config.yaml:13`），不是 Redis。
- 单机部署，运维由使用者自行完成，不能假设有专职 DBA。

---

## R2 检索记录

| 检索词 | 时间 | 来源 |
|---|---|---|
| `Valkey 8 license BSD-3-Clause latest release 2026 Linux Foundation` | 2026-10-26 | valkey.io 首页（版本与许可证声明） |
| `Redis 8 license AGPLv3 Redis 7.4 RSALv2 SSPL license change` | 2026-10-26 | redis.com/legal/licenses、redis.ac.cn 许可概述 |
| `sa-token redis 集成 sa-token-redis-template 依赖 官方文档` | 2026-10-26 | sa-token.cc「集成 Redis」章节 |
| `sa-token-redis-template 序列化 JDK Jackson SaTokenDao searchData 限制` | 2026-10-26 | sa-token.cc「JSON 序列化扩展」「常见问题排查」 |
| `Garnet Microsoft MIT license vs KeyDB maintained vs Dragonfly license` | 2026-10-26 | microsoft/garnet 仓库介绍页、dbpro.app 与 markaicode.com 的 2026 年对比文 |

未做 GitHub 全量同类系统检索：本主题的候选集是「键值存储实现 + 会话集成方式」，不是业务域，三家对比已覆盖实质差异；此处如实标注为**轻量调研**，与 `auth-middleware.md` 同一处理方式。

---

## R3 候选比较

### 候选 A：保持 Sa-Token 内存态（不引入 Valkey 使用）

| 维度 | 结论 |
|---|---|
| 功能覆盖度 | 登录、踢人、改密门禁全部可用；**重启掉线**、**无法多实例**两条缺口保留 |
| 活跃度 | 不涉及 |
| 许可证 | 不涉及 |
| 依赖与可维护性 | 最简，零新增故障点 |
| 栈兼容性 | 现状 |
| 二次开发成本 | 0；但将来要支持多实例时需重做会话层，涉及所有登录链路回归 |

### 候选 B：Valkey + `sa-token-redis-template`（推荐）

| 维度 | 结论 |
|---|---|
| 功能覆盖度 | 官方文档明确「重启数据不丢失、分布式多节点会话一致」；踢人下线、同端互斥等策略不受影响。`sa-token-spring-boot3-starter` 已自带 `sa-token-jackson`，会话按 JSON 序列化，`SessionAttributes` 目前只放 String/包装类/`Instant`，无需注册类型白名单 |
| 活跃度 | Valkey 上游当前 9.1.2（2026-09-01 发布），Linux Foundation 治理；Sa-Token 1.46.0 与仓库版本一致 |
| 许可证 | Valkey **BSD-3-Clause**；`sa-token-redis-template` 与 `commons-pool2` 均 Apache-2.0，无授权风险 |
| 依赖与可维护性 | 复用已引入的 `spring-boot-starter-data-redis`（Lettuce），只加一个 Sa-Token 适配包；无第二套客户端 |
| 栈兼容性 | Spring Boot 3.4.1 + Sa-Token 1.46.0，官方 Starter 对应；配置走 `spring.data.redis.*`，换哨兵只改配置 |
| 二次开发成本 | 低；回退成本也低（去掉适配包与配置即回内存态） |

### 候选 C：Redis 8

**直接排除**：Redis 8 起为 RSALv2 / SSPLv1 / AGPLv3 三选一，后两者被仓库明文禁止，RSALv2 属 source-available 且限制对外提供服务；`openspec/config.yaml:15` 另已明文禁止 Redis 7.4+。Redis 7.2 及更早虽为 BSD，但已停止维护，不构成可选项。

### 候选 D：Garnet（Microsoft Research）

| 维度 | 结论 |
|---|---|
| 功能覆盖度 | RESP 兼容，现有客户端可直连；会话场景够用 |
| 活跃度 | 活跃（2026-09 发 2.1.8，仓库近一年 500+ 提交） |
| 许可证 | MIT，允许商用与闭源分发 |
| 依赖与可维护性 | C# / .NET 运行时，与 Java 技术栈异构；生产运维案例与故障处理资料少于 Valkey |
| 栈兼容性 | 协议兼容，但镜像、监控、备份、升级路径需自行验证 |
| 二次开发成本 | 中；相对已落地的 Valkey 无增益 |

**落选原因**：本项目只用「字符串 + TTL + 少量键扫描」，Valkey 的功能与性能已远超需求；换 Garnet 只多一个异构运行时和一条未验证的运维路径。

### 候选 E：Dragonfly

**落选原因**：BSL 1.1（非 OSI 许可，四年后转 Apache-2.0），与「零授权预算、许可证从简」的既定原则相悖；其多线程吞吐优势在本项目量级下用不上。

### 候选 F：KeyDB

许可证与近两年发版情况**未验证**，且相对 Valkey 无功能差异可陈述，不作为候选。

---

## R4 结论

**选 B：Valkey 承担会话存储，本期只做这一件事。**

> **部署形态：已确认（2026-10-26）**——本期单机 + 持久化，配置与键设计不写死单点，留升级到主从/哨兵的接口。
> **范围：待确认**——只做会话，读缓存/限流/幂等/锁/队列一律不进本期。

理由：

1. **真正缺的是会话，不是缓存。** 当前数据量下直查 PostgreSQL 未测到瓶颈，上读缓存属于为不存在的问题增加一致性问题；而重启掉线是确定会发生的事（每次发版必现），PDA 班次中途掉线直接打断作业。
2. **踢人下线这个已确认需求排除了无状态令牌方案。** 要做服务端主动失效，就必须有一份共享的会话记录；既然要存储，用已有 Valkey 比新开一张数据库会话表更省（会话是高频读写、可丢失数据，不该占用业务库）。
3. **许可上没有第二种选择。** Redis ≥7.4 三类许可全触禁；Valkey 是 BSD 分支且已在 compose 中落地，只差把它用起来。
4. **单机也会挂，但代价可接受。** Valkey 与应用同机部署，机器级故障本就同时影响两者；引入会话存储带来的边际可用性损失主要落在「Valkey 进程单独崩溃」，此时表现为全员重新登录，不产生脏数据。
5. **不引入锁与队列，避免过度设计。** 并发正确性继续由数据库条件更新承担；任务超时用「状态 + 到期时间」扫描，比引入投递语义更可查。

### 首期落地的具体形态

- 依赖：`sa-token-redis-template` + `commons-pool2`（均为 Apache-2.0），复用现有 `spring-boot-starter-data-redis`。
- 配置：只用 `spring.data.redis.host/port/password/timeout/lettuce.pool.*`，不写自研连接串；将来换哨兵只加 `spring.data.redis.sentinel.*`。
- 持久化：`appendonly yes` + `appendfsync everysec`（当前 `docker-compose.dev.yml:27` 是 `--appendonly no`，必须改）；`maxmemory-policy noeviction`，容量按峰值会话数估算并告警——静默驱逐会话会表现为「随机掉线」，比写失败更难查。
- 键空间：Sa-Token 自有前缀 `satoken:`；业务缓存将来统一 `yw:<模块>:<用途>:<业务键>`，两套不混用，禁止无 TTL 键。
- 安全：绑定内网地址、设置密码、禁用或重命名 `FLUSHALL`/`KEYS`。
- 镜像：当前 `valkey/valkey:8-alpine` 是浮动标签，上游已到 9.1.2；落地时钉到具体小版本，升 9 单独评估，不在本 change 内做。
- 降级：Valkey 不可用时登录与已登录请求统一失败并给出可读错误，不做「自动回退内存态」——两套会话源切换会造成「同一用户时而在时而不在」的假象。

### 后四类用途的启用条件（不排期，出现信号再开 change）

| 用途 | 触发条件 |
|---|---|
| 读缓存 | 字典/权限/菜单类查询出现可复现的慢查询（单次 > 200ms 或占慢日志多数），或表行数过万且每请求必查 |
| 限流 | 服务暴露到公网，或出现暴力登录/爬接口的实际事件 |
| 幂等/防重 | 出现重复提交造成的重复单据，且有可复现路径 |
| 分布式锁 | 出现数据库条件更新挡不住的并发写冲突（乐观锁重试率或死锁可观测） |
| 延迟/超时任务 | 任务模块上线且数据库扫描出现压力 |

---

## R5 风险与待确认

### 风险

| 风险 | 影响 | 应对 |
|---|---|---|
| 会话强依赖 Valkey | Valkey 挂 = 无法登录 + 已登录请求失败 | 同机部署，边际损失小；监控与告警必须跟上；不自动回退内存态 |
| AOF 丢失窗口 | `everysec` 最坏丢 1 秒写入，表现为少量用户掉线 | 接受；不追求 `always`（每次请求落盘，损失性能） |
| 内存驱逐 | 会话被驱逐 = 随机掉线 | `noeviction` + 容量估算 + 内存告警 |
| 序列化兼容 | 升级 Sa-Token 或改会话字段后旧会话反序列化失败 | 会话值只放 String/包装类/`Instant`（现状已如此）；改字段等于全员重登，写进升级说明 |
| 镜像浮动标签 | 重建容器可能拉到不同小版本 | 钉版本 |
| 单机无故障切换 | Valkey 重启期间不可用 | 已确认本期接受，留哨兵升级口 |

### 待确认（未闭环前不进入实现）

1. **接受「Valkey 不可用时全员无法登录」**——这是把会话移出进程内存的必然代价，需明确认可。
2. **接受 AOF `everysec` 的 1 秒丢失窗口**，不要求零丢失。
3. **确认本期非目标清单**（读缓存、限流、幂等、锁、队列）不在本 change 内顺带做。

---

## R6 产物与后续

- 本文路径：`docs/research/cache-and-valkey.md`，后续 change 的 `proposal.md` 必须引用。
- 建议 change：`session-on-valkey`（一个垂直切片，含最小基建：连接配置、AOF、键前缀、健康检查、降级错误、集成测试「重启后令牌仍有效」）。
- 不在本 change 内：删除 `spring-boot-starter-data-redis` 的替代方案（若最终否决本方案，则该依赖应一并移除，不留在 pom 里当摆设）。

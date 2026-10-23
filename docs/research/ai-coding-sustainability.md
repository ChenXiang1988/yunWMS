# AI 主导编码的工程可持续性调研

- 调研时间：2026-10-23
- 适用对象：yunWMS（多货主 3PL 云仓，全量代码由 AI 编写，人类只做验收）
- 结论状态：**待用户确认**（第 7 节的 P0 建议可立即执行，P1/P2 需确认后再开 change）
- 与已有调研的关系：不重复 `wms-open-source-landscape.md`（业务系统选型）、`frontend-stack.md`（前端技术栈选型）。本篇只解决一个问题：**当写代码的主体是 AI 时，这套工程如何保持「可用、可长期维护」**。

---

## 1. 需求梳理（R1）

### 1.1 问题陈述

本仓库当前的工程纪律（`AGENTS.md`、`.codebuddy/rules/*`、`openspec/config.yaml`）写得比多数人类团队都细：模块边界、令牌层级、菜单递归、单次提交 400 行、迁移与代码分离。但这些纪律**目前只以文字形式存在，没有任何机器校验**。这带来一个具体后果：AI 会忠实地"读懂规则"，然后在规则没覆盖的地方持续产出"看起来合规、实则漂移"的代码，且每次提交都能通过现有的唯一一道自动化检查（前端 CI）。

所以本篇要回答的不是"怎么让 AI 写得更快"，而是：

1. 现状里哪些东西已经坏了、哪些只是还没建？
2. 行业里把「规则」变成「门禁」的标准做法是什么？
3. 按什么顺序做，才能让每一步都有可验证的收益？

### 1.2 功能范围（落在哪些域）

| 域 | 内容 |
| --- | --- |
| 版本控制与交付 | 变更纳入 git 的完整性、分支策略、归档纪律、CI 覆盖面 |
| 后端质量门禁 | 架构边界校验、静态检查、模块化单测、测试有效性 |
| 前端质量门禁 | lint/format、一致性规则、死代码与循环依赖、UI 库与令牌层级的机检 |
| 数据库变更安全 | 迁移 lint、迁移可从零重建、破坏性变更拦截 |
| 规则与流程治理 | `AGENTS.md` / rules / spec 三者的关系，规则如何可执行化 |

### 1.3 关键特征

- **写代码的主体是 AI，人类只做验收**：这决定了所有"靠人盯"的纪律都不可靠，必须转成机器判定。
- **人类不逐行 review 中间过程**：意味着单次提交的"自证"必须由 CI 完成，而不是靠提交者（AI 自己）声称。
- **零商业授权预算**：所有新增依赖必须是宽松许可证（MIT/Apache-2.0/BSD），GPL/AGPL/SSPL 一票否决。
- **一个 change 一个垂直切片、归档后即删**：流程已定，问题在于它没有 CI 支撑。
- **后端 21 张表已建、零业务代码**：典型的"数据结构先行"状态，也是 AI 最容易"对着表臆造行为"的地方。

### 1.4 边界与非目标

**本期不做**

- 不引入微服务、不做模块化单体框架的整体改造（Spring Modulith 属"待定候选"，见第 5.1 节）。
- 不做全量历史代码重构；当前后端 299 行、前端约 2 830 行，不存在重构负担。
- 不做 AI 编程工具（Copilot / Cursor / CodeRabbit）的采购与接入决策——本篇只关注**与工具无关的工程基建**。
- 不引入付费 SaaS 作为质量门禁的唯一手段。

**本期只解决两件事**

1. 把已经写下的规则，变成 CI 里能自动失败的检查。
2. 堵住会让现有成果丢失或悄悄退化的口子（未入 git 的后端、无 backend CI、无迁移回滚）。

### 1.5 现存约束

- 技术栈固定：Java 21 + Spring Boot 3.4.1 + MyBatis-Plus 3.5.9 + PostgreSQL + Flyway；前端 Vue 3 + Ant Design Vue 4 + pnpm workspace。
- 仓库已有 `openspec` spec 驱动流程（`openspec/config.yaml` 有 rules 段），无需另起流程体系。
- 单人（人类）验收 + AI 批量产出的实际模式，没有第二位工程师做交叉复核。
- 依赖准入需三项确认：许可证、最近维护时间、是否可被现有依赖替代。

---

## 2. 工作区现状事实

> 全部结论来自本地文件读取与命令执行，不含推测。证据路径为绝对路径。

### 2.1 版本控制：一处必须立刻处理的口子

```
$ git branch -a
* main
  remotes/origin/HEAD -> origin/main
  remotes/origin/main
```

| 事实 | 证据 | 影响 |
| --- | --- | --- |
| **从未使用 `change/<change-id>` 分支**，全部工作直接落在 `main` | `git branch -a` 只有 main | `AGENTS.md`「一个 OpenSpec change = 一条分支 = 一个 PR」未落地；改动无法按 change 隔离与回滚 |
| **`code/backend/` 整个目录未被 git 跟踪**（41 个文件，全部 untracked） | `git status --porcelain -uall code/backend` → 41 行 `??` | 后端 21 个迁移脚本、7 个 Java 类、全部 pom.xml **没有任何版本控制保护**。一次误删/误清即全部丢失 |
| 7 篇调研文档、`docs/prd/`、`docs/standards/`、平台清单 xlsx 同样 untracked | `git status --short` | 研究沉淀与需求资产随时可能丢 |
| 两个已完成 change 未归档 | `openspec/changes/archive/` 为空；`migrate-network-console/tasks.md` 16 项全勾选、`migrate-owner-portal/tasks.md` 15 项全勾选 | 「归档后删除分支」从未执行，`openspec` 的 spec 增量无法沉淀进 `openspec/specs/` |
| `init-wms-foundation/tasks.md` 52 项**全部未勾选**，且文件处于 modified 状态 | `openspec/changes/init-wms-foundation/tasks.md` | 后端 0 业务代码与此一致，不是勾选遗漏 |
| 无 force-add 的编译产物 | `git ls-files \| grep -E '\.(class\|lst)$'` → 空 | `target/` 已被 `.gitignore` 正确忽略 ✅ |

### 2.2 后端：工程骨架质量高，但零校验

| 项 | 事实 | 证据 |
| --- | --- | --- |
| Java 代码量 | **7 个文件 / 299 行**；0 个 Controller、0 个 Service、0 个 Mapper、0 个实体、0 个 DTO | `code/backend/wms-common/src/main/java/com/yunwms/common/**`、`wms-auth/**`、`wms-app/**` |
| 数据库 | **21 个 Flyway 迁移 / 24 张表 / 约 900 行 SQL**，V001→V021 连续无缺号；全是 `CREATE TABLE`，含 CHECK、部分唯一索引、外键 | `code/backend/wms-app/src/main/resources/db/migration/` |
| 迁移执行时机 | 应用启动时执行（`spring.flyway.locations`），**构建期不校验迁移**；无 `flyway-maven-plugin` | `code/backend/wms-app/src/main/resources/application.yml:13-17` |
| 迁移回滚 | **零回滚脚本**。Flyway Community 不支持 undo，`AGENTS.md`「必须可回滚」无落地 | 全仓 grep `rollback|undo|回滚` 仅命中 README 文字 |
| Java 测试 | **0 个**。无 `src/test` 目录；3 个模块声明了 `spring-boot-starter-test` 依赖但无一行测试 | `code/backend/wms-app/pom.xml:49-53` 等 |
| 架构边界 | 「ArchUnit 拦截跨模块依赖」是**纸面约束**：全仓无 archunit 依赖、无 ArchUnit 测试 | 全仓 grep `ArchUnit|archunit` 仅命中 `AGENTS.md:38` 与一份调研报告 |
| 静态检查 | checkstyle / spotless / pmd / sonar / .editorconfig **全部不存在** | 全仓 grep 0 命中 |
| CI | `.github/workflows/ci.yml` **只有一个 `frontend` job**，后端零保护；与 `AGENTS.md:47`「编译、单测、静态检查全部通过才算完成」直接冲突 | `.github/workflows/ci.yml:14-48` |
| 已装未用 | Redis（`wms-auth/pom.xml:30-33`）、MyBatis-Plus、`spring-boot-starter-validation` 均零业务代码 | 全仓 grep `RedisTemplate` 0 命中；`@Valid` 0 命中 |
| 约束验证脚本 | `code/backend/tools/verify_constraints.py`（259 行、约 60 用例）需手工起 PG + 手工 `psql` 循环，**未接入 CI** | `code/backend/README.md:113-120` |
| 模块依赖方向 | Maven 已强制单向无环：`wms-common` ← `wms-auth` / `wms-master` ← `wms-app` | 各 `pom.xml` |

**正面事实**：注释密度高，每个非平凡决策都写了"为什么"（如 `code/backend/wms-auth/src/main/java/com/yunwms/auth/config/SecurityConfig.java:14-19` 解释为何必须显式关 formLogin）。`wms-master` 无 `src` 目录是**有意为之**（`openspec/changes/init-wms-foundation/design.md:46-50`：等真有代码时再建模块），不是遗漏。SQL 约束设计水平高。基础层是刻意写好的，不是占位空壳。

### 2.3 前端：monorepo 骨架成型，业务为零

| 项 | 事实 | 证据 |
| --- | --- | --- |
| 工程形态 | pnpm workspace 单仓：`apps/network-console`、`apps/owner-portal`、`packages/tokens`、`packages/request`、`packages/ui-web` | `code/frontend/pnpm-workspace.yaml` |
| 代码量 | `.vue` 18 个 + `.ts` 28 个，**约 2 830 行**（不含 dist） | `code/frontend/apps/*/src` |
| 业务实现 | **为零**。列表全是组件内硬编码数组；登录写死 `setToken('skeleton-token')`；`api/client.ts` 创建的 http 实例**从未被任何页面 import** | `apps/network-console/src/views/customer/index.vue:28-56`、`apps/network-console/src/views/login/index.vue:25-35`、`apps/*/src/api/client.ts` |
| 静态检查 | **eslint / prettier / stylelint 全部不存在**，无 lint 脚本，无 `.editorconfig` | 全仓 grep 0 命中 |
| 前端测试 | 全仓唯一测试文件是 `packages/tokens/scripts/gen-css.test.ts`（25 行、3 断言）；**8 个页面 0 测试**，无 vitest/playwright，无覆盖率 | `code/frontend/packages/tokens/scripts/gen-css.test.ts` |
| CI | 有 `typecheck → test → build`，但 `test` 实际只覆盖 tokens 一个包 | `.github/workflows/ci.yml:40-48` |
| UI 库 | 只有 `ant-design-vue@4.2.6` 一套，**无 element-plus 残留、无 `el-*` 用法**，符合门禁 ✅ | `code/frontend/pnpm-lock.yaml` |
| 令牌 | 四层齐全（primitive/semantic/density/shell），真源 `packages/tokens/src/primitives.ts`，生成物有防手改单测 ✅ | `code/frontend/packages/tokens/src/` |
| 重复代码 | 两个 app 的 `api/client.ts`、`api/notify.ts`、`AppBootstrap.vue` **完全重复**；`BasicLayout.vue` 近乎复制（225 vs 222 行） | `apps/network-console/src/api/` vs `apps/owner-portal/src/api/` |
| 菜单 | 前端**静态数组**，非后端树驱动、无递归、无权限映射 | `apps/network-console/src/layouts/BasicLayout.vue:19-25` |

### 2.4 规则与代码的不一致清单

这是本次调研最重要的发现之一：**规则写得越细、与代码的偏差越大，AI 放大偏差的能力就越强**。

| 规则条款 | 出处 | 代码现状 |
| --- | --- | --- |
| 「ArchUnit 拦截跨模块依赖」 | `AGENTS.md:38` | 无依赖、无测试、无检查 |
| 「编译、单测、静态检查全部通过才算完成」 | `AGENTS.md:47` | 后端静态检查环节不存在，CI 不跑后端 |
| 「一次迁移一个文件，必须可回滚」 | `AGENTS.md:54` | 无回滚脚本；Flyway Community 不支持 undo |
| 「菜单由后端树驱动，必须递归渲染」 | `AGENTS.md:63`、`.codebuddy/rules/frontend-ui/RULE.mdc:37-39` | 扁平静态数组，无递归组件、无转换层 |
| 「业务代码只引用 semantic 与 component 层」 | `docs/frontend.md:67` | 组件直接用 `--text-*`/`--leading-*`/`--weight-*`（`packages/ui-web/src/components/PageHeader.vue:30-31`） |
| 「通用组件放 `src/components/<模块>/`」 | `AGENTS.md:62` | `apps/*/src/components/` 只有一个扁平 `AppBootstrap.vue` |
| 「共享组件包只有两个：`ui-web` / `ui-pda`」 | `.codebuddy/rules/frontend-ui/RULE.mdc:21-24` | `packages/ui-pda` 不存在，规则超前于代码 |
| 「一次提交不超过 400 行」 | `AGENTS.md:22` | 无任何机器校验 |

反向缺口（代码有、规则没管）：两 app 大量重复代码无约束；`.env.development` 的 `VITE_USE_MOCK` / `VITE_APP_TITLE` **零消费方**（死配置，易误导后续开发以为有 mock 层）；i18n 只覆盖 3 组词条，页面文案大量硬编码中文。

### 2.5 一句话诊断

> 这套工程目前是**「规则完备、门禁缺席」**的状态。
> 规则质量高于平均水平，工程可执行性接近零。
> 因此"AI 写得对不对"完全依赖 AI 每次都恰好读懂并遵守了文字规则——这不是可持续假设。

---

## 3. 检索范围与方法（R2）

检索时间：2026-10-23。全部为公开检索，未使用二手转述的许可证结论作为最终依据。

| 检索词 | 目的 | 主要来源 |
| --- | --- | --- |
| `AI 生成代码 可维护性 风险 工程实践 2026 代码审查 瓶颈` | 定位 AI 编码在长期维护上的已知失败模式 | ACM TPC TechBrief（2026-04-30）、CCC "Beyond Code" 报告（2026-09-24）、Unico Connect《AI Code at Scale》 |
| `METR study AI coding agents productivity experienced developers open source repo` | 量化"AI 让人变快"这一前提是否成立 | METR 随机对照试验（2025-07-10，16 名资深 OSS 开发者） |
| `ArchUnit vs Spring Modulith module boundary enforcement Java maven 2026` | 模块边界自动守护方案 | ArchUnit 官方、Spring Modulith 官方、Taikai、sivalabs、gitee 架构文章 |
| `PostgreSQL migration linter squawk Flyway CI lint schema drift atlas` | 迁移安全与 schema 漂移防护 | Squawk 官网、Bytebase（迁移工具与 SQL review 工具横评）、Atlas `migrate lint` |
| `knip madge depcheck publint monorepo dead code circular dependency detection 2026` | 前端死代码、循环依赖、架构规则 | Knip 官网、dependency-cruiser、madge、rev-dep 工具横评 |
| `mutation testing PIT jacoco testcontainers contract testing pact java 2026 best practice` | 测试有效性而非覆盖率 | PIT、Testcontainers、Pact、JaCoCo、fail-fast 分层策略 |
| `spec-driven development github spec-kit BMAD-METHOD OpenSpec AGENTS.md context engineering AI coding rules` | 确认现有 spec 驱动流程是否需要更换 | stackness 横评、BMAD、Spec Kit、OpenSpec 官网 |
| `AI code review agent quality gate automated pull request reviewer 2026 github copilot code review limitations` | AI 评审工具的能力边界 | CodeRabbit / Qodo / Greptile / Copilot Code Review 对比与实测 |

已核对的关键外部事实（原文出处已记录在上表链接中）：

- **METR RCT**：16 名资深开源开发者在自己熟悉的仓库上完成任务，使用 AI 工具比不用**慢 19%**；事后他们自认为**快了 20%**。即"感觉更快"与"实际更快"方向相反。
- **CCC "Beyond Code" 报告（2026-09-24）**：84%–90% 的开发者已在使用 AI 编程工具，瓶颈从"写"转移到"验证与维护"。
- **Unico Connect《AI Code at Scale》**：AI 输出会沿袭仓库中已有的模式；**若仓库既有模式本身不一致或定义松散，AI 会放大这种不一致**。这条与本仓库 2.4 节的不一致清单完全对应。
- **《Your AI Agent Just Wrote a Migration That Will Lock Your Production Table》**：agent 在依赖不可见状态的后端工作（尤其迁移）上退化明显；而当评审队列被 AI 产出灌满时，评审者会在数十秒内放行一个"看起来没问题"的三行 SQL 改动。本仓库当前状态（24 表零代码 + 无 backend CI + 无迁移回滚）正落在这个失效模式上。

---

## 4. AI 写代码与人类写代码的差别，以及对策

这是本篇的方法论基础。**差别不是"AI 更弱"，而是失效模式不同，因此防线也要不同。**

| 维度 | 人类工程师 | AI 编码代理 | 对策（工程手段） |
| --- | --- | --- | --- |
| **上下文来源** | 脑子里的架构意图 + 口头传承 | 只读它这次打开的文件 | 决策必须落成**可执行门禁**，而不是文档或口头约定 |
| **违规倾向** | 知道红线，也知道红线背后的原因 | 会遵守读到的**文字**，但对没有检查的条款会静默漂移 | 每条规则配一个 lint / 测试 / CI job；无检查的规则等于不存在 |
| **产出节奏** | 慢，但一次一件事 | 快，且倾向一次铺开大量文件 | 保留 400 行/提交，并用工具机检 diff 体量 |
| **重复** | 会主动抽象、抽不动会喊疼 | 倾向复制粘贴，且能复制得很像 | 重复检测（jscpd / knip / Sonar duplication）+ 共享包约束 |
| **典型错误** | 逻辑误解、漏边界 | **幻觉 API**、造出"看起来合理但库里不存在"的行为与依赖 | 编译 + 类型 + 依赖解析 + 集成测试 + 契约测试；依赖版本必须锁 |
| **对自身产出的信心** | 知道自己哪里没测 | **会自认为已经测过**（METR 的 20% 错觉） | 结论只能由机器给：CI 绿灯才算验证过，AI 的自述不算 |
| **跨会话记忆** | 渐进遗忘，但有笔记与同事 | 稳定遗忘，跨会话丢失决策 | spec + `AGENTS.md` + rules + 调研文档；重要决策落文档不落对话 |
| **一致性** | 受项目历史影响 | **倾向按规则文本"重新发明"已有实现**（重复、层级越界） | 一致性检查（重复检测 + 层级 lint + 死代码检测） |
| **回归倾向** | 会主动想"这会影响到哪" | **改动的影响面被低估**，尤其在看不见运行状态的层（DB、异步） | 迁移 lint + 迁移从零重建 + 变更影响面测试 |
| **规模上限** | 受体力与注意力自然限制 | 受上下文窗口限制，**会一口气生成 5 000 行无法维护的代码** | 强制小步：单 change 单切片、单提交 400 行、每条任务 2 小时内可验证 |
| **能超过人类的地方** | — | 样板代码、跨语言迁移、批量重构、测试脚手架、文档同步 | **应当放大的场景**：批量补测试、批量对齐样式、批量迁移 |
| **不该指望的地方** | — | 业务口径判断、异常场景穷举、"这条规则到底合不合理" | 口径必须由人给：spec 里写清"谁在什么时机因为什么" |

**一句话概括**：人类写代码时，"规范"靠习惯维持；AI 写代码时，**规范只能靠 CI 维持**。本仓库当前所有规范都是"习惯型规范"，所以它们对 AI 全部无效。

---

## 5. 候选方案比较（R3）

比较维度：功能覆盖度 / 活跃度 / 许可证 / 依赖与可维护性 / 与现有技术栈兼容性 / 二次开发成本。**星标数不作为判据。** 许可证一栏，凡本次未读 LICENSE 原文的，一律标注"未验证"，进入实施前须复核。

### 5.1 模块边界与架构守护

| 候选 | 覆盖度 | 活跃度 | 许可证 | 依赖与可维护性 | 技术栈兼容 | 二次开发成本 | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **ArchUnit** | 自定义规则，表达力最强；能直接表达"模块间不得直接依赖 mapper""实体不得 public" | TNG 维护；最近发版 2025-05（1.4.1），发版节奏偏慢但工具已成熟 | **Apache-2.0**（已从 POM `<licenses>` 原文核实） | 单 jar + JUnit 5，零外部服务 | 与 JUnit 5 / Maven 天然契合，直接进 `mvn test` | 低：写一组 ArchTest 类即可 | **推荐（首选）** |
| **Spring Modulith** | 模块验证 + 应用模块测试 + 领域事件 + 模块文档，能力最完整 | 活跃（Spring 官方） | Apache-2.0 | 引入 `@ApplicationModule`、`@NamedInterface` 注解与运行时校验 | 与 Spring Boot 契合最好 | **高**：需要重构包结构、定义模块边界、事件模型；与现有 `wms-*` Maven 模块的映射关系需重新设计 | 备选（需用户确认后单独开 change） |
| **Taikai**（ArchUnit 生态的 Spring Boot 预定义规则集） | 开箱即用的 Boot 规范规则 + ArchUnit 自定义 | 活跃度**未验证** | Apache-2.0（未验证） | 轻量 | 直接可用 | 低 | 可作为 ArchUnit 的补充，不单独引入 |
| **jQAssistant** | 架构文档与规则，SQL 驱动 | 活跃度未验证 | Apache-2.0（未验证） | 需 Neo4j，运维成本高 | 与 Maven 集成 | 中 | 落选：引入 Neo4j 对单人项目过重 |
| **Deptrac**（PHP 生态） | 层级规则 | — | — | — | — | — | 落选：PHP 专属，Java 侧有 ArchUnit |
| **module-boundary 纯靠 Maven 依赖**（现状） | 只能约束模块级，约束不到包级与类级 | — | — | 零成本 | 已生效（单向无环） | 零 | **保留为第一层**，但不充分：无法阻止 `wms-outbound` 直接调 `wms-inventory` 的内部类（若未来建了包级依赖） |

### 5.2 代码风格与静态检查

| 候选 | 覆盖度 | 活跃度 | 许可证 | 可维护性 | 兼容性 | 开发成本 | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **Spotless**（Maven 插件） | 格式统一 + 可挂 checkstyle/pmd/错误prone 规则 | 活跃 | Apache-2.0 | 配置集中，AI 可直接改配置而非改代码 | 与 Maven 契合 | 低 | **推荐（Java 侧格式层）** |
| **Checkstyle**（经 Spotless 挂载） | 内置规则集可判"AI 最容易漂移"的点：命名、魔法数、缺失 javadoc、行长 | **非常活跃**，2026-10-23 仍有推送 | **LGPL-2.1**（已从仓库元数据核实；**非 Apache-2.0**，见第 11 章说明） | 规则 XML 对 AI 友好（可读、可改） | Maven 原生 | 中（规则需调优，避免误报刷屏） | **推荐（Java 侧规则层）** |
| **Error Prone**（经 Spotless/编译器） | 能查空指针、误用 API 等真 bug | 活跃 | Apache-2.0 | 误报多，需 suppress 注解管理 | 需 `-Xplugin` 配置 | 中高 | **暂缓**：先让 Spotless+Checkstyle 跑起来再考虑 |
| **SonarQube**（自建） | 质量看板 + 重复检测 + 安全 | 活跃 | **LGPL-3.0**（社区版），商用分发需注意 | 需常驻服务 | 与 IDE/CI 集成好 | 中高（需部署服务） | 落选为**自建**方案：单人项目维护服务成本不划算。**但开源场景另有解——见 13.5 节 SonarCloud Free OSS Plan（0 元 SaaS）** |
| **jscpd**（重复检测） | 复制粘贴检测 | 活跃度未验证 | MIT（未验证） | 单二进制/CLI，无服务 | 语言无关 | 低 | **推荐（可选，低成本）** |

### 5.3 数据库迁移安全

| 候选 | 覆盖度 | 活跃度 | 许可证 | 可维护性 | 兼容性 | 开发成本 | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **Squawk**（npm 包名 `squawk-cli`，**不是 `squawk`**） | Postgres 迁移 lint：禁 `DROP COLUMN`、禁不加默认的 `NOT NULL`、禁危险类型变更等，规则以"防停机"为目标 | 非常活跃（仓库 `sbdchd/squawk`，2026-10-23 有推送，1229 star） | **Apache-2.0 OR MIT**（npm 元数据已核实） | npm CLI，无服务依赖；可接 GitHub Actions | 只吃 Postgres SQL，**与 Flyway 脚本格式兼容**（同为 .sql） | 低 | **推荐（首选）** |
| **Atlas `migrate lint`** | 50+ 规则，含锁表风险分析 | 活跃（ariga） | Apache-2.0（Atlas OSS，未验证） | Go 二进制，可容器化 | **与 Flyway 不直接兼容**——需改用 Atlas 自己的迁移目录管理，属于换工具 | 高 | 落选：当前 21 个脚本已是 Flyway 规范，换管理器的收益低于成本 |
| **Bytebase**（开源版） | SQL review + 迁移安全 + 审批流 | 活跃 | 开源版部分 Apache-2.0（未验证） | 需部署服务 + 接管迁移流程 | 与现有 Flyway 流程冲突 | 高 | 落选：单人项目引入审批流平台过重 |
| **CI 里用临时 PG 容器从零跑通全部迁移** | 验证"迁移可重建"，是最基础也最常被跳过的一步 | — | 无额外依赖 | 需 CI 有 Docker（GitHub ubuntu runner 有） | 天然兼容 Flyway | 低（配一个 job + Testcontainers 或服务容器） | **推荐（与 Squawk 配套）** |
| **回滚脚本** | Flyway Community 不支持 undo；可行替代是**前滚修复脚本**（forward-fix） | — | — | 需人工判断"何时能安全删列" | 兼容 | 中 | **推荐用"前滚修复 + 迁移 lint 拦截破坏性变更"替代**，需用户确认后写入 `AGENTS.md` |

### 5.4 测试有效性

| 候选 | 覆盖度 | 活跃度 | 许可证 | 可维护性 | 兼容性 | 开发成本 | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **JUnit 5 + Spring Boot Test** | 基础单测 | — | — | — | 已在依赖里 | 低 | **前置条件**：0 测试状态下先补测试骨架 |
| **Testcontainers（PostgreSQL）** | 用真实 PG 跑集成测试，避免 H2 与 PG 方言差异 | 最近发版 2025-06（1.21.3），节奏慢但稳定 | MIT（已核实） | 需 Docker；CI runner 有 | 与 Flyway + PG 强绑定 | 中 | **推荐**：本项目 24 张表全是 PG 特性（部分唯一索引、CHECK），H2 测不了 |
| **PIT（mutation testing）** | 度量"测试是否真的能抓 bug"，比覆盖率有意义 | 最近发版 2025-04（1.19.1），节奏慢 | Apache-2.0（已核实） | 跑得慢，适合夜间任务 | Maven 原生 | 中 | **推荐（限定在关键状态机/金额/库存口径类代码）**，不全量开 |
| **Pact**（消费者驱动契约测试） | 跨模块 API 契约 | 活跃 | MIT（未验证） | 需维护 pact 文件 | 与 `wms-*` 模块拆分方向匹配 | 中 | **暂缓**：等模块间真有交互再上 |
| **JaCoCo** | 覆盖率看板 | 活跃 | EPL-2.0 | 仅报告，不判失败 | Maven 原生 | 低 | **推荐但只作参考值**，不设为门禁（覆盖率当门禁会催生无意义测试） |
| **property-based testing（jqwik）** | 穷举异常组合，正好对应 spec 要求的"异常与逆向场景" | 活跃度未验证 | Apache-2.0（未验证） | 需学习生成器模型 | Java 原生 | 中 | 备选：适合编码规则、库存扣减、编码生成器这类不变量密集的逻辑 |

### 5.5 前端依赖、死代码与架构规则

| 候选 | 覆盖度 | 活跃度 | 许可证 | 可维护性 | 兼容性 | 开发成本 | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **ESLint + eslint-plugin-vue** | 唯一被普遍认可的前端静态检查底座；能承载本项目全部前端规则（禁 primitive 直用、禁跨 app import、禁 `el-*`、菜单禁层级判断） | 活跃 | MIT（eslint-plugin-vue MIT） | 规则需长期维护，但规则就是"项目宪法"，值 | Vue 3 官方推荐 | 低 | **推荐（必备）** |
| **Prettier** | 格式统一，消除 AI 产出的格式漂移 | 活跃 | MIT | 极低 | 与 ESLint 可共存 | 极低 | **推荐（必备）** |
| **dependency-cruiser** | 把架构写成配置，违规即 CI 失败；支持 Vue；可查循环依赖 | 活跃 | MIT | 配置文件对 AI 友好 | TS/Vue 原生 | 低 | **推荐**：正好对应"菜单必须递归""禁止跨 app 引用""禁止 ui-pda/ui-web 跨端" |
| **madge** | 快速查循环依赖 | 活跃 | MIT | 极低 | TS | 极低 | **推荐（轻量补充）**，或用 dependency-cruiser 覆盖 |
| **Knip** | 找未用文件/依赖/导出；**正好能抓出"http 实例从未被调用"这类死接线** | 非常活跃（2026-10-23 有推送，12504 star） | **ISC**（已从 npm 元数据核实；非 MIT） | 配置需按 workspace 调 | pnpm workspace 支持好 | 低中 | **推荐（高价值）** |
| **publint** | 包导出正确性（`packages/*` 对外 exports） | 活跃 | MIT | 极低 | npm 包 | 极低 | **推荐（低成本）** |
| **oxlint / Biome** | 更快的 lint / 一体化工具链 | 活跃 | MIT / Apache-2.0（未验证） | 与 ESLint 生态有重叠 | — | 低 | 备选：可作为 ESLint 的性能替代，但不建议同时引入两套 |
| **Vitest + @vue/test-utils** | 组件与逻辑单测 | 活跃 | MIT | 与 Vite 同源，无额外配置成本 | 与现有 vite 6 一致 | 低 | **推荐（必备）**：现有 8 个页面 0 测试 |
| **Playwright** | 端到端，验证"登录 → 建客户 → 建商品"主链路 | 活跃 | Apache-2.0 | 需浏览器下载，CI 需缓存 | — | 中 | **推荐（限定主链路 1–2 条）** |
| **MSW** | 网络层 mock | 活跃 | MIT | — | 与 axios 拦截器配合好 | 低 | 备选：等后端接口稳定后再评估 |

### 5.6 AI 代码评审与安全扫描

| 候选 | 覆盖度 | 活跃度 | 许可证/成本 | 兼容性 | 开发成本 | 结论 |
| --- | --- | --- | --- | --- | --- | --- |
| **CodeRabbit** | PR 级评审 | 活跃 | 商业 SaaS，有免费额度（超限需付费，违反零预算） | GitHub 原生 | 低 | 落选为主方案：付费依赖 + 只看 diff、看不到全库上下文（正是本项目跨模块耦合问题的盲区） |
| **GitHub Copilot Code Review** | PR 级评审 | 活跃 | 商业（已付费团队工具） | GitHub 原生 | 低 | 落选：同上，且与已有 agent 工具重叠 |
| **Semgrep**（OSS） | 规则化安全与质量扫描，可写自定义规则 | 活跃 | **LGPL-2.1**（社区版，未验证） | CLI，可进 CI | 中 | 备选：需要自定义规则时价值高，先不引入 |
| **Trivy / Grype / OSV-Scanner** | 依赖与镜像 CVE 扫描 | 活跃（Trivy 2026-10-23 有推送，38661 star） | **Apache-2.0**（Trivy 已核实） | CLI，进 CI | 低 | **推荐（低成本）**：AGENTS.md 要求依赖准入确认维护时间，这项能自动回答"最近是否还在维护/有无漏洞" |
| **OSV-Scanner + Dependabot** | 依赖版本与漏洞跟踪 | 活跃 | MIT | GitHub 原生 | 极低 | **推荐**：Dependabot（MIT）可用；**Renovate 是 AGPL-3.0，按本仓库许可证红线直接排除** |

### 5.7 候选总表（按落地优先级）

| 优先级 | 能力 | 工具 | 许可证 | 主要作用 |
| --- | --- | --- | --- | --- |
| P0 | 版本控制兜底 | `git add code/backend` + 分支纪律 | — | 防丢 |
| P0 | 后端进 CI | Maven `verify` job | — | 编译/单测入 CI |
| P0 | 迁移可重建 | CI 临时 PG 跑通 21 个迁移 | — | 防迁移腐化 |
| P1 | 架构边界 | ArchUnit | Apache-2.0 | 把 AGENTS.md 变成可执行 |
| P1 | Java 风格与规则 | Spotless + Checkstyle | Apache-2.0 | 消除 AI 风格漂移 |
| P1 | 前端 lint/format | ESLint + eslint-plugin-vue + Prettier | MIT | 前端宪法 |
| P1 | 前端架构/死代码 | dependency-cruiser + Knip | MIT | 抓重复、越界、死接线 |
| P1 | 前端测试底座 | Vitest + @vue/test-utils | MIT | 补上 0 测试 |
| P1 | 迁移安全 | Squawk（LICENSE 待复核） | 未验证 | 拦截破坏性变更 |
| P1 | 依赖安全 | Trivy / OSV-Scanner + Dependabot | Apache-2.0 / MIT | 依赖准入自动化 |
| P2 | 架构模块化 | Spring Modulith（待确认） | Apache-2.0 | 事件与模块验证 |
| P2 | 测试有效性 | PIT + Testcontainers | Apache-2.0 / MIT | 测「测试是否有效」 |
| P2 | 重复检测 | jscpd | MIT（待复核） | 抓跨 app 复制粘贴 |
| P2 | 契约测试 | Pact（待复核） | MIT | 跨模块契约 |

---

## 6. 结论（R4）

### 6.1 结论：**基于现有项目改造**

不是"直接复用"（没有一个开源项目能同时解决"把 yunWMS 自己的规则变成 CI 门禁"这件事——ArchUnit / Squawk / ESLint / Knip 都是通用工具，需要按本项目规则逐条配置，不存在开箱即用的 WMS 级方案），也不是"自行实现"（所有需要的检查能力都有成熟开源实现，自己写 lint 规则是纯浪费）。

具体形态：**保留现有 spec 驱动流程与模块划分，补齐"规则 → 可执行检查"的映射层**，并按 P0 → P1 → P2 分三个 change 推进。

### 6.2 为什么现有流程（OpenSpec）要保留

检索确认：截至 2026-10，spec 驱动开发（SDD）生态已相当成熟——OpenSpec（MIT，38 工具支持）、GitHub Spec Kit、BMAD-METHOD（6.x）、AWS Kiro 并存。OpenSpec 已被行业评测列为"已有 `AGENTS.md` 场景下的推荐选项"。本仓库 `openspec/config.yaml` 的 rules 段（正/异常/逆向场景、迁移兼容策略、任务粒度）质量高于通用模板。**换框架的收益为负**，因此：

- 保留 OpenSpec 作为流程主干；
- **把 `openspec/config.yaml` 的 rules 段从"提示"升级为"检查项"**——每条 rules 都要有对应的 lint / 测试 / CI 步骤；
- 保留 `AGENTS.md` 与 `.codebuddy/rules/` 作为**上下文供给**（给 AI 读），但不再把它们当作**质量保证**（那由 CI 负责）。这是本篇最核心的一条分工原则。

### 6.3 被否候选及落选原因

| 被否 | 落选原因 |
| --- | --- |
| Spring Modulith（现阶段） | 能力最全，但要重构包结构并重新定义模块边界，与"一个 change 一个垂直切片"的当前节奏冲突。**不是不做，是不在 P0/P1 做**，等 `wms-inbound` / `wms-inventory` 等模块真正建立、模块间交互出现后再单开 change 评估 |
| Atlas 替换 Flyway | 与现有 21 个 Flyway 脚本不兼容，等于换迁移管理体系；收益（锁表分析）Squawk 能覆盖大半 |
| Bytebase | 需部署服务并接管迁移流程与审批流，单人项目运维成本高于收益 |
| SonarQube 社区版 | 需常驻服务；LGPL 在分发场景需另行确认；重复检测这一项用 jscpd 替代即可 |
| CodeRabbit / Copilot Code Review | 付费或与现有工具重叠；且仅看 diff，看不到跨模块耦合这类全库级问题——而这正是本项目的主要风险面 |
| Renovate | **AGPL-3.0，触犯 `AGENTS.md` 许可证红线**，直接排除；改用 Dependabot（MIT） |
| jQAssistant | 需 Neo4j，单人项目过重 |
| Deptrac | PHP 专属 |
| ESLint + Biome/oxlint 并存 | 两套 lint 规则会互相打架，选一套 |

---

## 7. 分阶段路线

> 每个阶段独立成 change，遵循 `AGENTS.md` 的分支、提交与归档纪律。
> **P0 为止血项，不涉及任何新依赖，可立即执行。**

### 7.1 P0：止血（建议 1 天内，不新增依赖）

| # | 动作 | 涉及文件 | 验证方式 |
| --- | --- | --- | --- |
| P0-1 | **把 `code/backend/` 纳入 git** | `code/backend/**`（41 个文件） | `git ls-files code/backend \| wc -l` = 41；`git status` 干净 |
| P0-2 | 把未跟踪的研究与需求资产纳入 git | `docs/research/**`、`docs/prd/**`、`docs/standards/**`、平台清单 xlsx | 同上 |
| P0-3 | **CI 增加 backend job**：`./mvnw -B verify`（含 `test` 阶段，暂为空测试集） | `.github/workflows/ci.yml` | 本地 `./mvnw -o verify` 通过；CI 出现 backend job 绿灯 |
| P0-4 | CI 增加**迁移从零重建** job：起 PG 服务容器 → `./mvnw -B flyway:migrate` 或启动应用 → 校验 24 张表存在 | `.github/workflows/ci.yml` | CI 日志显示 `migrate` 成功且表数量 = 24 |
| P0-5 | 建 `.editorconfig`（全仓统一缩进/换行/末尾换行） | `.editorconfig` | `git ls-files --eol` 无冲突；本地编辑器一致 |
| P0-6 | 归档 `migrate-network-console` 与 `migrate-owner-portal`，删除其目录并把 spec 增量并入 `openspec/specs/` | `openspec/changes/archive/**`、`openspec/specs/**` | `openspec validate --strict` 通过 |
| P0-7 | 补 `packages/tokens` 的 `typecheck` 脚本，或明确其不参与 typecheck | `code/frontend/packages/tokens/package.json` | `pnpm -r typecheck` 覆盖预期包 |
| P0-8 | 清理死配置 `VITE_USE_MOCK` / `VITE_APP_TITLE`，或补上消费方 | `code/frontend/apps/*/.env.development` | Knip 无 "unused env" 告警（P1 引入后验证） |

**P0 说明**：`flyway-maven-plugin` 需新增一个 Maven 插件依赖（Apache-2.0，Flyway 官方），属"引入新依赖"，按 `AGENTS.md` 需在 change 中做准入确认；或者改用「CI 起 PG → 启动应用 → 应用启动时 Flyway 自动执行」的方式，避免引入插件。**推荐后者**，零新依赖。

### 7.2 P1：把规则变成门禁（建议 1–2 周，按能力分 2–3 个 change）

| # | 能力 | 对应现有规则 | 工具 | 落地位置 |
| --- | --- | --- | --- | --- |
| P1-1 | Java 架构边界 | `AGENTS.md:38`「禁止跨模块直接依赖」 | ArchUnit（Apache-2.0） | 新增 `code/backend/wms-app/src/test/java/.../ArchitectureTest.java` |
| P1-2 | Java 风格与规则 | `AGENTS.md` 命名、JavaDoc、魔法数 | Spotless + Checkstyle（均 Apache-2.0） | `code/backend/pom.xml` |
| P1-3 | 前端 lint/format | `.codebuddy/rules/frontend-ui` 全部条款 | ESLint + eslint-plugin-vue + Prettier（均 MIT） | `code/frontend/eslint.config.js`、`code/frontend/.prettierrc` |
| P1-4 | 前端架构与死代码 | 「禁跨 app 引用」「禁跨端引用 ui-pda/ui-web」「菜单禁层级判断」 | dependency-cruiser + Knip（均 MIT） | `code/frontend/.dependency-cruiser.js`、`knip.json` |
| P1-5 | 前端测试底座 | `AGENTS.md:47`「单测通过才算完成」 | Vitest + @vue/test-utils（MIT） | `code/frontend/apps/*/vitest.config.ts` |
| P1-6 | 迁移安全 | `AGENTS.md:53-54`「先加字段、再写代码、最后删旧字段」 | Squawk（LICENSE 待复核） | `.github/workflows/ci.yml` 新增 lint 步骤 |
| P1-7 | 依赖安全与准入 | `AGENTS.md:72-74`「依赖准入三项确认」 | Trivy 或 OSV-Scanner + Dependabot | CI + `.github/dependabot.yml` |
| P1-8 | 前端重复检测 | 现状：两 app 完全重复的 3 个文件 + 近乎重复的 `BasicLayout.vue` | jscpd（待复核） | CI 步骤 |

**P1 的关键实现要求**：每条 ESLint / ArchUnit / Checkstyle 规则都要**在注释或配置文件里写明它对应哪条 `AGENTS.md` / rules 条款**。理由：AI 修不了它不理解的规则，只能绕过；而它能理解「这条规则来自 AGENTS.md 第 N 条」。

### 7.3 P2：结构性提升（建议 1 个月后，视 P1 结果决定）

| # | 候选 | 前置条件 |
| --- | --- | --- |
| P2-1 | Spring Modulith 或补齐 ArchUnit 模块规则（域事件机制） | `wms-inbound` / `wms-inventory` 等模块真实建立后 |
| P2-2 | Testcontainers（PostgreSQL）+ PIT 变异测试（限关键状态机） | P1-5 已有基本测试 |
| P2-3 | Pact 契约测试 | 跨模块交互真正出现 |
| P2-4 | Playwright 主链路 e2e（登录 → 建货主 → 建商品） | 后端主链路接口可用后 |
| P2-5 | 把门禁收敛成一个 `code/scripts/verify.sh`，AI 会话收尾时必须执行 | P0/P1 完成后 |

**P2-5 是本篇认为最容易被低估的一项**：它把"AI 说它验证过了"变成"AI 必须运行验证脚本并把输出贴出来"。这是对 METR 那个 20% 主观错觉最直接的对冲。

---

## 8. 风险与待确认（R5）

### 8.1 主要风险

| 风险 | 说明 | 缓解 |
| --- | --- | --- |
| **门禁一次上太多导致 AI 卡死** | 规则太严 → 每个 change 都撞墙 → 绕过或加豁免注释（这正是 `RULE.mdc` 明确禁止的） | 规则分批上，每批先跑一周看误报率；禁止豁免注释，例外走 `openspec` 记录 |
| **Checkstyle/Spotless 与 AI 产出冲突刷屏** | 格式类检查一旦误报率高，AI 会倾向无脑改格式刷绿，污染 diff | 只在 pre-commit/CI 跑，且配置集中在一个文件 |
| **lint 规则与 `AGENTS.md` 文字漂移** | 规则改了，lint 没改 → 两套真相 | 每条 lint 规则注明来源条款；`AGENTS.md` 增加「规则 ↔ 检查项」对照表 |
| **测试写成"迎合断言"的假测试** | `AGENTS.md` 已禁止改断言让测试通过，但 AI 仍可能写出无意义的断言 | 关键路径用 PIT 变异测试兜底；抽查测试是否真的会因为业务改错而失败 |
| **覆盖率崇拜** | 覆盖率当门禁 → AI 写出无价值测试凑数 | 覆盖率只作参考值，不设门禁 |
| **引入依赖破坏许可证红线** | 逐项核实后：Checkstyle 实为 **LGPL-2.1**（非 Apache-2.0）；**Renovate 是 AGPL-3.0，已排除**；Squawk 包名易错（应为 `squawk-cli`） | 第 11 章准入清单已逐项核实并记录；每个新依赖在 change 的 `design.md` 登记许可证原文链接、最近推送时间、是否可被现有依赖替代 |
| **CI 额度与时长** | 加 backend job + PG 容器 + 迁移重建会增加 CI 时间与额度 | 迁移重建 job 只在 push 到 `main` 与 PR 上跑；PIT 放夜间定时任务 |
| **单人项目无交叉复核** | AI 产出 + AI 自评 → 无人发现系统性误解 | 关键 change 保留人工走查任务（`init-wms-foundation` 已有 `5.4 人工走查` 的先例） |

### 8.2 必须由用户确认的事项（未闭环前不进入 P1 实现）

1. ~~**CI 是否允许新增 job**（涉及 GitHub Actions 免费额度与总时长上限）~~ → **已核实（2026-10-23）：本仓库为公开仓库，GitHub Actions 对标准托管运行器 0 元、分钟数不限量**，额度不构成约束，无需再确认。改为确认第 13.3 节的**分级执行方案**是否接受（PR 只跑 L1、重检查移至 main 与夜间）。
2. **是否接受引入 P1 的开源依赖**：清单与逐项核实结果见**第 11 章**，逐条批准入口见**第 14 章评审清单**。许可证均已读原文核实；其中 Checkstyle 为 LGPL-2.1（构建期使用、不分发），需确认接受。
3. **迁移"可回滚"的定义**：Flyway Community 不支持 undo。是改为「前滚修复（forward-fix）+ lint 拦截破坏性变更」，还是引入付费/额外工具（如 Atlas、Bytebase）？
4. **分支纪律是否严格执行**：当前所有工作直接在 `main`，是否从下一个 change 起强制 `change/<id>` 分支 + PR？
5. **P2 的 Spring Modulith 是否进入排期**：它涉及包结构重构与模块边界重定义，成本远高于 P1。
6. **`init-wms-foundation` 的 52 项任务全未勾选**：是否确认"后端 0 业务代码"是当前真实进度（而非勾选遗漏）？
7. **是否同意把 `openspec/config.yaml` 的 rules 段升级为"检查项清单"**（每条 rules 必须有对应 CI 检查）？

---

## 9. 附录：本仓库"不成立假设"清单

供后续 AI 会话直接使用，避免重复踩坑（每条均已在本篇正文给出证据）：

| 常见假设 | 本仓库实况 |
| --- | --- |
| 「有 pom.xml 就有工程」 | 只值 299 行 Java、7 个类、0 个业务接口 |
| 「配了 `spring-boot-starter-test` 就有测试」 | 3 个模块配了依赖，**0 个测试文件**，且 CI 不跑后端 |
| 「ArchUnit 会拦截跨模块依赖」 | **无依赖、无测试**，纯纸面约束 |
| 「CI 会跑 `mvn verify`」 | CI **只有 frontend job** |
| 「Flyway 迁移有回滚脚本」 | **一个都没有** |
| 「`mvn flyway:migrate` 可用」 | 插件未配置，命令跑不通（`init-wms-foundation/tasks.md:10` 却这么写） |
| 「wms-master 里有货主/SKU 代码」 | 该模块**连 `src` 目录都没有**，产出 1.61 KB 空 jar |
| 「auth 依赖 Redis 就有会话实现」 | 依赖引了，**零 Redis 代码** |
| 「有 checkstyle/spotless/sonar 统一风格」 | **一个都没有** |
| 「target/ 里的 .class 是工程的一部分」 | 是本机构建产物，已被 `.gitignore` 覆盖 |
| 「多租户 tenant_id 双维度已落库」 | 21 个迁移脚本中 `tenant_id` **0 命中**（`openspec/config.yaml:14` 声明了） |
| 「后端代码在版本库里」 | **`code/backend/` 整个目录 untracked** |
| 「变更都在 change 分支上」 | `git branch` **只有 main** |
| 「已完成 change 已归档」 | `openspec/changes/archive/` **为空** |

---

## 10. 附：可直接采用的 CI 骨架（示意，非最终实现）

> 以下仅示意结构，落地时须走独立 change 并按 `AGENTS.md` 做依赖准入。

```yaml
jobs:
  backend:            # 现状缺失
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - run: ./mvnw -B verify        # 编译 + 单测 + ArchUnit + Spotless/Checkstyle

  db-migration:       # 现状缺失
    runs-on: ubuntu-latest
    services:
      postgres: { image: postgres:16, ... }
    steps:
      - run: ./mvnw -B spring-boot:run   # 启动即触发 Flyway，校验 24 表

  frontend:
    # 已有，追加：
    - run: pnpm -r lint        # ESLint + Prettier
    - run: pnpm -r test        # 已有（tokens），扩展到 Vitest
    - run: pnpm -r build
    - run: pnpm -r knip        # 死代码
```

---

## 11. 依赖准入清单（已逐项核实）

**核实方式**：GitHub REST API 仓库元数据（`license.spdx_id` / `pushed_at` / `archived`）、npm registry `latest` 元数据、Maven Central Search API、以及构件 POM 的 `<licenses>` 段原文。
**核实时间**：2026-10-23。本章数据不采信二手描述，凡标「已核实」均有上述来源。

| # | 工具 | 引入形式 | 当前版本 | 许可证（核实结果） | 上游仓库 | 最近推送 | 可否被现有依赖替代 | 准入结论 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | ArchUnit | Maven `com.tngtech.archunit:archunit` + `archunit-junit5` | 1.4.1（2025-05-07） | **Apache-2.0**（POM `<licenses>` 原文） | `TNG/ArchUnit` | — | 否，无等价物 | **批准** |
| 2 | Spotless | Maven 插件 `com.diffplug.spotless:spotless-maven-plugin` | 2.44.5（2025-05-27） | **Apache-2.0** | `diffplug/spotless` | 2026-10-21 | 否 | **批准** |
| 3 | Checkstyle | 经 Spotless 挂载 `com.puppycrawl.tools:checkstyle` | 10.26.1 | **LGPL-2.1**（非 Apache-2.0） | `checkstyle/checkstyle` | 2026-10-23 | 部分（Spotless 内置格式化可替代格式部分，不能替代规则部分） | **有条件批准**（见 11.1-1） |
| 4 | ESLint | npm `eslint` | 10.13.0 | **MIT** | `eslint/eslint` | 2026-10-23 | 否 | **批准** |
| 5 | eslint-plugin-vue | npm `eslint-plugin-vue` | 10.11.1 | **MIT** | `vuejs/eslint-plugin-vue` | 2026-10-23 | 否 | **批准** |
| 6 | Prettier | npm `prettier` | 3.9.10 | **MIT** | `prettier/prettier` | 2026-10-23 | 否 | **批准** |
| 7 | dependency-cruiser | npm `dependency-cruiser` | 18.7.0 | **MIT** | `sverweij/dependency-cruiser` | 2026-10-19 | 部分（madge 只查环，不查层级规则） | **批准** |
| 8 | Knip | npm `knip` | 6.42.1 | **ISC**（非 MIT） | `webpro-nl/knip` | 2026-10-23 | 否 | **批准** |
| 9 | Vitest | npm `vitest` | 5.0.5 | **MIT** | `vitest-dev/vitest` | 2026-10-23 | 否（现有 `node:test` 只够 tokens 包用） | **批准** |
| 10 | @vue/test-utils | npm `@vue/test-utils` | 2.5.1 | **MIT** | `vuejs/test-utils` | 2026-10-23 | 否 | **批准** |
| 11 | Squawk | npm **`squawk-cli`** | 2.70.0 | **Apache-2.0 OR MIT** | `sbdchd/squawk` | 2026-10-23 | 否 | **批准（包名必须是 `squawk-cli`，见 11.1-2）** |
| 12 | Trivy | GitHub Action / 本地二进制 | — | **Apache-2.0** | `aquasecurity/trivy` | 2026-10-23 | 可由 OSV-Scanner 替代 | **批准（与 13 二选一）** |
| 13 | OSV-Scanner | GitHub Action / 二进制 | — | Apache-2.0（未逐项取仓库元数据） | `google/osv-scanner` | — | 可由 Trivy 替代 | **与 12 二选一** |
| 14 | Dependabot | GitHub 平台内置功能 | — | 平台功能，无独立依赖 | — | — | 可由 Renovate 替代，**但 Renovate 是 AGPL-3.0，触红线，排除** | **批准** |
| 15 | Testcontainers | Maven `org.testcontainers:testcontainers` | 1.21.3（2025-06-26） | **MIT** | `testcontainers/testcontainers-java` | — | 否（PG 特性无法用 H2 替代） | **P2 阶段再批** |
| 16 | PIT | Maven `org.pitest:pitest` | 1.19.1（2025-04-07） | **Apache-2.0** | `pitest/pitest` | — | 否 | **P2 阶段再批** |
| 17 | jscpd | npm `jscpd` | 5.4.2 | **MIT** | `kucherenko/jscpd` | — | 部分（dependency-cruiser 有重复检测） | **P2 阶段再批** |
| 18 | Spring Modulith | Maven `org.springframework.modulith:*` | — | **Apache-2.0** | `spring-projects/spring-modulith` | 2026-10-23 | ArchUnit 可覆盖大部分边界约束 | **暂不批**（需单开 change 论证） |

### 11.1 三条必须写进 change 的说明

**(1) Checkstyle 是 LGPL-2.1，不是 Apache-2.0。**
`AGENTS.md:39` 的红线是 GPL / AGPL / SSPL，LGPL-2.1 不在其中。需明确记录：Checkstyle 在本项目中**仅作为构建期静态检查工具运行，不随产品二进制分发**，不触发 LGPL 的「随产品分发需提供源码」义务。若将来把 Checkstyle 打进交付物，许可证风险需重新评估。
**处置建议**：优先用 Spotless 内置的格式化规则兜住格式层，只把少量真正有价值的 Checkstyle 规则（命名、魔法数、缺失 javadoc）挂上去，把 LGPL 依赖面控制到最小。

**(2) Squawk 的 npm 包名是 `squawk-cli`，不是 `squawk`。**
npm 上名为 `squawk` 的包是 **v4.0.0 / MIT / 2022 年发布 / 仓库 `banterability/squawk`**，与 Postgres 迁移**完全无关**。若执行 `npm i -D squawk`，会得到一个不报错、也不做任何检查的工具——**这正是「绿灯假象」的典型形态**：门禁看起来配了，实际从未生效。
正确安装：`npm i -D squawk-cli`（v2.70.0，Apache-2.0 OR MIT，仓库 `sbdchd/squawk`，2026-10-23 仍有推送）。
**准入流程必须加一步：装完之后跑一次 `npx squawk-cli --version` 确认它真的会报违规**。凡门禁类依赖，都要有一条"负向验证"——故意写一条违规 SQL，确认工具真的拦得住。

**(3) ArchUnit 的上游仓库是 `TNG/ArchUnit`**（组织名 TNG，不是 tngtech）。许可证已从构件 POM 的 `<licenses>` 段原文核实为 Apache-2.0。

### 11.2 发版节奏分层（2026-10-23 实测）

| 分层 | 成员 | 含义 |
| --- | --- | --- |
| 近 3 天内有推送（活跃） | ESLint、Prettier、eslint-plugin-vue、Knip、Vitest、@vue/test-utils、dependency-cruiser（10-19）、Squawk、Trivy、Spring Modulith、Spotless、Checkstyle | 迭代快，跟进及时 |
| 最近发版已超 1 年（成熟稳定） | ArchUnit（2025-05）、Spotless 插件版本（2025-05）、PIT（2025-04）、Testcontainers（2025-06） | 均为**构建期工具**，不进入运行时，对本项目无版本风险；「一年没发版」在这类工具上通常是稳定而非弃坑的信号 |

**真正的风险类型不是「发版慢」，而是「改名 / 弃用 / 同名包」**——第 11.1 节第 2 条即此类。这也解释了为什么准入清单必须逐项核实而不能凭印象写。

---

## 12. 规则 → 检查项映射表

把 `AGENTS.md` 与 `.codebuddy/rules/frontend-ui` 的全部条款逐条落到检查手段上。**可完全机检的约占三分之二**；剩下三分之一明确标为"只能靠约定"，不做假覆盖。

### 12.1 后端与仓库级规则

| 规则条款（出处） | 可机检 | 检查手段 | 工具 | 落地位置 |
| --- | --- | --- | --- | --- |
| 一个 change = 一条分支 = 一个 PR（`AGENTS.md:13`） | ✅ | 分支名正则 + PR 存在性 | 脚本 | `code/scripts/verify-repo.sh` |
| 分支命名 `change/<id>` / `fix/<简述>`（`:14`） | ✅ | 分支名正则校验 | 脚本 | 同上 |
| 归档后立即删除分支（`:15`） | ⚠️ 半 | 归档目录非空 ∧ 同名分支仍存在 → 告警 | 脚本 | 同上 |
| 分支存活不超过 3 天（`:16`） | ✅ | 分支创建时间 vs 今天 | 脚本 | 同上 |
| 并行会话用 worktree 隔离（`:17`） | ⚠️ 半 | 检查 `.git/worktrees` 数量，多个 worktree 同分支 → 告警 | 脚本 | 同上 |
| Conventional Commits，类型限 9 种（`:21`） | ✅ | 提交信息正则 | 脚本 或 commitlint | 同上 |
| 单次提交不超过 400 行（`:22`） | ✅ | `git show --numstat` 累加 | 脚本 | 同上 |
| 一次提交只做一件事（`:23`） | ❌ | 语义判断，工具无法可靠判定 | 靠 change 粒度约束 | — |
| 每提交可编译可运行（`:24`） | ✅ | CI 必须全绿 | CI | `.github/workflows/ci.yml` |
| 重构与功能分开提交（`:25`） | ⚠️ 半 | 同提交内同时出现 `refactor` 与 `feat` 语义 → 告警 | 脚本（粗判） | — |
| 迁移与代码变更分开提交（`:26`、`:42`） | ✅ | 同一提交同时含 `V*.sql` 与 `.java` → **失败** | 脚本 | `code/scripts/verify-repo.sh` |
| 新功能域先出调研文档（`:32`） | ✅ | change 内无 `docs/research/` 引用 → 失败 | openspec 校验 + 脚本 | — |
| 禁止跨模块直接依赖（`:38`） | ✅ | ArchUnit 规则 | ArchUnit | `code/backend/wms-app/src/test/java/.../ArchitectureTest.java` |
| 禁止引入 GPL/AGPL/SSPL（`:39`） | ✅ | 依赖许可证扫描 + 准入清单登记 | Trivy/OSV + `docs/standards/dependency-admission.md` | 新建 |
| 禁止为让测试通过改断言（`:41`） | ⚠️ 半 | 同提交内测试文件变更与被测源码变更并存 → 告警 | 脚本（粗判） | — |
| 编译、单测、静态检查全通过（`:47`） | ✅ | 三个 CI job 全绿 | CI | `.github/workflows/ci.yml` |
| 先加字段 → 写代码 → 最后删旧字段（`:53`） | ✅ | 迁移顺序检查：删列必须晚于加列的 N 个版本 | 自定义脚本 | `code/scripts/verify-migrations.sh` |
| 一次迁移一个文件（`:54`） | ✅ | 同一提交含 >1 个 `V*.sql` → 失败 | 脚本 | 同上 |
| 迁移必须可回滚（`:54`） | ⚠️ 定义待定 | Flyway Community 无 undo；拟改「前滚修复 + lint 拦截破坏性变更」 | squawk + 脚本 | **需先确认定义** |
| 库存相关表禁止物理删除（`:55`） | ✅ | 迁移中出现 `DROP TABLE` 库存表 → 失败 | 自定义脚本 + squawk | `code/scripts/verify-migrations.sh` |
| 依赖准入三项确认（`:74`） | ✅ | 准入清单 + CVE 扫描 + 版本更新提醒 | Dependabot + Trivy/OSV | `.github/dependabot.yml` |
| 术语统一（`:80-81`） | ❌ | 中文词表正则误报率高，不做 | 靠 spec 与评审 | — |

### 12.2 前端规则

| 规则条款（出处） | 可机检 | 检查手段 | 工具 | 落地位置 |
| --- | --- | --- | --- | --- |
| 一个应用只允许一套 UI 组件库（`AGENTS.md:59`、`RULE.mdc:20`） | ✅ | `package.json` 依赖白名单：只允许 `ant-design-vue`；出现第二套 UI 库即失败 | 脚本 + ESLint `no-restricted-imports` | `code/scripts/verify-repo.sh` |
| 共享包按端分叉、禁止跨端引用（`:60`、`RULE.mdc:21-24`） | ✅ | 依赖图规则：`ui-web` 不得依赖 `ui-pda`，反之亦然 | dependency-cruiser | `code/frontend/.dependency-cruiser.js` |
| `packages/request`、`packages/tokens` 不依赖 UI 库（`RULE.mdc:25`） | ✅ | 同上 | dependency-cruiser | 同上 |
| 已迁应用不得再出现 `element-plus`（`RULE.mdc:26`） | ✅ | 依赖白名单 + `el-*` 用法扫描 | 脚本 + ESLint | 同上 |
| 多端不共享 UI 组件与布局（`AGENTS.md:61`） | ✅ | 依赖图规则：两个 app 之间不得互相 import | dependency-cruiser | 同上 |
| 通用组件放 `src/components/<模块>/`、页面按域放 `src/views/<domain>/`（`:62`） | ✅ | 路径约束 | ESLint `no-restricted-imports` + 自定义路径规则 | `code/frontend/eslint.config.js` |
| 菜单由后端树驱动、key 唯一、递归渲染（`:63`、`RULE.mdc:37`） | ✅ | 模板禁 `level === 1` / `depth < 3` / `index === 0` 等层级判断；key 唯一性用单测断言 | ESLint + Vitest | eslint 配置 + `apps/*/src/**/__tests__/` |
| 菜单项禁 `router-link` / `<a>` 包裹（`RULE.mdc:38`） | ✅ | ESLint `vue/no-template-as` + 自定义 | ESLint | eslint 配置 |
| 菜单 key 由转换层生成并校验唯一（`RULE.mdc:39`） | ✅ | 转换层单测 | Vitest | `apps/*/src/**/__tests__/` |
| 业务代码禁色值字面量（`:68`、`RULE.mdc:30`） | ✅ | 正则禁 `#hex`、`rgb(`、`rgba(`、`hsl(`，白名单 `packages/tokens/src/primitives.ts` 与生成物 | ESLint `no-restricted-syntax` | eslint 配置 |
| 只引用 semantic 与 component 层（`:68`、`docs/frontend.md:67-68`） | ✅ | 禁 `var(--text-*)`、`var(--leading-*)`、`var(--weight-*)` | ESLint `no-restricted-syntax` | eslint 配置 |
| 令牌改动须同步生成物（`:69`） | ✅ | **已有**：`packages/tokens/scripts/gen-css.test.ts` 拦截手改 `primitive.css` | 已有 | 保持 |
| 间距圆角字号行高阴影只允许 `var(--*)`（`RULE.mdc:33`） | ✅ | 禁裸 px（需白名单：antd 覆盖点、图表尺寸） | ESLint + 少量豁免白名单 | eslint 配置 |
| 现有死接线（`api/client.ts` 从未被 import） | ✅ | Knip 报未使用导出/文件 | Knip | `code/frontend/knip.json` |
| 两 app 完全重复的 3 个文件 + 近乎重复的 `BasicLayout.vue` | ⚠️ 半 | dependency-cruiser 查不到"跨包复制"，需 jscpd 或人工收敛到 `packages/` | jscpd（P2） | `code/frontend/.jscpd.json` |
| 菜单为前端静态数组、与后端树未打通（`AGENTS.md:63`） | ❌ | 这是**功能缺失**，不是规则违规 | — | 属 `init-wms-foundation` 6.9 任务范围 |

### 12.3 统计

| 类别 | 条数 | 说明 |
| --- | --- | --- |
| 可完全机检 | **30** | 覆盖了 `AGENTS.md` 里全部"禁止项"与"验证"条款，以及前端门禁的全部硬约束 |
| 半机检（需告警 + 人工确认） | 5 | 分支归档、worktree 隔离、重构/功能分提交、改断言、分号重复代码 |
| 不能机检 | 4 | 一次提交只做一件事、术语统一、"不改范围外文件"的语义判断、"需求不清要问" |

**这 4 条正是 AI 最容易违反、也最需要人类验收时重点看的**。换句话说：机器负责挡住「可判定的错」，人类只需要盯住「需要判断的对错」——这正好把人类从逐行 review 里解放出来，符合 `AGENTS.md:7-8` 设定的分工。

---

## 13. CI 方案与额度估算

### 13.1 job 划分与预估时长

> 时长为估算：基于本机实测构建产物（`code/backend/wms-app/target/wms-app-0.1.0-SNAPSHOT.jar` 47.49 MB，依赖已在本机 `.m2` 缓存）与 GitHub Actions `ubuntu-latest` 常规速度。首次运行需下载 Maven 依赖，会显著更慢。

| job | 内容 | 首次 | 缓存后 | 触发条件 |
| --- | --- | --- | --- | --- |
| `backend` | `./mvnw -B verify`（编译 + 单测 + ArchUnit + Spotless/Checkstyle） | 4–6 min | 1.5–2.5 min | push + PR |
| `db-migration` | 起 PG 16 服务容器 → 启动应用触发 Flyway → 校验 24 张表存在 | 3–4 min | 1.5–2 min | push + PR |
| `frontend` | `pnpm install --frozen-lockfile` → typecheck → lint → test → build | 3–4 min | 1–2 min | push + PR（**已有 job，需扩 3 步**） |
| `frontend-arch` | dependency-cruiser + Knip | 30 s | 30 s | push + PR |
| `migration-lint` | `squawk-cli` 扫描 `db/migration` | 10 s | 10 s | **仅当 diff 含 `.sql`** |
| `dep-scan` | Trivy/OSV 扫依赖 + CVE | 60 s | 40 s | 每日定时 + push 到 main |

### 13.2 成本结论：已经是 0 元，不需要换平台

**核实前提（2026-10-23）**：远程仓库 `github.com/ChenXiang1988/yunWMS` 为 **公开仓库（Public）**，MIT License。

GitHub 官方计费文档原文：「GitHub Actions 使用方式的计量方式：使用标准 GitHub 托管运行器对**公共仓库**是**免费的**。对于私有仓库，每个 GitHub 账户根据其套餐会获得一定额度的免费分钟数。」

即：**2,000 分钟/月 是私有仓库的额度，对本仓库不适用**。公开仓库用标准托管运行器**分钟数不限量、0 元**。

| 场景 | 额度 | 本方案占用 | 月成本 |
| --- | --- | --- | --- |
| **公开仓库（当前状态）** | 分钟数不限量 | — | **¥0** |
| 若转私有（Free 计划） | 2,000 分钟/月 | P1 方案约 240 分钟（12%） | ¥0（仍在免费额度内） |

**结论：换 CI 平台在本项目上没有任何成本收益。** 各平台免费额度对比：

| 平台 | 免费额度 | 对本项目 |
| --- | --- | --- |
| **GitHub Actions（公开仓库）** | **0 元、分钟不限量** | ✅ **已是最优，无需迁移** |
| GitLab CI Free | 400 分钟/月 | ❌ 额度比 GHA 私有还少，且要迁仓库与配置 |
| CircleCI | 30K credits/月（≈6 000 分钟） | ❌ 要重写 DSL，且超出会计费 |
| Semaphore Community | 仅自托管 runner | ⚠️ 需自建 runner，不比 `act` 省事 |
| Jenkins / Woodpecker 自建 | 软件免费，机器要钱 | ❌ 本机 Mac 跑 Jenkins 更贵更麻烦 |
| GHA 自托管 runner（跑在本机 Mac） | 免费（GitHub 已推迟原定收费） | ⚠️ 可行但占用本机资源、版本管理麻烦，不如直接用托管 runner |

**公开仓库仍有的限制**（均不构成瓶颈）：每月 workflow run 次数上限（账号级 2,000 次/月）、artifact 存储 500 MB、缓存存储 10 GB。本项目按 30 次 PR/月估算，占用远低于任何一条。

### 13.3 真正的成本不是钱，是等待时间与重复执行

既然金额已是 0，"便宜"应重新理解为两件事：**① 少占额度次数；② 让 PR 反馈更快**。据此把 6 个 job 分四级执行：

| 层级 | 位置 | 内容 | 时长 | 触发 |
| --- | --- | --- | --- | --- |
| **L0 本地** | `pre-commit` + `code/scripts/verify.sh` | 格式、lint、Conventional Commits、400 行、迁移与代码分提交、菜单与令牌规则 | 1–3 s | 本地提交前 |
| **L1 PR CI** | GitHub Actions | `backend`（编译+单测+ArchUnit+Spotless）、`frontend`（typecheck+lint+test+build）、`migration-lint`（squawk） | **3.5–4.5 min** | pull_request |
| **L2 main** | GitHub Actions | `db-migration`（PG 容器 + 迁移从零重建）、`dep-scan` | 2.5–3 min | push 到 main |
| **L3 夜间** | GitHub Actions | PIT 变异测试（限关键状态机） | 10–20 min | schedule |

**PR 反馈从 8 分钟降到 4 分钟左右**，且重检查只跑在低频路径上。

L0 的价值最大：第 12 章里 30 条可机检规则中，约 **22 条属于"快检查"**（格式化、命名、禁止项、路径、菜单、令牌），放进 `pre-commit` 后在提交前 1–3 秒内拦掉，根本不会进 CI。CI 只保留必须干净环境才成立的 8 条（编译、单测、架构测试、迁移重建）。

### 13.4 三处现成的浪费，以及一个零消耗补充选项

**现成浪费一：重复触发。**
`on: push` 与 `on: pull_request` 同时存在，同一个 PR 会跑两遍 CI（分支 push 一次 + PR 一次），额度次数与等待时间直接翻倍。
建议：`push` 只保留 `branches: [main]`，其余靠 `pull_request`。原注释担心"白名单式触发容易漏 fix/ 分支"——改法不会漏，因为 fix 分支同样会提 PR。

**现成浪费二：无 Maven 缓存。**
现有 job 已有 `cache: pnpm`，加 backend job 时必须同时加 `cache: maven`，否则每次重下依赖约 +3–4 分钟。

**已做对的地方：**`concurrency` 已配 `cancel-in-progress: true`，同分支新推送会取消旧任务，这已经在省额度了，保留。

**零消耗补充选项：`act`（MIT，`nektos/act`）。**
用 Docker 在本地执行同一份 `.github/workflows/*.yml`，0 消耗、0 排队，可离线。
定位：**开发期调试 CI 配置的工具，不是替代品**。局限需如实记录：它不运行真实 GitHub runner 软件而是模拟执行，`services` 与 matrix 场景本地行为可能与真实 runner 有差异；且 macOS 上 Docker 较慢。
建议用法：`act` 用于验证 workflow 改动的语法与步骤顺序，最终仍以真实 runner 结果为准——公开仓库本来就不花钱，没有理由放弃真实 runner 的一致性。

### 13.5 开源场景专属的免费能力（2026-10-23 逐项核实）

本仓库为**公开开源项目**（`github.com/ChenXiang1988/yunWMS`，MIT）。开源身份解锁了一批**普通私有仓库拿不到**的 0 元能力，此前未纳入本篇比较：

| 能力 | 提供方 | 开源项目是否免费 | 核实依据 | 对本项目的用法 |
| --- | --- | --- | --- | --- |
| Actions 运行分钟数 | GitHub | **公开仓库不限量、0 元** | GitHub 计费文档 | 承载全部 CI 门禁 |
| **Secret scanning + Push protection** | GitHub | **公开仓库免费自动运行**；语言无关，覆盖所有分支完整历史 + Issues/PR/Discussions | GitHub Docs《Secret scanning》 | 防密钥泄漏，公开仓库必开；push protection 通常已默认开启 |
| **CodeQL 代码扫描** | GitHub | **公开仓库可用** | GitHub Docs《About the CodeQL CLI》 | Java / TypeScript 的 SAST，进 CI |
| **Dependabot** | GitHub | **公开仓库免费** | GitHub 计划页 | 依赖版本更新 + CVE 告警 |
| **OpenSSF Scorecard action** | OpenSSF | **对所有公开仓库免费** | `ossf/scorecard-action` README | 供应链卫生体检（分支保护、危险 workflow 模式、是否含二进制产物等） |
| **SonarCloud Free OSS Plan** | Sonar | **公开项目免费**；2026-01-27 宣布；资格只需「公开项目」，**不需隶属基金会**（官方 2026-03-09 回复确认） | Sonar Community 公告 | 质量看板 + 功能分支分析 + 自定义质量标准 + PR 分析 |
| Copilot Pro | GitHub | 知名开源维护者免费 12 个月，**每月重新评估资格** | GitHub 定价页 | 面向**人类**维护者；对本项目「AI 会话写码 + 人类只验收」的模式无直接价值 |
| Renovate | Mend | 功能显著强于 Dependabot（自动合并、分组更新），但**许可证是 AGPL-3.0** | — | 开源项目使用 AGPL 工具本身**合规**；但 `AGENTS.md:39` 明文禁止 AGPL 依赖，**若要启用须先修改该条规则** |

**SonarCloud Free OSS Plan 的未确认项（如实记录，实施前须核对官方订阅文档 `docs.sonarsource.com/sonarqube-cloud/administering-sonarcloud/managing-subscription/subscription-plans#oss-plan`）**：

- 官方公告**只说「当前免费」，未承诺永久免费**；
- 公告未逐项确认 **duplication 检测**、**AI 专项代码分析**、具体 SAST 覆盖是否在 OSS 计划内；
- 公告中关于 Beta 功能（Context Augmentation、PR 分析 / Remediation Agent）的提问**官方未答复**。

**因此对 SonarCloud 的定位是「值得试」**——但它的价值在质量看板与 PR 分析，**不替代 P1 的本地门禁**（本地门禁零 secret、AI 会话可自行复现，见 13.6 第 (1) 条）。

### 13.6 单人维护的开源项目：增量要求（修订）

**前提修订（2026-10-23，用户明确）**：仓库公开开源，但维护者**不接受外部参与开发**——不会合并外部 PR，也不需要外部贡献者流程。

这一条把本节原先的推论推翻了两处，下文按修订后重写：

| 原推论 | 修订后 |
| --- | --- |
| 「公开仓库会收到外部 PR，fork PR 无 secrets 是硬约束」 | **不成立**——不会接外部 PR，fork 约束不是选型硬约束 |
| 「`docs/research/` 是外部贡献者理解设计的唯一入口」 | **定位错了**——第一读者不是外部贡献者，见第 (3) 条 |

**（1）门禁选型：fork 约束解除，但「零 secret」仍是优选**

约束解除后，SonarCloud、CodeQL、Trivy 等需要 token 的工具**可以作合并门禁**。但 P0/P1 仍优先选本地可跑工具，理由不再是"必须"，而是三条更实际的：

- CI 配置更简单，不需要管理 secret；
- 泄露面更小（零 secret 即零泄露面）；
- 本地 `verify.sh` 与 CI 用同一套命令，**AI 会话能自己复现 CI 结果**——这对"AI 写码 + 人类只验收"的模式价值最高。

**（2）门禁的地位从「防外部代码」变成「唯一防线」**

既然没有第二双眼睛（既无人类同事 review，也无外部贡献者），**AI 产出的每一行代码的质量保证来源就只剩自动化门禁 + 你的验收**。这让 P0/P1 的优先级**比原先更高**，不是更低：

> 单人维护 + 全量 AI 写码 + 门禁缺席 = 一个没有任何防线的系统。
> 单人维护 + 全量 AI 写码 + 门禁完备 = 一个质量只取决于门禁强度、与人数无关的系统。

**（3）`docs/research/` 的真正读者是未来的 AI 会话**

这是我上一轮的定位错误，纠正如下：多数开源项目没有成体系的决策调研，本仓库已有 9 篇（含本篇），覆盖开源选型、前端栈、主数据模型、RBAC、执行单元建模、平台履约模型、CAD 解析可行性等。

但在「全量 AI 写码 + 单人维护」模式下，**这些文档的第一读者不是外部贡献者，而是下一个 AI 会话和你自己**。它们的作用是：

- 让新会话知道上一个会话为什么这么决定，避免重复调研与反复摇摆；
- 把「调研 → 结论 → 方案 → 被否候选」的推理链固化，避免半年后重新论证同一个选型；
- 这正是 `AGENTS.md:32`「新功能域必须先完成开源调研」这条规则的价值落点。

**因此它们必须入 git 并建索引**——不是为了对外展示，是为了**下一个会话能读到**。目前 7 篇未入 git、无索引，等于每个新会话都要从零开始。

**（4）保留的开源基建（只有两项，各有独立理由）**

| 项 | 保留理由（与"接贡献者"无关） | 现状 |
| --- | --- | --- |
| `SECURITY.md` | **不是为了接贡献，是为了漏洞私下报告**。公开仓库会被安全扫描器扫，漏洞细节若走公开 issue 会先被曝光。给一条私下渠道成本极低 | 缺 |
| README「维护状态声明」 | **保护你自己的时间**。明确写"个人项目，单人维护，不接受外部 PR，仅接受 bug 报告"，避免仓库看起来"可贡献实则无人响应"——这类半吊子开源项目最常见的负面评价 | 缺 |

**取消的项**（原先为接外部贡献者准备，现无对应需求）：`CODEOWNERS`（只有一名维护者）、PR 模板（不接外部 PR）、`CONTRIBUTING.md`（改为 README 里一段声明即可）。

**（5）两处规则与开源身份的张力（提出，不擅自修改）**

- `AGENTS.md:39` 禁止引入 GPL / AGPL / SSPL 依赖。**开源项目使用 GPL 依赖本身完全合规**（分发时提供源码即可），这条规则在开源语境下会挡掉大量生态可选方案。实际影响有限（PostgreSQL 是 BSD、Spring Boot 是 Apache-2.0），但若将来要接入 GPL 系中间件，需先评审这条规则。
- `AGENTS.md:39` 同时排除 Renovate（AGPL-3.0）。在开源项目里 Renovate 合法且更优（自动合并、分组更新）。**单人维护场景下这个价值反而更高**——自动合并依赖更新能直接省掉你的手工动作。若启用需先改这条规则。

**（6）顺序建议（修订）**

原建议「先做门禁，再对外开 issue、接贡献者」的前提已不成立，改为：

> **先做 P0 / P1 门禁，再扩大公开面**（打 tag 发布、写公开说明、允许他人 clone 使用）。

理由：公开历史永久。虽然不会有外部代码混进来，但**当前状态下的漂移证据**（重复的 `api/client.ts`、令牌层级越界、静态菜单、0 测试、`code/backend/` 未入 git）会固化进公开历史，且事后无法清除。对一个不接外部贡献的项目，这些痕迹影响的是项目可信度，以及**未来万一需要交接时**的接手成本。

### 13.7 本地一键验证脚本（第 7.3 节 P2-5 的具体设计）

所有门禁的本地入口，必须满足两条：

1. **一条命令跑完**：`code/scripts/verify.sh` 依次执行后端编译/单测/架构检查、迁移校验、前端 typecheck/lint/test/build/死代码检查，任一失败即非零退出。
2. **负向验证**：每个门禁工具都要有一条"故意违规，确认真的拦得住"的用例，脚本里带 `--self-test` 模式。这是防止第 11.1 节第 2 条那类**绿灯假象**的关键——工具装上了不等于在生效。

AI 会话收尾时必须执行 `verify.sh` 并贴出完整输出，这把「AI 说它验证过了」变成「AI 必须运行并展示机器判定结果」，是对 METR 那个 20% 主观错觉最直接的对冲。

---

## 14. 评审清单（供逐条批准）

按批准优先级排列。每条只需回答「批准 / 不批准 / 改」。

| # | 待批项 | 涉及依赖 | 归属 |
| --- | --- | --- | --- |
| 1 | 后端 41 个文件纳入 git；`docs/research`、`docs/prd`、`docs/standards` 纳入 git | 无 | P0 |
| 2 | CI 增加 `backend` job（`./mvnw -B verify`），并加 `cache: maven` | 无 | P0 / L1 |
| 3 | CI 增加 `db-migration` job（PG 容器 + 迁移从零重建 + 24 表校验），归入 L2（push 到 main） | 无 | P0 / L2 |
| 3b | 接入 `pre-commit`（MIT）承载 L0 快检查：格式、lint、Conventional Commits、400 行、迁移与代码分提交 | pre-commit（MIT） | P1 / L0 |
| 3c | `act`（MIT，nektos/act）作为本地调试 workflow 的工具，不作门禁 | act（MIT） | P1 / 开发期 |
| 4 | 归档 `migrate-network-console`、`migrate-owner-portal`，把 spec 增量并入 `openspec/specs/` | 无 | P0 |
| 5 | 建 `.editorconfig`；清理死配置 `VITE_USE_MOCK` / `VITE_APP_TITLE` | 无 | P0 |
| 6 | 引入 ArchUnit，把「禁止跨模块直接依赖」变成可执行规则 | ArchUnit（Apache-2.0） | P1-1 |
| 7 | 引入 Spotless + Checkstyle，限定规则范围以控制 LGPL 依赖面 | Spotless（Apache-2.0）、Checkstyle（**LGPL-2.1**） | P1-2 |
| 8 | 引入 ESLint + eslint-plugin-vue + Prettier，承载前端门禁全部条款 | ESLint / eslint-plugin-vue / Prettier（全 MIT） | P1-3 |
| 9 | 引入 dependency-cruiser + Knip | dependency-cruiser（MIT）、Knip（**ISC**） | P1-4 |
| 10 | 引入 Vitest + @vue/test-utils，建立前端测试底座 | 二者均 MIT | P1-5 |
| 11 | 引入 `squawk-cli`（**注意包名**）+ 自定义迁移顺序检查脚本 | squawk-cli（Apache-2.0 OR MIT） | P1-6 |
| 12 | 引入 Trivy 或 OSV-Scanner（二选一）+ Dependabot | Trivy（Apache-2.0） | P1-7 |
| 13 | 「迁移可回滚」改定义为「前滚修复 + lint 拦截破坏性变更」，写入 `AGENTS.md` | 无 | 定义变更 |
| 14 | 从下一个 change 起强制 `change/<id>` 分支 + PR；补分支/提交纪律检查脚本 | 无 | 流程 |
| 15 | 是否把 `openspec/config.yaml` 的 rules 段升级为「检查项清单」（每条 rules 须有对应 CI 检查） | 无 | 流程 |
| 16 | P2 再批：Testcontainers、PIT、jscpd | MIT / Apache-2.0 / MIT | P2 |
| 17 | P2 再议：Spring Modulith（需单开 change 论证包结构与模块边界） | Apache-2.0 | P2 |
| 18 | 确认 GitHub **secret scanning + push protection** 已在公开仓库生效（公开仓库免费自动，通常已默认开启） | 0 元 | P0 |
| 19 | 接入 **CodeQL** 代码扫描（公开仓库免费）进 CI | 0 元 | P1 |
| 20 | 接入 **OpenSSF Scorecard** action（公开仓库免费）+ 申请 OpenSSF 徽章 | 0 元 | P2 |
| 21 | 试接 **SonarCloud Free OSS Plan**（公开项目免费）；定位为质量看板与 PR 分析，**不作合并门禁** | 0 元 | P2 |
| 22 | 补两项开源基建：`SECURITY.md`（漏洞私下报告渠道）+ README「维护状态声明」（个人项目、单人维护、不接受外部 PR） | 无 | P1 |
| 23 | `docs/` 建索引，把 9 篇调研文档接入 README（目前 7 篇未入 git）；**第一读者是未来的 AI 会话**，不是外部贡献者 | 无 | P1 |
| 24 | 是否修改 `AGENTS.md:39`，允许在开源语境下使用 GPL / Renovate（AGPL）——**开源项目用 GPL 依赖本身合规** | 无 | 规则评审 |
| 25 | 采纳顺序建议：**先完成 P0 / P1 门禁，再扩大公开面**（打 tag 发布、写公开说明）（公开历史不可撤回） | 无 | 流程 |


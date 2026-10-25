# 规则治理：把「判不出来」的地方改成「能判出来」

- 调研时间：2026-10-25
- 时效：本文为 2026-10-25 的快照，记录当时的检查结果与依据；后续变化以仓库现状为准，本文不回改。
- 数据来源：仓库自身（`git log --numstat` 全量扫描、`git status`、各规则文件原文）、GitHub API 与官方文档（ArchUnit User Guide 1.5.1、RFC 2119/8174、各仓库 LICENSE）。所有读数均可复跑。
- 结论状态：已确认并执行（2026-10-25）。**第三节提议的「冻结基线」在实施当日被实测推翻**：现有代码的存量模块依赖违反为 0，且冻结在 `allowStoreUpdate=true` 时会把新增违反静默写进基线并判通过，实际改用纯分层规则；推翻依据见提交 `test(backend): 用 ArchUnit 校验模块依赖方向`。

---

## 一、这份文档解决什么

规则写了「必须怎么做」，但没写「怎么算没做到」。结果是每条最后都由写代码的那个 AI 自己判断——而 `docs/agents.md` 整篇的设计目的，恰恰是避免自己判断自己。

具体四个毛病，都是能举例的：

### 毛病 1：同一个 400 行，三个说法

| 位置 | 说法 |
| --- | --- |
| `AGENTS.md:28` | 不超过 400 行，**不含**自动生成代码与纯格式化 |
| `.codebuddy/rules/ai-delivery/RULE.mdc:17` | 不超过 400 行，**没提**任何排除 |
| `docs/agents.md:122` | 增删合计，**排除** 4 个文件；并写明「不含自动生成代码」这条**不适用本项** |

按哪个说法执行都能过。下面第二节的体检结果说明这不是理论问题。

### 毛病 2：写 Java 的 AI 不读 Java 规范

`AGENTS.md:55` 要求「动手前必须读完本次改动命中的规范文件」。但两个实现类 Agent 的必读清单里没有编码规范：

- `backend-implementer.md:9` 只读 `AGENTS.md`、`ai-delivery`、`research-first`——不读 `docs/coding-java.md`、`docs/coding-project.md`
- `frontend-implementer.md:9` 只读 `AGENTS.md`、`docs/frontend.md`、`frontend-ui`——不读 `docs/coding.md`

### 毛病 3：摘要漏了「以谁为准」

`ai-delivery/RULE.mdc` 是 `AGENTS.md` 的摘要，但没写「冲突时以谁为准」。子 Agent 只读这两份时，遇到不一致没有依据（优先级表在 `docs/agents.md:74`，但子 Agent 不被要求读那份）。

### 毛病 4：规则写了检查，从没执行

`AGENTS.md:45` 写「跨模块直接依赖由 ArchUnit 拦截」。实际情况：
- `code/backend` 四个模块的 pom 里没有 ArchUnit
- `ci.yml` 只有前端一个 job，后端 147 个文件从未在 CI 里编译过

---

## 二、历史体检结果（140 次提交）

用 `code/tools/check-commit-size.sh` 扫全部历史，口径与 `docs/agents.md` 第五节一致（增删合计，排除 4 个生成物）。

**140 次提交，14 次超过 400 行。** 按超限量降序：

| 提交 | 行数 | 标题 | 性质 |
| --- | --- | --- | --- |
| `9c08f3f` | 6978 | chore: 导入现有工程基线 | 仓库初始化 |
| `99bfcdf` | 2610 | refactor(spec): 首期范围缩减至客户与商品 | 规格文档 |
| `48f3d43` | 2436 | feat(backend): 搭建四模块工程骨架与 24 张表的 Flyway 迁移 | 初始化 |
| `7eb5f02` | 2106 | docs: 补充 CAD 出图规范、调研结论与 PRD 就绪度标准 | 文档 |
| `9e4f416` | 1927 | feat(tools): 仓库平面图解析与货架自动排布工具 | 工具 |
| `7b1e705` | 1517 | feat(auth): 实现双主体登录与令牌签发，wms-common 改名 wms-web | 业务代码 |
| `f818879` | 1253 | refactor(openspec): 归档 init-wms-foundation | 流程 |
| `c0f8904` | 863 | feat(auth): 实现首次登录强制改密 | 业务代码 |
| `192e90a` | 574 | test(app): 用户管理端到端集成测试 | 业务代码（2026-10-25） |
| `c1b5936` | 566 | feat(auth): 实现密码策略与登录失败锁定 | 业务代码 |
| `5ed81c2` | 480 | docs(openspec): 补充客户与平台规格 | 文档 |
| `ed659f6` | 459 | test(app): 角色管理端到端集成测试 | 业务代码 |
| `65b507e` | 418 | test(auth): 用户查询与角色分配的 17 个单测 | 业务代码（2026-10-25） |
| `f948e10` | 405 | feat(auth): 改密失败计入登录失败计数 | 业务代码 |

三点观察：

1. **不是「历史上偶尔超」，是从来如此。** 140 次里 14 次超限，最大的接近 7000 行。
2. **最近仍在发生。** `192e90a`（574 行）与 `65b507e`（418 行）是 2026-10-25 提交的，就在体检前一天。
3. **已登记的只有 3 笔。** `openspec/changes/implement-owner/tasks.md:85-87` 记了 1536 / 863 / 405 三笔，只覆盖当前 change。其中 1536 与本文算出的 1517 差 19 行，原因是那次提交含 5 个文件重命名，两种统计对 rename 的处理不同。**不影响「是否超限」的判断，但说明登记值本身也不可复现。**

### 修复原则

**不重写历史。** 已合并的提交保持原样，只登记。理由与 `AGENTS.md`「存量违约不重写」一致：改写历史会破坏所有并行 worktree 的基线，代价远大于收益。

---

## 三、怎么修：挡住新的，放过旧的

新增的模块边界检查用 ArchUnit 的「冻结」机制（User Guide 1.5.1 第 8.6 节）：

- 第一次运行，把当前所有违反记成一个文本文件，放进 git
- 之后每次运行，**只报新增的违反**；原来那些不报
- 修好一条，它自动从记录里消失，不会永久豁免

这样其他线程的代码不会因为历史问题变红，但新写的越界代码会被拦下。

两个口子必须管住：

| 口子 | 做法 |
| --- | --- |
| 谁都能重新生成基线，等于没有基线 | `archunit.properties` 里 `allowStoreCreation=false`、`allowStoreUpdate=false`；CI 再显式传一遍 |
| 基线越改越宽 | 基线**只能缩不能放**。要放必须单独一次提交，写明新增了哪条、为什么允许 |

---

## 四、用了什么、没用什么

| 工具 | 用途 | 许可证 | 结论 |
| --- | --- | --- | --- |
| ArchUnit `archunit-junit5` | 检查模块之间有没有乱引用 | Apache-2.0 | 用。`AGENTS.md:45` 早就指定了它，只是一直没装 |
| commitlint | 检查提交信息格式 | MIT | 可用，本次未做（属另一件事） |
| RFC 2119 / RFC 8174 | 规定 MUST / SHOULD 这些词的严格含义 | IETF 文档 | 参考。8174 明确：只有全大写才有约束力，且用不用这些词本身不强制 |
| OPA / Conftest | 通用策略引擎 | Apache-2.0 | 不用。它检查的是配置文件，我们的规则管的是代码和流程；要先把 diff 转成 JSON 才能写策略，比十行脚本还贵 |
| Semgrep | 通用静态扫描 | **LGPL-2.1** | 不用。属于 GPL 家族，需单独确认；能力又与 ESLint + ArchUnit 重叠 |
| danger-js | PR 层面检查 | MIT | 不用。它依赖 PR 事件，本仓库没有 PR 流程 |
| Spring Modulith | Spring 官方模块化检查 | Apache-2.0 | 不用。要改模块结构才能检查，等于为了体检而手术 |

所有读数采集于 2026-10-25。

---

## 五、改了哪些文件

| 提交 | 文件 | 说明 |
| --- | --- | --- |
| 1 | `AGENTS.md:28`、`ai-delivery/RULE.mdc:8`、`:17`、`docs/agents.md:122` | 400 行指向唯一口径；补「冲突以 AGENTS.md 为准」 |
| 1 | `backend-implementer.md:9`、`frontend-implementer.md:9` | 必读清单补编码规范 |
| 2 | `code/backend/pom.xml`、`wms-app/pom.xml`、`ModuleBoundaryTest.java`、`archunit.properties`、`archunit_store/` | 三条模块边界规则 + 冻结基线 |
| 3 | `code/tools/check-commit-size.sh`、`docs/agents.md` 第五节 | 历史规模检查脚本 + 存量违约登记 |
| 4 | `.github/workflows/ci.yml` | 加 backend job，显式禁止在 CI 里重建基线 |

---

## 六、对其他线程的影响

| 事项 | 说明 |
| --- | --- |
| 需要做什么 | 合并前 rebase 一次，本地跑 `./mvnw -pl wms-app test -Dtest=ModuleBoundaryTest` |
| 报新增违反怎么办 | 改代码，或找主 Agent 评估是否扩基线（扩基线要单独提交并写理由） |
| CI 会变慢 | backend job 增加约 3–5 分钟（含 Testcontainers） |
| 基线会不会漂 | ArchUnit 默认忽略行号，只按类名匹配，rebase 影响小 |

---

## 七、还没解决

1. **400 行阈值本身是否合理。** 历史上 14 次超限、最大 6976 行，说明要么阈值太紧、要么提交习惯太粗。本次只统一口径，没动数值。
2. **`docs/agents.md` 里 8 项「需要人判断」的检查项。** 实际判断者是主 Agent 自己，本次未处理。
3. **规则改动没有留痕机制。** `design.md` 只服务 change，规则文件改了就是改了，`git log` 只能回答「改了哪行」，回答不了「为什么改」。建议后续用 ADR 记录（模板 MADR，MIT OR CC0-1.0）。
4. **基线文件可能很大。** 若首次生成的基线超过数百行，说明模块分层实际已被破坏，此时应单开 change 修，而不是继续冻结。

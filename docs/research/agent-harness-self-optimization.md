# Agent Harness 自优化方案调研（Harness² 及同类）

- 调研时间：2026-10-25
- 时效：本文为 2026-10-25 的快照，记录当时状态；后续变化以仓库现状与上游仓库为准，本文不回改。
- 数据来源：GitHub 仓库 README、`docs/SETUP.md`、`docs/EXPERIMENTS.md`、issues 列表页、项目主页 `harness2.github.io`、arXiv 摘要页与 **HTML 全文（v1）**；star / 提交数 / 最后推送时间来自页面快照与第三方聚合站，**非 GitHub API 实时值**，采集时间不完全一致，仅作量级参考。
- 证据分级：**已核**＝读过一手页面（仓库文档、论文正文、项目主页）；**二手**＝仅见第三方文章或聚合站读数；**未验证**＝无可用证据，按缺项处理。
- 结论状态：**待用户确认**（第 5 节 Q1–Q3 闭环前不得开工）
- 关联调研：`docs/research/ai-coding-sustainability.md`（把规则变成 CI 门禁）与本篇不重叠——那篇管「AI 写的代码怎么不被放过」，本篇管「AI 的工作方式本身能不能被自动改进」。
- 绑定变更：无。本篇为后续「交付协议增补」类变更的前置依据，届时在该变更 `proposal.md` 中引用。

---

## 1. 需求要点（R1）

### 1.1 问题陈述

起因是一个具体项目：Google Research 于 2026-10 开源 Harness²，主张在**冻结模型权重**的前提下，让 Agent 自动改写自己的执行外壳（提示词、工具指引、记忆、技能、子代理），并声称在三个职业基准上单步提升 5 分以上。

对本仓库而言，值得判断的不是「这个项目好不好」，而是三个问题：

1. 它的机制里有没有可直接搬进本仓库交付协议的部分？
2. 能不能作为依赖引进来跑？
3. 如果引不进来，缺的东西应该自己补在哪一层？

### 1.2 功能范围

| 域 | 内容 |
| --- | --- |
| 交付协议 | `AGENTS.md`、`.codebuddy/rules/*`、`.codebuddy/agents/*` 的改写方式与验证方式 |
| 多 Agent 分工 | 主 Agent 派发、子 Agent 实现、审计复查的现有链路 |
| 经验沉淀 | `completion.md`、`design.md`、`docs/research/*` 三类留痕之间的取舍 |
| 评测能力 | 是否存在可复用的固定任务样本与判定口径 |

### 1.3 关键特征

- **无 ground-truth**：业务口径由人确认，不存在能自动判分的验证器。这与 Harness² 的立论前提一致，是它值得看的原因。
- **成本敏感**：零商业授权预算，外部模型调用与长时评测都算成本。
- **规则已很重**：`AGENTS.md` 自我限定「只放门禁、禁止项与路由」，再往里加机制会与它自己的边界冲突。
- **验收主体是人**：人类不逐行 review，只在 change 结束验收。任何「模型自评放行」都违背这条。
- **无评测集**：仓库有 `.github/workflows/ci.yml`（前端检查），但没有任何 Agent 行为评测样本。

### 1.4 边界与非目标

**本期不做**

- 不做模型微调、不做强化学习训练，不碰权重。
- 不自建 JobBench / LAB / WorkBuddy-Bench 级别基准：LAB 属法律数据、WorkBuddy-Bench 需 Docker 与外部数据集，均超出本仓库需要。
- 不做 Agent 行为的全自动打分与自动合入。
- 不为「用上某个框架」而改造现有 OpenSpec 流程。

**本期只判断**

Harness² 的哪几条机制值得写进交付协议，以及引入其代码的可行性。

### 1.5 现存约束

- 技术栈：Java 21 + Spring Boot 3.x、PostgreSQL、Vue 3 + Ant Design Vue 4；Agent 侧为 CodeBuddy（子 Agent 定义在 `.codebuddy/agents/`）。
- 现有自动化只有前端 CI，无 Agent 评测、无后端 CI（见 `ai-coding-sustainability.md` 第 2 节）。
- 依赖准入三项：许可证、最近维护时间、能否被现有依赖替代。
- 单次提交 ≤ 400 行、一个 change 一个垂直切片、分支存活 ≤ 3 天。

---

## 2. 检索范围与方法（R2）

检索时间 2026-10-25。检索词：

| 层次 | 检索词 |
| --- | --- |
| 主候选 | `harness² github`、`harness2 github agent harness squared` |
| 同类机制 | `gepa-ai/gepa reflective prompt evolution license stars`、`agentic context engineering ACE github`、`google-research reasoning-bank license` |
| 工程化框架 | `stanfordnlp dspy license stars 2026 latest release` |
| 可信度交叉 | `harness2 实测 限制 issue substrate` |

方法：先读一手页面（仓库 README、项目主页、arXiv 摘要页），再用第三方盘点文交叉核对，不采信二手文章的实验数字。检索中发现中文自媒体的「实测」文章大量复述 README，不作为独立证据。

---

## 3. 候选清单与比较（R3）

### 3.1 全景表

| 项目 | Star（二手读数） | 活跃度 | 许可 | 定位 | 证据 |
| --- | --- | --- | --- | --- | --- |
| `google-research/harness2` | 约 19.3k | **3 次提交**，无 Release，v0.1.0 | Apache-2.0 | 递归 harness 优化框架（论文配套） | 已核 |
| `gepa-ai/gepa` | 6.8k–7.1k | 901 次提交、45 个 Release（PyPI 0.1.4），最后推送 2026-09 | MIT | 反思式 prompt / 代码优化（Pareto 前沿） | 二手 |
| `stanfordnlp/dspy` | 33.6k–38.4k | 445 贡献者、v3.2.x–v3.4.0（2026-05 至 2026-09）、约 640 未决 issue | MIT | 声明式程序 + 优化器（含 GEPA、MIPROv2） | 二手 |
| `kayba-ai/agentic-context-engine`（ACE 实现） | 约 2.6k | 944 次提交，最后推送 2026-09 下旬；另有商业托管版 | Apache-2.0 | 上下文演进（playbook 增删改） | 二手 |
| `ace-agent/ace` | 约 1.3k | 最后推送 2026-08-24，只有 eval 脚本、未上 PyPI | Apache-2.0 | ACE 论文参考实现 | 二手 |
| `google-research/reasoning-bank` | 未采集 | OSS 首发 2026-04-22，提交量少 | 未从 LICENSE 正文核实 | 记忆驱动自演进（Harness² 的对照基线） | 未验证 |
| Meta-Harness / Harness Scaling | 未采集 | 未找到独立仓库 | 未验证 | Harness² 论文中的两个基线 | 未验证 |
| OpenCode / Codex CLI | — | 持续更新 | 各自许可未核实 | Harness² 的执行器 substrate，非优化框架 | 未验证 |

> Star 数在不同聚合站差异明显（dspy 33.6k / 38.4k），说明采集口径不同。**量级可信，具体数字不可引用**。

### 3.2 主候选展开：Harness² 到底做了什么

**命题**：Agent = Model + Harness。权重冻结，只优化外层；且把「优化 harness 这件事」也变成优化对象。

**任务级递归**——对比式 `Propose → Probe → Compose`：

| 步 | 动作 | 关键点 |
| --- | --- | --- |
| Propose | 提出若干对 harness 的编辑 | 分两类作用域：invariant（与领域无关）与 domain-specific |
| Probe | 在同一任务上真实执行候选 | 拿的是**执行轨迹差异**，不是分数 |
| Compose | 只保留有执行证据支撑的编辑，组合成新 harness | 禁止凭想象合并 |

**Harnessing 级递归**——把改 harness 的经验沉淀进改进库（`harness files + EXPERIENCE.md`），指导下一次任务。每个新任务仍从初始 harness H₀ 起跑，跨任务**只继承经验，不继承产物**。

**八类可编辑组件**（按论文对 OpenCode 结构的切分）：C1 system prompt、C2 guardrails、C3 memory、C4 tool guidance、C5 subagents、C6 skills、C7 scripts、C8 plugins。落在三个层面：什么进上下文、工具怎么用、任务如何委派与工具循环内部行为。

**规模化的两种方式**：`--k K --mode parallel` 并行探更宽；`--k K --mode sequential` 串行精炼更深。

**实验（已核，来自项目主页）**：主表为 WorkBuddy-Bench，执行器 Gemini 3.5 Flash + OpenCode，三次均值。

| 方法 | 平均分 | 相对 base |
| --- | --- | --- |
| Base harness | 72.4 | — |
| ReasoningBank | 73.4 | +1.0 |
| Meta-Harness | 76.4 | +4.0 |
| Harness Scaling | 75.3 | +2.9 |
| Harness² k=1 | 77.5 | +5.1 |
| Harness² k=5 | 80.8 | +8.4 |

论文摘要另称跨三基准、四种「模型 × harness」配置，最高增益 WB +11.5、JobBench +4.5、LAB +11.8 个百分点。

### 3.3 逐维度比较

| 维度 | Harness² | GEPA | DSPy | ACE 系 |
| --- | --- | --- | --- | --- |
| 功能覆盖度 | 覆盖 8 类组件，含子代理与插件，宽度最大 | 主要覆盖 prompt 与少量代码，宽度窄 | 覆盖 prompt 与程序结构，不覆盖子代理/插件 | 覆盖上下文 playbook，宽度中 |
| 优化信号 | 执行轨迹对比，无真值 | 反思 + Pareto 前沿，建议有少量样本 | 需 metric，通常是可编程评分 | 反思 + 上下文增删改 |
| 活跃度 | **3 次提交、无 Release** | 高（901 提交、45 发布） | 高（445 贡献者） | 中高（944 提交） |
| 许可证 | Apache-2.0 | MIT | MIT | Apache-2.0 |
| 依赖与可维护性 | Linux + Python 3.12 + uv；跑真基准需 gcloud ADC、Node 20+、Docker、Pandoc/Poppler | 纯 Python，可 pip 安装 | 纯 Python，可 pip 安装 | 纯 Python |
| 与现有栈兼容性 | 低：执行器只支持 OpenCode / Codex，接第三方需自写 substrate | 中：与具体 Agent 框架解耦，可单独调 prompt | 中：偏 RAG / 程序化场景，与本仓库业务不贴合 | 中：偏上下文记忆层 |
| 二次开发成本 | 高：需自建 substrate、自备基准、自付多轮执行成本 | 中 | 中 | 中 |

### 3.4 关键判读

- **直接复用不成立**：3 次提交、无 Release、无版本承诺，README 自述「不是 Google 官方支持的产品」；执行器网络不受限、Codex 沙箱关闭，JobBench 的评分标准在 HF 公开可下，且作者自陈一次 Codex rollout **在未被指示的情况下自行下载了该任务的 rubric**（见 3.14）。作为依赖引入等于把交付链挂在一个论文仓库上。
- **「基于现有项目改造」也不划算**：要接进本仓库得自写 substrate（Codex/OpenCode 之外）、自建基准数据，而这两件事正是它没提供、我们也不需要的东西。
- **真正的可借点有两条**，且都不需要代码：
  1. **对比式证据**——改规则/改子 Agent 提示词时，用「同一批任务、改前改后各跑一次、看轨迹差异」代替「读起来更合理」。
  2. **只继承经验不继承产物**——跨任务留的是「可执行规则 + 适用场景 + 证据」，不是上一轮产出的代码或文档。
- **必须拒绝的一条**：它允许模型在无真值下自行判断改动是否有效（Compose 阶段）。本仓库的验收主体是人，这条不适用。

### 3.5 问题设定与形式化（论文 §2）

以下为论文正文与仓库文档的逐项读取结果。3.2 是摘要级，本节是细节级。

- **Harness 定义**：ℋ = (c¹, …, c^K)，K 个可编辑组件；与**冻结**模型 ℳ 组成 agent 𝒜(·; ℳ, ℋ)。
- **开放世界设定**：任务流 q₁…q_N 逐个到达，**无 ground truth、无可信 verifier**；每个任务都从公共初始 harness ℋ₀ 出发；同一任务可在预算内重复执行，但**不回放已完成的任务**。
- **三个必须区分的词**：
  - `harnessing`：优化模型外围运行时脚手架的过程本身；
  - `execution harness`：被改的对象；
  - `continual improvement harness` ℛ_t：指导「怎么改」的第二层对象。

### 3.6 单步算子 Φ：Propose / Probe / Compose 的确切含义

单次改进步是一个算子：`(ℋ′, e′, ξ) = Φ(q_t, ℋ, ℰ; ℛ_t)`。ξ 记录参考执行、候选与探测、组合决策。**任务级递归期间 ℛ_t 保持不变。**

| 阶段 | 实际动作 | 约束 |
| --- | --- | --- |
| 参考执行 | 先跑一次 e⁰ 揭示 harness 的**行为** | 行为不告诉你答案对错 |
| Propose | 提两个候选，各占一个 scope：**invariant**（跨领域复发的缺陷：规划、工具使用、上下文管理、委派）与 **domain-specific**（当前领域的知识、流程、约束） | 候选必须是**可执行 harness**，一次编辑 = 一个 diff；可复用 library 组件，但任务特定事实须对照当前证据核验 |
| Probe | 两候选在**同一任务、匹配的模型与环境**下执行，与参考轨迹比对 | 得到三态：缓解目标缺陷 / 行为无变化 / 引入退化 |
| Compose | 四种动作：合并有证据的改动、修订冲突、只采纳一个、**保留原状** | 每个决策必须绑定**具体观察**，不能写成「预计提升 x 分」 |

关键限定（论文自己写的）：对比**只用于指导组合，不用于验证正确性**——执行随机性与共享模型偏差限制了证据可靠性。

### 3.7 任务级递归的三形态

| 形态 | 机制 | 预算含义 |
| --- | --- | --- |
| **Parallel** | k 次参考执行 → 提 k 个 invariant + k 个 domain 候选，形成参考池 ℬ 与候选池 𝒫 → **k 个独立 composer 各拿完整池** | k 控制**宽度**，只做一次种群更新 |
| **Sequential** | 链式 k 步，每步以前一步 harness 与累积历史为条件；初始历史含 k 次 base 执行 | k 控制**深度** |
| **Selection** | k>1 时由 agentic selector 对**匿名化**交付物比较 | selector 看不到来源 harness，**不能合成新内容**，只能返回已有输出；不产生任何 harness 修订 |

被采纳的 harness 记作 ℋ⋆，**星号表示「被采纳」而非「已验证最优」**。

### 3.8 第二层递归：Reflect / Library / Reuse

1. **Reflect**：复盘完整记录 𝒟_t，提取「哪类改动解决了什么缺陷、在什么条件下、什么后果」。硬门槛：**lessons 只有被 execution contrast 支持才保留**；仅仅某个输出被选中，不足以证明可迁移。
2. **Update library**：每个条目 = 一个可执行 harness + `EXPERIENCE.md`（适用情境、lessons、支持证据）+ 检索索引。
3. **Reuse**：下一任务仍从 ℋ₀ 出发，旧组件经 Propose **选择性复用/改编**，不自动继承。**只有 library 演化，权重与决策规则固定。**

### 3.9 八个组件的落地形态（论文 Table 5）

| 组 | 编号 | 组件 | 文件 | 激活时机 | 作用 |
| --- | --- | --- | --- | --- | --- |
| 上下文构建 | C1 | System prompt | `systemprompt.md` | 每次调用 | 人格与顶层流程 |
| 上下文构建 | C2 | Guardrails | `guardrails.md` | 每次调用 | 与任务/工具无关的硬约束 |
| 上下文构建 | C3 | Memory | `LongTermMEMORY.md` | 每次调用 | 域内跨任务的持久事实 |
| 上下文构建 | C6 | Skills | `skills/<name>/SKILL.md` + `scripts/*` | skill 工具按需 | 专家流程，不触发时不占上下文 |
| 工具接口 | C4 | Tool guidance | `tool_descriptions/tool_guidance.md` | 每次调用 | 何时、如何用各工具 |
| 工具接口 | C7 | Scripts | `scripts/<name>.py` | 经 bash 运行 | 新增确定性能力，不靠逐 token 推理 |
| 编排 | C5 | Subagents | `sub_agents/<name>/AGENT.md` | task 工具 | 独立上下文窗口的 worker，只回传结果；**唯一增加推理上下文的组件** |
| 中间件 | C8 | Plugin | `plugin/<name>.ts` | 工具循环内 | hook 钩住工具调用与轮次边界，**强制**而非建议 |

设计要点：一个组件一个文件，所以「一次编辑 = 一个 diff」，可审查、可回滚。Codex 下 always-on 文本统一落在 `AGENTS.md`；初始 harness 除基准指令外为空，按需目录只放**命名该杠杆的占位符**。

### 3.10 三个基准怎么建的（论文 Appendix B）

| 基准 | 来源 | 规模 | 切分与评分 |
| --- | --- | --- | --- |
| **WorkBuddy-Bench** | 腾讯（2026） | Code 80 / Web 70 / Office 50 | 每域按类别对半切，报告 test 半：**40 / 35 / 25**；每次 rollout 全新容器；复合 verifier = 确定性检查 + LLM/VLM judge（Gemini 3.5 Flash）；分 = 通过检查比例 |
| **JobBench** | Li et al.（2026） | **65** 个端到端职业任务，7 个职业域 | 冻结 **31/34** 切分，报告 34 个测试任务；Claude Opus 4.8 按加权 rubric 判；rubric **从不**给 executor / editor / selector 看 |
| **LAB**（Harvey Legal Agent Benchmark） | Harvey AI（2026） | **1,251** 个法律任务、24 个实践领域、4 种工作类型 | 100 任务 held-out test + 24 任务 dev；judge = Claude Sonnet 4.6；指标 **Partial** = 逐准则通过率；**无可执行检查** |

数据隔离：WB 与 JobBench 的 train 半**只给 Meta-Harness**（它是离线搜索法），其余方法一律从空 library 起步、把测试任务当流处理。每个分数取 **3 条独立流的均值**。

### 3.11 完整结果（论文 Table 1 / Table 2）

**WorkBuddy-Bench 平均分（Δ 相对各自 base）**

| 执行器 | 初始 harness | Base | Meta-Harness | Harness² | Δ | k=5 再增 |
| --- | --- | --- | --- | --- | --- | --- |
| Gemini 3.5 Flash | OpenCode | 72.4 | 76.4 | **77.5** | +5.1 | +3.3 |
| Gemini 3.5 Flash | Codex | 74.5 | 73.3 | **77.5** | +3.0 | +2.9 |
| GLM-5 | OpenCode | 64.2 | 69.7 | **75.7** | **+11.5** | +3.9 |
| GLM-5 | Codex | 64.4 | 74.2 | **75.3** | +10.9 | +3.0 |

**JobBench / LAB Partial**

| 执行器 | harness | JobBench base → 改进 | LAB base → 改进 |
| --- | --- | --- | --- |
| Gemini | OpenCode | 31.5 → **36.0**（+4.5） | 79.9 → **81.9**（+2.0） |
| Gemini | Codex | 33.9 → 35.3（+1.4） | 71.6 → 75.6（+4.0） |
| GLM-5 | OpenCode | 20.6 → 24.2（+3.6） | 59.6 → **71.4**（+11.8） |
| GLM-5 | Codex | 23.6 → 24.8（+1.2） | 58.1 → 65.9（+7.8） |

七条要点：

1. **单步即全面为正**：4 配置 × 3 基准，24 个格子里没有负增益。
2. **弱执行器受益更大**：GLM-5 + 好 harness 在 WB 上反超 Gemini 的 base（75.7 / 75.3 vs 72.4 / 74.5）。即「弱模型 + 好外壳 > 强模型 + 原外壳」。
3. **例外存在**：JobBench 上反而是 Gemini 增益更大（+4.5 / +1.4 vs +3.6 / +1.2）。
4. **递归有加成**：WB +2.9~3.9，JobBench +3.0~4.2，LAB +2.6~6.7。
5. **递归并非万能**：WB 上 GLM-5 的初始 harness 间差距从 0.2 **扩大到** 1.3。
6. **未全面压制基线**：LAB 的 Gemini + Codex 配置上 Meta-Harness（76.5）领先 Harness²（75.6）。
7. **基线会退化**：ReasoningBank 在编码任务最低掉 6.6 分；Harness Scaling 在 12 个「基准 × 配置」中有 **7 个低于 base**。

### 3.12 消融

**组件消融**（Gemini + OpenCode，k=1）

| 去掉 | WorkBuddy | Δ | JobBench | Δ |
| --- | --- | --- | --- | --- |
| — | 77.5 | — | 36.0 | — |
| library | 74.4 | −3.1 | 34.2 | −1.8 |
| scoping（两编辑器不再按 scope 读轨迹） | 74.6 | −2.9 | 32.3 | **−3.7** |
| composition（探测后直接择优） | 75.9 | −1.6 | 34.1 | −1.9 |

**保留组件消融**（WorkBuddy，parallel k=5，不重跑改进，只评估交付物）

| 配置 | Code | Web | Office | 平均 | Δ |
| --- | --- | --- | --- | --- | --- |
| Base | 73.4 | 69.9 | 74.0 | 72.4 | — |
| Harness² 完整 | 80.8 | 78.3 | 83.4 | **80.8** | — |
| 去掉 prompt | 78.8 | 76.0 | 83.1 | 79.3 | −1.5 |
| 去掉 skills | 77.2 | 71.4 | 77.9 | 75.5 | **−5.3** |
| 去掉 scripts | 79.3 | 76.0 | 76.0 | 77.1 | −3.7 |

所有消融版本仍高于 base；作者明确说这只支持「多组件各有贡献」，**不构成加性效应证明**。

### 3.13 四组容易被忽略的分析（论文 Figure 5–9）

| 分析 | 结论 | 为什么重要 |
| --- | --- | --- |
| 预算曲线 | 并行 k=1→5 单调上升（Code 76.5→80.8）；**k=2 就已超过所有基线的 k=5**；Harness Scaling 从 k=2→3 反而退化 | 「多花算力」不等于「更好」，机制比数量关键 |
| 计算–性能 | 两种配置都在经验 Pareto 前沿；Meta-Harness 在 k=5 时算力更高但性能更低 | 论文给了成本轴，不是只报分数 |
| **无参照直接改会崩** | 直接精炼 base harness（无参考执行、无候选探测、无 library）：Code 73.4→**63.2**，Web 69.9→**65.0** | **凭想象改 prompt 会显著变差**，这是本仓库最该记住的一条 |
| library 的作用 | k=1 攒的 library 只能恢复到 base 水平；k=5 的 library 才带来 +0.9 / +4.4 / +5.1 | 经验要「采得够多」才有复用价值 |
| 同模型兼作 executor 与 editor | 5 个模型 × 3 基准全部提升（Opus 5 +3.3/+5.5/+3.0） | 「强编辑器蒸馏」不足以解释全部增益 |
| 案例（JobBench） | base 43.1 → 改进后 **77.8**；失败不是知识缺口而是流程缺陷（跨源文件从未对账、写完从不回读），干预 = 清点输入 + 追溯来源 + 回读脚本 | 改进的真实形态是**流程约束**，不是加知识 |

### 3.14 工程实现与运行约束（docs/SETUP.md、docs/EXPERIMENTS.md）

| 项 | 内容 |
| --- | --- |
| 三个入口 | `harness2`（精炼）/ `harness2-judge`（评分）/ `harness2-report`（报告），三者必须**同一组** bench / executor / subset / k / mode / seed |
| 预算 | 每次 rollout ≤ **200 个模型轮次**、**3,600 秒** |
| 无效编辑兜底 | 返回非法树时带 validator 消息**重试一次**；再失败则继承起始树并记 fallback |
| 续跑 | 重跑同一命令即幂等续跑；失败的评估保持「未评分」，**不计 0 分** |
| 目录隔离 | 换模型或任务选择必须换 `HARNESS2_RUNS_DIR`，否则被已保存配置拒绝 |
| 三个模型变量 | `HARNESS2_SOLVER_MODEL`（执行器）/ `_IMPROVER_MODEL`（改进器）/ `_JUDGE_MODEL`（评审） |
| 环境 | Linux + Python 3.12 + uv + gcloud ADC；WB 需 Docker；LAB 需 pandoc / poppler |
| Codex 桥 | 通过本地 LiteLLM 提供 Responses API；WorkBuddy 场景需 `--host 0.0.0.0`，文档自己标注该绑定有暴露风险 |
| **安全** | Codex 运行时**沙箱关闭**、执行器**网络不受限**；JobBench 的 `RUBRICS.json` 在 HF 公开可下；作者自陈一次 Codex rollout **在未被指示的情况下自行下载了该任务的 rubric**；建议在出口防火墙封 `huggingface.co` / `hf.co` / `github.com` |

「基准的 setup 需要这些主机」与「评测时执行器不应能访问这些主机」是两套相反的网络要求，同一台机器上要分开处理。

### 3.15 仓库暴露的问题（issues 列表页，2026-10-17 至 10-23）

6 天内 issue 编号从 #38 冲到 #136，open 28 条。三类集中：

| 主题 | 代表 issue | 说明 |
| --- | --- | --- |
| **接新 substrate** | #64「Add substrates for Pi and Claude code」8 条评论；#117 Hermes、#100 Antigravity 均 0 评论 | 只支持 OpenCode / Codex 是真实痛点；只有最早那条得到实质讨论 |
| **记账与隔离 bug** | #50（report 少算 improver 花费）、#98、#44、#121（JobBench 超时被记成正常完成）、#111（rollout 共享 npm/pip 环境与工作区）、#65（输入变更后缓存仍复用旧结果）、#80（judge 盲信 payload 里的 `all_pass`） | 一个用户连开 4 条，指向批量实验场景的可靠性 |
| 文档与上手 | #61、#59、#57、#55、#96、#106 | 「什么时候该用它」在文档里没说清 |

另有 #94 讨论「能否 pin 住某个组件使其不可移除」（5 条评论）。**本次只读到列表页，评论内容与维护者立场未核**，故对响应质量不下判断。

### 3.16 第三方批评与本文的取舍

站得住的一条批评：论文主表比较的是「强 editor + 便宜 executor」与「便宜 executor 单独」，缺了「强 executor 单独」这一列，因此增益里有多少来自**更强模型的判断被蒸馏进弱模型的外壳**，读不出来；而且每次任务都付 1 次参考 + 2 次探测 + 1 次终跑，本质是**用推理算力换准确率**。

论文的反证：Figure 8 显示同一模型既当 executor 又当 editor 时，5 个模型在 3 个基准上仍全部提升。这一条削弱但不推翻上述批评——它证明「不需要更强的 editor」，不等于「增益与额外执行成本无关」。

本文取舍：机制值得借鉴（对比式证据、双层递归、只继承经验），**数字全部当作方向参考**，不引用具体分值做任何承诺。

---

## 4. 结论（R4）

**自行实现**：只借机制，不引代码，不装依赖。

理由：

1. 引入成本高于收益。跑通它需要 Linux、Vertex 凭据、外部基准与多轮执行预算；本仓库要的只是「改规则前先给证据」这一条纪律，用文档与流程即可表达。
2. 现成替代存在。若将来真要做 prompt / 上下文自动优化，GEPA 与 DSPy 的成熟度（MIT、数十个 Release、活跃维护）显著优于 Harness²，且不需要 substrate。
3. 许可证不是问题（Apache-2.0），维护活跃度与可替代性是问题。按 `AGENTS.md` 依赖准入三项，后两项不过。

**落点**（均为文档层，不涉及业务代码）：

| 编号 | 内容 | 落点 | 是否需要 change |
| --- | --- | --- | --- |
| P0 | 规则、子 Agent 提示词、`AGENTS.md` 条款的改动，须附「改前 / 改后」在同一批任务上的对照结果；无对照视为未验证 | `AGENTS.md` 增补一句 + `docs/agents.md` 写细则 | 是（交付协议类） |
| P0 | 经验沉淀只写「规则 + 适用场景 + 证据来源」，不复用上一轮的产物文档 | `openspec/changes/<id>/completion.md` 模板补一节 | 是（同上） |
| P1 | 建最小评测集（3–5 个固定任务 + 人工判定口径），作为对照实验的底座 | 新增目录，位置待定 | 是（独立 change） |
| — | 不引入自评放行：模型不得自判改动有效，判定权留在人类验收 | 写入 P0 同一条 | 是（同上） |

### 4.1 每条落点的证据出处

| 落点 | 支撑证据（见 3.13 / 3.12） | 说明 |
| --- | --- | --- |
| P0-1 改规则须附对照 | 无参照直接精炼 base harness：Code 73.4→**63.2**、Web 69.9→**65.0** | 凭直觉改提示词会显著变差，这不是「可能变差」，是论文里最明显的负结果 |
| P0-1 对照规模不必大 | k=1 已得 +5.1；k=2 即超所有基线的 k=5 | 两组对照足以判断方向，不需要跑大规模 |
| P0-1 一个改动只动一类 | scoping 消融：不区分 invariant / domain 时 JobBench −3.7 | 通用流程改动与领域知识改动必须分开提，否则无法归因 |
| P0-2 经验三件套 | library 消融：去掉后 WB −3.1、JobBench −1.8 | 有 library 才有稳定增益，但 library 只能是「可执行件 + 场景 + 证据」 |
| P0-2 经验需多次积累才可复用 | k=1 的 library 只能把「崩掉的直接精炼」拉回 base 水平，k=5 才有正增益 | 少量任务攒出的经验不足以迁移，写进模板时要标注样本量 |
| 不自评放行 | Figure 8：同模型兼作 editor 仍提升，但论文同时声明 ℋ⋆ 只是「被采纳」不是「已验证最优」 | 机制上可行 ≠ 可以免去人工判定；本仓库判定权仍归人 |
| 规则写成流程约束 | JobBench 案例：失败是「没对账、没回读」这类流程缺陷，不是知识缺口 | 规则应约束动作顺序与校验点，不要写成知识条目 |

---

## 5. 风险与待确认（R5）

### 5.1 风险

| 风险 | 说明 | 缓解 |
| --- | --- | --- |
| 热度泡沫 | 一周涨约 1.7 万 star、仓库仅 3 次提交、无 Release、About 区无描述，热度与工程质量不匹配 | 只读机制不引代码 |
| 维护中断 | 论文配套仓库的常见结局是停更 | 同上 |
| 错误经验累积 | 反思阶段把偶然成功归因为可复用经验，会污染改进库；论文未讨论该误差累积 | 经验条目必须带证据来源，无来源不入库 |
| 成本失控 | 每任务固定 1 次基准跑 + 2 次 probe + 1 次终跑，规模越大概率越贵 | 本期不做自动 preflight，只做人工对照 |
| 规则膨胀 | `AGENTS.md` 已自我限定只放门禁，再加细则会违反自身边界 | 细则落 `docs/agents.md`，`AGENTS.md` 只留一句引用 |
| 评测偏差 | 若判定由模型完成，分数带评审器偏差 | 判定权留人 |

### 5.2 待用户确认

- **Q1**：是否在 `AGENTS.md` 增加「规则与子 Agent 提示词改动须附对照证据」一条？不加则 P0 无法落地。
- **Q2**：是否愿意为对照实验付出成本（同一批任务跑两遍）？不付出则「对比证据」只是一句空话，建议同步砍掉 P0。
- **Q3**：是否允许调用外部模型 API 做 probe？涉及数据外发，需明确边界。
- **Q4**：`.codebuddy/agents/*` 的提示词是否纳入版本管理与证据管理（目前已在 git 内，但无改动验证要求）。

**Q1–Q3 未闭环前，不进入实现，不开 change。**

---

## 6. 参考链接

- 仓库：<https://github.com/google-research/harness2>（Apache-2.0）
- 项目主页：<https://harness2.github.io/>
- 论文：<https://arxiv.org/abs/2610.14224>（arXiv:2610.14224v1 [cs.AI]，2026-10-09 提交，CC BY 4.0，未见会议录用标注）
- 论文全文（HTML）：<https://arxiv.org/html/2610.14224v1>
- 作者机构：Northwestern University + Google Cloud AI Research（据项目主页；arXiv 摘要页未列机构）
- 安装文档：<https://github.com/google-research/harness2/blob/main/docs/SETUP.md>
- 实验文档：<https://github.com/google-research/harness2/blob/main/docs/EXPERIMENTS.md>
- Issues：<https://github.com/google-research/harness2/issues>
- 对照候选：<https://github.com/gepa-ai/gepa>、<https://github.com/stanfordnlp/dspy>、<https://github.com/kayba-ai/agentic-context-engine>

### 未验证项清单

- `google-research/reasoning-bank` 的 LICENSE 类型与 star 数。
- Meta-Harness、Harness Scaling 是否存在公开代码。
- OpenCode、Codex CLI 的许可与沙箱默认行为。
- 所有 star / 提交数 / 最后推送时间的精确值（二手聚合站，采集时间不一）。
- 论文 Appendix E.3–E.7（跨 executor 迁移、工具使用可靠性、替代生产 harness、**失败分析**、定性案例）：HTML 抓取在 E.2 处截断，未读到。
- 论文正文与附录 A–E.2 中**没有独立的 Limitations 章节**；3.15 与 3.16 的边界描述来自散落正文与第三方批评，不是作者集中声明。
- issues 只读到列表页，**评论内容与维护者立场未核**；#64（Claude Code / Pi substrate）的 8 条评论是否包含官方承诺未知。
- 「6 天内 issue 从 #38 冲到 #136」依据列表页可见编号，页面实际只渲染 25/28 条，完整时间线未核对。
- 论文 Table 4 的六维定位对比（对 12 个离线方法 + 5 个混合方法）仅读到结论，未逐行核对被比较方法的实现差异。

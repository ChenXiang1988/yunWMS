# 多 Agent 协作约定

本文件定义 yunWMS 的 agent 分工：主 Agent 负责编排与验收，子 Agent 负责执行。

与 `AGENTS.md` 的关系：`AGENTS.md` 是交付协议（分支、提交、禁止项、验证），适用于所有 agent；本文件只定义**谁做什么、怎么派活、怎么回收**。两者冲突时以 `AGENTS.md` 为准。

本阶段落地 1 个主 Agent + 3 个子 Agent，按**验证命令、规则集、路径白名单三者都不同**切分：

| Agent | 权限 | 路径 | 完成判据 |
|---|---|---|---|
| `backend-implementer` | 写 | `code/backend/**` | `./mvnw clean verify` |
| `frontend-implementer` | 写 | `code/frontend/apps/**` | 目标 app 的 `build` + `typecheck` |
| `delivery-auditor` | 只读 | — | 逐项结论 + 证据路径 |

不合并成一个 Agent：合并后白名单会退化成 `code/**`，「越界即失败」这条约束随之失效。

PDA、测试、文档不单独设 Agent——`apps/pda` 尚未建立，测试代码跟随对应实现，文档属 `chore/` 级由主 Agent 自己做。

**三个是三个角色，不是三条并行通道。** 同步派发时主 Agent 会被挂起，同一时刻只有一个在跑。

---

## 一、主 Agent：职责

主 Agent 是与用户对话的那个会话。它按顺序做五件事：

1. **沟通** —— 向用户澄清需求。不猜业务口径，不猜编码规则。
2. **确认** —— 把三道门禁的待确认项闭环：调研 R5 未确认项、proposal 的范围与非目标、design 的被否方案。
3. **派活** —— 把 `tasks.md` 的单条任务转成任务包（见第二节）。
4. **检查** —— 子 Agent 回传后按第五节检查单核对；`change/*` 合并进 main 前，还必须派一次 `delivery-auditor` 并把结论落成 `audit.md`（见第八节）。自查不算审计：自查的人正是改动的人。
5. **汇报与提交** —— 向用户说明「改了哪些文件 / 怎么验证的 / 还有什么没做」，并由主 Agent 统一 git 提交，以保证 Conventional Commits 格式与单次 ≤400 行。

### 禁止项

- **禁止直接做业务功能实现**，业务功能一律派发给子 Agent。
- **例外按改动性质判定**：主 Agent 可直接做**基建与文档类改动**——文档、`docs/research/` 调研、`.codebuddy/` 配置、构建配置（`pom.xml` 等）、CI 配置。
  **判据是分支类型**：能进 `chore/<简述>` 分支的，主 Agent 自己做；需要 `change/<change-id>` 分支的，派发。
  > 不枚举路径清单，是因为清单会越拉越长且必然漏项（本次给 `pom.xml` 加 flyway 插件即为例外未覆盖的情形）；「基建 vs 功能」比「文件在不在名单里」更好判断。
- **无论哪一类，以下一律派发**：业务代码、数据库迁移、测试代码。
  > `delivery-auditor` 机检判据：本次提交所在分支类型 + 是否触碰业务代码 / 迁移 / 测试。
- 不替子 Agent 做技术决定，不绕过 research-first 门禁。
- 不修改测试断言来让检查通过。
- 不把未经消解的指令冲突下传给子 Agent。

---

## 二、派活契约（任务包 9 项）

派给子 Agent 的任务包必须含以下九项，缺一项即视为不合格任务包：

| # | 项 | 内容 |
|---|---|---|
| 1 | 目标 | 一句话，对应 `tasks.md` 的某一条 |
| 2 | 路径白名单 | 允许改动的路径；越界即判定失败 |
| 3 | 前置上下文 | change 路径、相关 spec/design 段落、已引用的 `docs/research/<主题>.md` |
| 4 | **冲突预检** | 派活前主 Agent 自查任务包是否与 `AGENTS.md`、`.codebuddy/rules/`、本 change 的 `design.md`/`specs` 冲突。有冲突先消解或找用户确认，**不得下传** |
| 5 | 完成标准 | 编译 + 单测 + 静态检查通过，给出可复制的验证命令 |
| 6 | 禁止项 | 跨模块直接依赖、顺手改范围外文件、改测试断言、迁移与代码混合提交 |
| 7 | 交付格式 | 改动文件清单 + 验证命令及结果 + 遗留问题 |
| 8 | **工作目录与基线锚点** | 子 Agent 的工作目录（绝对路径）+ 派活时刻的 anchor；缺此项，越界核对不成立，见第十三节 |
| 9 | **预估变更规模** | 派活前估算增删合计行数。**预估 > 400 行时 MUST 拆任务包**，拆不动说明任务本身该拆 |

第 9 项是行数超限的**唯一有效修法**。审计只能发现超限，无法消除超限——把超限记成审计结论，等于用流程记录失败。历史三笔（1536 / 542 / 829 行）都是派活时没有估算规模造成的。

并行派多个子 Agent 时：各自占一条 `git worktree`，各自独立 anchor，且路径白名单用绝对路径、不得重叠。

---

## 三、冲突裁决

优先级从高到低：

```
AGENTS.md  >  .codebuddy/rules/  >  change 的 design.md / specs  >  任务包
```

任务包层级最低，意味着**任务包永远不能覆盖 rules**——这条用来挡住「为了赶进度在任务包里放宽 400 行限制」这类自我松绑。

冲突消解是主 Agent 的派活前职责。子 Agent 在执行中若发现新的冲突（例如 design 与存量代码不一致），**不得自行裁决**，立即返回主 Agent 并报三件事：

1. 哪两条冲突（原文引用）
2. 涉及的文件路径
3. 建议以哪条为准及理由

---

## 四、审批分层

核心区别：**用户审批的是范围，不是每一次工具调用**。

| 阶段 | 谁在场 | 批什么 |
|---|---|---|
| 派活前 | 主 Agent + 用户 | 主 Agent 审批任务包是否合格（冲突预检、白名单、禁止项）；重大任务由用户审批边界 |
| 执行中 | 仅子 Agent | 在任务包边界内自主执行 |
| 回收后 | 主 Agent + 用户 | 主 Agent 按检查单核对是否越界；用户做最终验收 |

### 为什么不由主 Agent 审批子 Agent 的工具调用

- **机制上做不到**：`enabledAutoRun` 是 IDE 层的权限闸门，不是 agent 之间的授权关系。agentic 执行期间主 Agent 被挂起等待结果，没有介入窗口去批任何一步。
- **原则上不该做**：AI 批准 AI 的工具调用等于自我授权——审计者的操作由被审计者授权，闸门失效。

### 按可逆性分级

| 操作 | 闸门 |
|---|---|
| 只读（读文件、搜索、`git diff`） | 自动批准。无副作用，人类逐次点击不提供判断力，只提供点击 |
| 写操作（改代码、新建文件） | 由任务包的路径白名单约束，回收时用检查单核对 |
| 不可逆（`git commit`、删除、`deploy`） | 保留在人类手里 |

因此**只读子 Agent 一律 `enabledAutoRun: true`**：风险由「无写入权限」这个边界本身承担，而不是由逐次点击承担；且「不可中途干预」使得逐次审批必然造成频繁打断。

实现类子 Agent 的 `enabledAutoRun` 待定——它涉及写操作与命令执行，需在「不打断」与「不可逆操作有人兜底」之间权衡，等第二个子 Agent 落地时再定。

---

## 五、回收检查单

子 Agent 回传后，主 Agent 逐项核对。**第一条可机检，其余需要人判断：**

- [ ] **`openspec/changes/<change>/audit.md` 是否存在**（分支合并进 main 前必须有一份，见八节）
- [ ] `git diff <anchor> --stat` 是否只落在白名单路径（anchor 由任务包给出，auditor 不得自行推断基线）
- [ ] 单次提交 ≤400 行（**唯一口径**，`AGENTS.md` 与 `.codebuddy/rules/` 均指向此处，其余各处不得另立豁免）—— 口径：**对每笔提交分别**计算增删合计（`git log -1 --numstat <sha>` 求和），排除生成物（`primitive.css`、`docs/api/inventory.md`、`docs/api/openapi.json`、`pnpm-lock.yaml`）。「不含自动生成代码与纯格式化」不适用于本项，逐案解释等于没有上限
- [ ] 数据库迁移是否与代码分离
- [ ] 是否引入 GPL/AGPL/SSPL 依赖（`xxl-job`、`jee-wms`、MySQL、Redis 7.4+）
- [ ] 前端是否出现色值字面量或第二套 UI 组件库
- [ ] 是否修改了测试断言
- [ ] 交付三件事是否齐全
- [ ] 对应 `tasks.md` 条目是否可勾选

**存量违约（2026-10-25 全量扫描）**：140 次提交中 14 次超过 400 行，最大 6978 行。清单与逐笔明细见 `docs/research/rule-governance.md` 第二节，可用 `sh code/tools/check-commit-size.sh` 复跑。处理方式与既有三笔一致：**不重写历史，只登记**。改写已合并提交会破坏所有并行 worktree 的基线，代价大于收益。

---

## 六、子 Agent 登记表

新增子 Agent 必须登记，登记后才能派活。

| 字段 | 说明 |
|---|---|
| name | 唯一标识，与文件名一致 |
| 模式 | agentic（本工作区实现与审计一律 agentic；manual 会替代主 Agent，破坏编排结构，不使用） |
| 职责 | 单一职责 |
| 触发条件 | description 中写明的时机 |
| 路径白名单 | 越界即失败 |
| 模型 | 本阶段留空，跟随默认 |
| 上线日期 | 登记日期 |
| 探针结果 | 见第七节 |

### 在册子 Agent

| name | 模式 | 职责 | 触发条件 | 路径白名单 | 模型 | 上线日期 | 探针结果 |
|---|---|---|---|---|---|---|---|
| `delivery-auditor` | agentic | 只读审计，产出 `audit.md` | **`change/*` 合并进 main 之前必须执行一次**（anchor 取 `git merge-base main HEAD`）；另对单次 >400 行的提交做抽查 | 只读，无写入权限 | 留空 | 2026-10-24 | 2026-10-24 首次任务实测，见第十节 |
| `backend-implementer` | agentic | 按任务包实现后端 Java 代码 | 主 Agent 完成冲突预检并下发单条实现任务包时 | `code/backend/**` | 留空 | 2026-10-24 | 2026-10-24 首次任务实测，见第十一节 |
| `frontend-implementer` | agentic | 按任务包实现前端页面与交互 | 主 Agent 完成冲突预检并下发单条前端任务包时 | `code/frontend/apps/**` | 留空 | 2026-10-25 | 待首次任务实测 |

定义文件：`.codebuddy/agents/` 下同名 `.md`。

**`delivery-auditor` 的触发条件刻意写成钩子而非条件句。** 原先写的是「主 Agent 需要核对时」——需不需要由主 Agent 自己判断，于是永远可以判定为不需要。30 个提交里它一次都没被调用。改成「合并前必须有一次」后，判断权从主观意愿转成客观节点。

纯 `docs/` 与 `code/tools/` 变更可免审，不触碰 `code/` 与迁移。

### 实现类 Agent 的 `enabledAutoRun` 决定

早前标记为「待定」，现定 **`true`**。

理由：agentic 调用期间主 Agent 被挂起，无法介入；而一次实现任务通常包含数十次工具调用，逐次审批只会造成频繁打断，不提供判断力。真正的不可逆边界改由两条约束承担：

1. **路径白名单**：写操作被限制在任务包给定范围内
2. **System Prompt 硬禁不可逆命令**：`git add/commit/push`、删除、部署一律禁止，提交统一由主 Agent 执行

回收时用第五节检查单核对是否越界。若首次实测发现越界，再收回到 `false` 并改为按步审批。

---

## 七、模型准入探针

**本阶段所有子 Agent 的 `model` 字段留空**，跟随默认。理由：项目尚无实测数据，此时分配型号是猜测；硬编码型号会在模型改名或下架时变成债务。

需要换模型时，先用探针实测，不靠排行榜或口碑。

### 五个观测维度

| 维度 | 指什么 |
|---|---|
| 约束遵守率 | 任务包给 N 条硬性禁止项，任务结束时还剩几条在执行 |
| 长程一致性 | 到多少上下文长度开始遗忘开头放的指令（lost in the middle） |
| 范围自律 | 会不会自己扩大改动范围（顺手重构） |
| **阻塞行为** | 遇到信息缺口或指令矛盾时，是停下来返回主 Agent，还是自行编造继续写 |
| 工具调用稳定性 | 多步调用不写错参数、不陷入重复循环 |

其中**约束遵守率**与**阻塞行为**最关键：其余三条影响效率，这两条直接决定交付物能不能要。

### 探针设计

1. 挑一个约 30 分钟的真实小任务。
2. 埋 5 条约束，其中 2 条放在任务包中段、1 条放在最开头（测长程一致性）。
3. 埋 1 个信息缺口（测阻塞行为）。
4. 给路径白名单（测范围自律）。
5. **同一任务跑 3 次**，统计：约束遵守率 / 停问率 / 越界次数 / 需人工中断次数。

先用默认模型跑一轮拿基线，不预设阈值；之后换任何模型都用同一探针对比。

---

## 八、机制约束与已知未验证项

> **关于调研文档**：Subagents 机制属工具链配置，不是业务功能域，因此**未**单独立 `docs/research/<主题>.md`，结论直接记于本节。
> 理由：① 它不产生 capability，没有 proposal 会引用它；② 候选集只有 CodeBuddy 自带机制一个，套 research-first 的候选比较表会写成凑数；③ 机制行为变更时要两边同步维护，反而容易变成陈旧文档。
> 相关取舍（agentic 与 manual 的取舍、`model` 留空的理由）均在本节。**记下「为什么省略」，比补一份没人读的文档更可回查。**

### 已确认的约束（CodeBuddy Subagents 机制）

- 子 Agent 存为 Markdown，项目级放 `.codebuddy/agents/`。
- **agentic 调用后不可中途干预**：只能等它跑完或中断。因此任务包粒度必须 ≤2 小时，与 `openspec/config.yaml` 的 tasks 纪律一致。
- frontmatter 必填：`name`、`description`（agentic 的路由完全依赖它）、`agentMode`、`enabled`。
- **子 Agent 不直接与用户对话**，其输出只回传给主 Agent；信息由主 Agent 转达。

### 未验证项（不要依赖，需实测）

- 子 Agent 是否自动继承 `AGENTS.md` 与 `.codebuddy/rules/`：官方文档未说明。因此每个子 Agent 的 System Prompt 第一行都要求「开工前先读 `AGENTS.md` 与对应 rule」，并把最硬的约束直接抄进 System Prompt。
- `model` 字段留空时的默认行为：官方文档未说明。
- **`tools` 字段的合法工具名**：官方文档只示例 `WebFetch, WebSearch`，未给完整清单；`.codebuddy/skills/` 用的是另一套字段 `allowed-tools: Bash(openspec:*)`，两者是否同语法未知。因此 `delivery-auditor` **暂不设置该字段**——盲写可能导致它只剩两个网络工具、读不了文件，比不收窄更糟。要启用需先实测：设定白名单后派一个只读任务，确认它仍能读文件、跑 `git diff`、列目录，缺一即回退。

### 审计必须留下产物

CodeBuddy 机制无法在 `git commit` 时自动唤起子 Agent，能强制要求的只有**产物**。因此审计结论必须落盘：

`openspec/changes/<change>/audit.md`，归档时随 change 进入 `archive/`，之后可回查。

### 审计输入包

派发审计时必须一次给全：

- `anchor`：`git merge-base main HEAD`，不得让 auditor 自行推断
- 允许路径 glob（`--allow`，可重复）
- 禁止扩展名（`--forbid-ext`，可重复）
- 生成物 glob（`--generated`，可重复）
- change 名（`--change`，用于 OpenSpec validate）
- 明确禁止：写文件、`tee`、shell 重定向、临时文件、构建、测试、安装依赖、Git 写操作

### 只读证据脚本

先由主 Agent 或 auditor 运行只读脚本，把 JSON 作为事实输入：

```bash
# 提交前：先 git add，再审计 staged diff
python3 code/tools/audit-delivery/audit_delivery.py --anchor main --staged \
  --change <change-id> \
  --allow 'openspec/changes/<change-id>/**' \
  --forbid-ext .java --forbid-ext .sql --forbid-ext .vue --forbid-ext .ts \
  --forbid-ext .tsx --forbid-ext .css

# 提交后：审 anchor..HEAD 的每笔提交
python3 code/tools/audit-delivery/audit_delivery.py --anchor main \
  --change <change-id> \
  --allow 'openspec/changes/<change-id>/**'
```

脚本只向 stdout 输出 JSON，不写文件。`blocking` 是必须处理项，`unknowns` 是证据不足项，统计数字必须由脚本复算，不靠人工数。

### 结论分级

- **阻塞**：白名单越界、禁止扩展名、生成物改动、单笔 >400 行、OpenSpec 校验失败
- **建议**：不阻塞但需主 Agent 决定的事项
- **无法判定**：未执行的构建、测试、数据库、浏览器或外部环境验证

### 两段式审计

1. 提交前审计 staged diff，先修再提交。
2. 提交后审计 `anchor..HEAD`，核对提交拆分与累计范围。
3. 主 Agent 必须重新读取被审计文件；审计建议只能作为线索，不能直接照改。

### 审计记录模板

```markdown
# 审计记录

- change: <change-id>
- anchor: <commit>（取 `git merge-base main HEAD`）
- 审计时间：YYYY-MM-DD

## 逐项结论

| 检查项 | 结论 | 证据 |
| --- | --- | --- |
| 路径白名单 | 通过 | 脚本 JSON `violations.outsideAllowlist` 为空 |
| 单次提交 ≤400 行 | 不通过 | `c1b5936` 增删合计 542 行 |
| 迁移与代码分离 | 通过 | 无迁移改动 |
| 违规依赖 | 通过 | 本轮无新增依赖 |
| 测试断言 | 通过 | `git diff <anchor> -- '**/test/**'` 只新增用例，无既有断言被削弱 |
| 生成物 | 通过 | `docs/api/openapi.json`、`inventory.md` 未改 |

## 存量违约

| 提交 | 违规 | 处置 |
| --- | --- | --- |
| `7b1e705` | 1536 行；一提交做两件事 | 已进 main，登记不改历史 |

## 结论

通过 / 不通过 / 无法判定（附原因）
```

**为什么必须落盘**：没有产物就无法证明「审过」，也无法在归档后回答「这个 change 当初审出过什么」。检查单第一条查的就是这个文件存在与否。

**行数问题的正确修法在动手之前**：派活时任务包加一条「预估变更 > 400 行就拆任务包」。审计只能发现超限，不能消除超限——把超限记成审计结论，等于用流程记录失败。

---

## 九、未尽事项（后续 change 候选）

以下内容本次不落地，登记在此，等各自的 change 处理：

- **资料分层治理**：审查 L0 常驻层（`AGENTS.md` + alwaysApply rules + Memory）、L1 按需层（skills、子 Agent）、L2 引用层（`docs/research/`、`specs/`）的分层是否合理。待查项包括：L0 体积基线、alwaysApply 的必要性复核、`docs/research/` 是否有索引与孤儿文档。

---

## 十、首次派活实测记录（2026-10-24）

**场景**：派 `delivery-auditor` 盘点 `init-wms-foundation` 的真实完成度（52 条任务）。

### 通过项

- **只读约束有效**：执行后 `git status` 显示 `code/**`、`openspec/**` 无任何改动，仅主 Agent 自身的改动在位。
- **冲突不自行裁决**：发现 4 处冲突（任务条数与任务包不符、compose 文件位置与 `design.md` D0 不符、认证中间件选型「设计已决策 vs 实现走另一条」、任务 1.4 的验证命令与工程实际不符），全部按约定回报主 Agent，未自行裁决。
- **阻塞行为正确**：遇到口径矛盾不硬编，按唯一真源全量覆盖后回报差异。
- **交付格式合规**：结论摘要 + 逐条对照 + 遗留问题三段齐全，每条结论附证据路径。

### 发现的问题与改进

| 问题 | 改进 |
|---|---|
| 回传过长被截断：52 条逐条表格超出单次回传上限，主 Agent 需额外读落盘文件才能看到遗留问题 | 任务包要求「结论摘要前置 + 表格按节分组 + 每条不超过一行」；超篇幅时按节分批回报 |
| 主 Agent 派活前的事实核对疏漏：任务包写 47 条，实际 52 条 | 派活前对可数事实（任务条数、文件数、表数）先数一遍，不凭印象 |
| 单次 79 次工具调用、约 26 分钟 | 仍在 ≤2 小时粒度内可接受，但全量盘点偏大，后续按节拆分派发 |

### 结论

- 主 Agent → 子 Agent 的回路已跑通，机制可用。
- **暂不增加实现类子 Agent**：本次审计暴露的设计与实现分歧（尤其是认证中间件选型）尚未裁决，此时派实现类 Agent 会按可能错误的选型施工。待用户裁决后再决定第二个子 Agent。

---

## 十一、`backend-implementer` 首次派活实测记录（2026-10-24）

**场景**：将 `wms-auth` 的认证基础设施从 Spring Security 切换为 Sa-Token（对应 `design.md` D11 与 `docs/research/auth-middleware.md` R4）。

**规模**：84 次工具调用，约 39 分钟，改动 5 个文件（2 删 2 增 2 改），净 +36 行。

### 按五个维度实测

| 维度 | 结果 | 证据 |
|---|---|---|
| 约束遵守率 | **5/5** | 改动全在白名单内；无 git 写操作；未改测试断言；未新增迁移；未引入白名单外依赖 |
| 长程一致性 | 良好 | 39 分钟后仍在执行「不新增依赖」「不越界」等任务包开头给出的约束 |
| 范围自律 | 良好 | 未顺手重构 `ApiResult`/`ErrorCode`；主动声明 `docs/agents.md` 与子 Agent 定义文件的白名单外改动不属于它 |
| 阻塞行为 | **正确** | 发现调研文档给的坐标与实际不符（`sa-token-spring-boot-starter` vs `spring-boot3-starter`）时，未自行改文档，而是上报待裁决 |
| 工具调用稳定性 | 良好 | 无重复循环，无参数错误 |

### 主 Agent 回收核对（未采信报告，独立复验）

| 检查项 | 结果 |
|---|---|
| `git status` 是否越界 | 无越界 |
| 单次提交 ≤400 行 | 满足（净 +36 行） |
| 迁移与代码分离 | 无迁移改动 |
| 违规依赖 | `sa-token 1.46.0` 为 Apache-2.0，父 pom 许可已核对 |
| 修改测试断言 | 无测试文件，不适用 |
| 构建 | `./mvnw clean verify` BUILD SUCCESS（主 Agent 独立执行） |
| 运行时 | health 200 / api-docs 200 / swagger-ui 200 / `/api/anything` 401 且响应体逐字节符合预期（主 Agent 独立执行） |

### 结论

实现类子 Agent 可用，`enabledAutoRun: true` 未造成越界，维持该设置。

### 两个遗留决策

1. `docs/research/auth-middleware.md` R4 写的 Boot 3.x 坐标有误（应为 `sa-token-spring-boot3-starter`）。该文档是快照，按 `docs-convention.md` 不回改正文；头部「时效」已声明以仓库现状为准。
2. Sa-Token 会话存储当前是默认内存实现，重启即失效、多实例不共享。落登录（任务 2.1）前需决定是否引入 `sa-token-redis-*` 并走依赖准入。

---

## 十二、并行会话的工作目录隔离

### 为什么需要

两个会话在同一个目录工作时，即使改的是不同文件，也会互相踩到这些共享状态：

| 共享状态 | 冲突后果 |
| --- | --- |
| `git` 工作区与暂存区 | A 会话 `git add` 后 B 会话提交，会把 A 的改动一起提交 |
| `target/`、`node_modules/` | 一方 `clean` 或重装依赖，另一方构建失败 |
| 同一个文件 | 后写覆盖先写，且没有任何提示 |

2026-10-24 出现过一次：一个会话改规格、另一个改认证代码，靠「恰好改不同文件」没撞车。这是运气，不是机制。

### 怎么做

每个并行会话用一条独立 worktree：

```bash
# 主工作区保持在 main，用于合并与验收
cd ~/yunWMS

# 会话 A：拉一条 worktree 并切到自己的分支
git worktree add ../yunWMS-owner -b change/implement-owner
cd ../yunWMS-owner

# 会话 B：另一条，路径与分支都不重叠
git worktree add ../yunWMS-sku -b change/implement-sku
cd ../yunWMS-sku
```

要点：

- **一条分支一条 worktree**，同一个分支不能被两条 worktree 同时检出，`git` 会直接拒绝。
- **路径必须在主仓库之外**（`../` 开头），放在仓库内会被 git 当成嵌套仓库。
- **路径白名单按 worktree 划分**：`implement-owner` 的会话只改 `code/backend/wms-auth`、`wms-master/**/owner*` 等，不得改另一条 worktree 正在改的文件。
- **依赖各自安装**：`node_modules` 不共享，各自 `pnpm install`。

### 回收

会话合并回 `main` 后立即清理：

```bash
cd ~/yunWMS
git worktree remove ../yunWMS-owner
git branch -d change/implement-owner   # 已合并才可 -d
```

遗留的 worktree 用 `git worktree list` 查看，`git worktree prune` 清理已删除目录的登记。

### 不适用的情况

串行推进（做完一个切片再做下一个）不需要 worktree，直接在一条分支上按顺序提交即可。**新开 worktree 的成本是每份都要装依赖**，不为并行而并行。

---

## 十三、工作区归属与基线锚点

### 问题

主 Agent 会写（文档、`.codebuddy/`、构建配置），子 Agent 会写（业务代码），两者共用同一工作目录。回收时 `git diff --stat` 只反映「当前工作区 vs HEAD」，无法区分哪些改动出自本次任务包。工作区越脏，越界核对越不可信——别人的在途改动会被算到本次任务头上，真正的越界反而被淹没。

### 成立前提：同步派发在时间上是串行的

`Task` 同步调用期间主 Agent 被挂起（第四节），子 Agent 运行期间主 Agent 不写文件。因此：

> **子 Agent 的产出 = 从 anchor 到回收时刻的全部 diff，归属唯一。**

**Team Mode（带 `name` 的异步成员）不满足此前提**：主 Agent 不被挂起，可以继续写，anchor 方案失效，必须改走 worktree 物理隔离（第十二节），且每个成员独立 anchor。

### 实测（2026-10-24）

在一条消息里同时发起同步 `Task`（只读子 Agent）与一次写操作，观测时序：

| 事件 | Unix 时间 |
| --- | --- |
| 派发前最后一次记录 | 1792851565 |
| 子 Agent 耗时 | 2.77s |
| 写操作实际执行 | 1792851574（派发后 9s） |

写操作在子 Agent 返回之后才执行，两者没有重叠。据此确认：**同步派发时主 Agent 的写操作与子 Agent 的执行串行**，anchor 的「产出 = anchor 之后全部 diff」成立。

**证据强度**：这是单次观测，不是机制保证。若 IDE 改为并行执行同一条消息内的多个工具调用，或改用 Team Mode，该结论失效，必须重新实测。受影响的范围仅限「同一条消息内并发多工具」，不涉及「子 Agent 之间」的并发——后者始终需要 worktree 隔离。

### 常态：收敛优先

派活前尽量把已完成且自洽的改动提交掉，把「未提交」窗口压到最短：

> **归属模糊的时间窗口 = 未提交改动的存活时间。**

最理想的派活状态是「工作区干净」或「工作区只剩本任务包的在途改动」。收敛不了（改动编译不过、属于另一条待提交的工作）时才用 anchor 兜底。

### 兜底：anchor

```bash
# 派活前，在目标工作目录执行
ANCHOR=$(git stash create)
git update-ref refs/baseline/<task-id> "$ANCHOR"
git for-each-ref refs/baseline          # 确认已固定
```

要点：

- `git stash create` 只创建 commit 对象，**不改工作区、不动 `refs/stash`**；工作区干净时返回空串，此时 anchor 直接用 `HEAD`。
- 必须 `git update-ref` 固定，否则该提交是 dangling 的，会被 gc 回收。
- `refs/baseline/*` 不属于任何分支，不随 `git push` 上传。
- 回收后清理：`git update-ref -d refs/baseline/<task-id>`。
- anchor 是快照，`git diff refs/baseline/<task-id>` 只给「相对派活时刻」的增量；**工作区原有的在途改动必须同时列成清单写进任务包**，否则会被误记为子 Agent 的产出。

### 核对

第五节的越界核对一律以 anchor 为基线，anchor 由任务包给出，**auditor 不得自行选基线，也不得用 `HEAD` 顶替**。若任务包的 anchor 与实际工作区对不上（例如 `git cat-file -e` 失败），auditor 直接返回主 Agent，不自行修复。

### 主 Agent 自身改动的收尾

顺序只有一种是无损的：**先提交主 Agent 的改动 → 再记 anchor → 再派活**。

放宽到「不提交也能派」的条件只有一个：主 Agent 的在途改动与子 Agent 的改动**不在同一文件**。此时 anchor 能区分，回收后按文件分两次提交。

**同一文件内既有主 Agent 的未提交改动、又有子 Agent 的改动时，`git` 无法按归属拆分**——这视为派活前收敛失败，不得派活，须先提交或先撤销主 Agent 那部分改动。

判据：任务包的路径白名单与主 Agent 在途改动的文件清单相交即冲突，派活前必须消解。

# DSH 工作区约束：官方依据与落地说明

| 项 | 内容 |
| --- | --- |
| 适用版本 | DeepSeek Harness `0.2.1-rc.3`（当前 Desktop 内置运行时） |
| 依据 | 官方 GitHub 仓库 tag `dsh-v0.2.1-rc.3`、官方文档站、随包 README 与 base 组合配置 |
| 整理日期 | 2026-10-30 |
| 用途 | 团队配置、评审、上线核对；不是官方原文的替代品 |

> **先说明一个前提**：DSH 官方没有一篇名为《工作区约束》的单页指南。相关规范分散在「进程沙箱子系统」「文件系统子系统」「权限预设」「用户审批」「工作目录」「CLI 参考」「包 README」中。本材料按主题汇编，逐条标注来源；官方没有写到的内容集中放在第 7 节，不做推测。

> **一句话结论**：DSH 的工作区约束，是以**会话创建时的 `cwd` 为主写入根（另加平台临时区域）**、**按每次调用解析**、由**进程沙箱与文件系统围栏强制执行**的「文件写入围栏」；需要升级时再加一层**一次性审批**。它不把读取、网络或进程可见性纳入约束范围，也不是通用安全沙箱。

> **使用顺序**：§0 速查 → §1–2 理解边界 → §3 配置与验证 → §5 越界处置 → 附录 B 上线核对。

---

## 0. 速查卡

| 问题 | 官方答案 | 来源 |
| --- | --- | --- |
| 工作区边界是什么？ | 会话的不可变 `SessionHeader.cwd`；可写范围是该根目录加平台临时区域 | [S1]、[S3] |
| 默认是什么模式？ | 随附 base profile 默认 `workspace-write` + `ask`；`dsh-sandbox-policy` 插件自身的故障安全默认是 `read-only` | [S17]、[S3]、[S12] |
| 读取会受工作区限制吗？ | 不会。所有模式下读取都不受沙箱围栏限制 | [S4]、[S12] |
| 写工作区外会怎样？ | 结构化拒绝；模型看到 `[sandbox: file access denied under <mode> mode]` | [S4]、[S7] |
| 确实需要写外面怎么办？ | 用最窄的更宽模式重试**同一次**操作，并附 `justification`；由审批应答者决定，只批一次 | [S5]、[S7] |
| 没有可用沙箱后端呢？ | `SANDBOX_UNAVAILABLE`，失败关闭，绝不无隔离运行 | [S5]、[S7] |
| 什么不在工作区约束覆盖范围内？ | `danger-full-access`、Web UI 的用户终端、插件/MCP 宿主代码；读取与网络本身也不受围栏限制 | [S1]、[S13]、[S22]、[S12] |
| `AGENTS.md` 算硬约束吗？ | 不算。它是模型可见的指导，不覆盖 system/developer/用户直接指令 | [S10] |

---

## 1. 官方约束原则与适用范围

### 1.1 七条原则

1. **默认收紧，显式放宽。** `dsh-sandbox-policy` 的故障安全默认是 `read-only`；需要 agent 写工作区的部署必须显式选择 `workspace-write`。随附 base profile 是例外：它显式把新会话固定为 `workspace-write` + `ask`。[S3]、[S17]
2. **失败关闭（fail-closed）。** 没有可用后端时以 `SANDBOX_UNAVAILABLE` 拒绝，而不是无隔离运行；没有可用审批应答者时以 `unavailable` 拒绝，而不是默认放行。[S5]、[S8]
3. **逐调用策略、逐会话状态。** `SandboxPolicy` 随每次调用携带，不固定在提供方上；会话模式写入会话日志，两个会话不会看到彼此状态；获批的升级只是「用更宽策略发起的一次新调用」。[S1]、[S3]
4. **单一可写根来源。** bash 与文件系统两条能力族从同一个 `writableRoots` 推导白名单，避免「write 工具写不了 `/tmp`、bash 却能写」的漂移。[S4]、[S6]
5. **最小必要升级。** 升级阶梯是封闭表：`read-only` 只能升到 `workspace-write` 或 `danger-full-access`；`workspace-write` 只能升到 `danger-full-access`；更窄的目标无效；重复当前模式无需审批。模型应选择「足以放行的最窄更宽模式」。[S5]
6. **边界诚实。** 模式词汇只覆盖**文件效果**；网络、进程可见性不在词汇内；in-process fs 围栏是可信代码中的策略检查，不是内核边界；官方安全说明明确「沙箱、审批与权限控制降低风险，但不保证隔离」。[S1]、[S4]、[S15]
7. **软硬分离。** `AGENTS.md` 是工作区指导，按「更具体优先」生效，但不覆盖 system、developer 或用户直接指令；真正的硬边界由沙箱与审批承担。[S10]

### 1.2 适用范围

| 能力 / 对象 | 是否受工作区写围栏 | 官方说明 |
| --- | --- | --- |
| `write` / `edit` 文件工具 | **是** | `dsh-fs-sandbox` 只围栏写入与编辑；读取、列目录、元数据、只读监听与 `fs-local` 完全一致。[S4] |
| 单次 `bash` / `pwsh` | **是** | 命令的文件效果受模式限制；网络与进程可见性不在保证范围。[S7] |
| agent 的持久终端（若部署挂载） | **是** | 终端 shell 在整个生命周期运行在有效沙箱边界下；有打开的会话或进行中的 spawn 时，改变有效模式会被拒绝。[S14] |
| Web UI 侧栏的用户终端（user shell） | **否** | `terminalController.create` 的 JSDoc 明确：分配的是 user shell，**without Agent sandbox or approval restrictions**；侧栏终端文档也说明命令使用系统用户权限、独立于 Agent 权限。[S13]、[S22] |
| `glob` / `grep` | **不涉及写入** | 直接 spawn ripgrep，不经文件系统沙箱；它们只读，因此不改变写边界。[S16] |
| 普通读取（`read`、列目录） | **否** | 读取在所有模式下都不受围栏限制。[S4]、[S12] |
| 网络访问 | **否** | 模式词汇不包含网络；base profile 下网络不受限制。[S1]、[S12] |
| 进程可见性 | **否（且因后端而异）** | bwrap 使用私有 PID 命名空间隐藏宿主进程；Landlock 与 Seatbelt 保持宿主进程可见性。[S12] |
| 插件代码 / MCP 服务器 | **否** | MCP 服务器命令官方明确是「agent 沙箱之外的受信任可执行代码」；官方安全说明把第三方插件列为可访问宿主资源的风险面，不能当成受沙箱约束的工具。[S15]、[S12] |
| `danger-full-access` | **绕过** | 消费方直接 spawn 原始 argv，不调用 `ctx.sandbox`；base profile 下审批策略为 `never`。[S1]、[S17] |

> 表中的「否」表示不在工作区写围栏内，不等于没有任何限制：宿主 OS 权限、网络策略、容器/VM 配置和后端能力仍可能限制。

### 1.3 官方明确不保证什么

官方安全说明的原文要点：

- DSH 是实验性开发者预览软件，**尚未接受安全审计**，不得视为安全或可用于生产环境的软件。[S15]
- 它会执行模型生成的代码与命令、加载第三方插件，并访问向其开放的网络、进程、凭据和文件。[S15]
- 沙箱、审批提示与权限控制可以降低风险，但**不保证隔离，也不能保证防止损害**；即使限制正确执行，也无法保护本项目获准访问的资源。[S15]
- 不要把 DSH 当作不可信工作负载唯一的安全控制措施；应只授予最小权限，优先在一次性虚拟机、容器或专用环境运行，备份可访问文件，不暴露敏感凭据。[S15]

### 1.4 四个容易混淆的「工作区」

| 概念 | 官方定义 | 是否访问边界 |
| --- | --- | --- |
| 工作区注册表（Workspace Registry） | 用户工作目录的持久记录：规范路径上的稳定 id、显示标题、会话账本；是宿主侧 UI 记账能力，对模型不可见 | **否**。沙箱策略读会话 cwd，不读注册表；删除注册只删记录，不删目录、文件、会话或日志。[S13] |
| 会话工作区根（session workspace root） | 会话创建时写入不可变 `SessionHeader.cwd` 的目录；沙箱策略用它作为 `workspace-write` 的根 | **是**。这是真正的写边界。[S1]、[S13] |
| 当前工作目录（working directory） | 会话内可变的当前目录；相对路径文件操作、新进程、指令发现、技能与补全使用它 | **否**。改变它不会改变沙箱可写根；已有终端和持久 shell 保留自己的目录。[S11] |
| 可写根（writable roots） | 工作区根 + 平台临时区域（如 `/tmp`、`os.tmpdir()`） | **是**。`workspace-write` 的实际允许集合。[S4] |

> 关键区别：`cd` 到别处，只改变「当前工作目录」；**沙箱可写根仍绑定原始项目**。[S11]

---

## 2. 工作区边界与访问权限的界定方式

### 2.1 边界是四层，不是一层

```text
会话创建
  └─ SessionHeader.cwd（不可变） ──► sandbox policy.workspaceRoot
                                        │
                    ┌───────────────────┴───────────────────┐
                    │                                       │
              bash / pwsh                            fs write / edit
                    │                                       │
                    └──────── 沙箱 / 围栏强制执行 ──────────┘
                                        │
                     工作区根 + 平台临时区域（/tmp、os.tmpdir()）
```

> bash/pwsh 走 OS 级进程沙箱；fs 的 `write`/`edit` 走进程内策略围栏。两者共享同一可写根，但强度不同：fs 围栏不是内核边界。

| 层 | 由谁决定 | 可变性 | 作用 |
| --- | --- | --- | --- |
| 1. 会话 `cwd` | 创建会话时由创建者赋予；Web 从所选工作区路径解析，回退到显式或默认 cwd | **不可变**，写入 `SessionHeader` | 决定 `workspace-write` 的根 |
| 2. 当前工作目录 | `dsh-working-directory`；`working-directory/change` 记录 | 可在会话内改变 | 决定相对路径、新进程、指令发现的位置；**不改变**沙箱根 |
| 3. 模式 | 显式批准 > 会话最后一条 `sandbox/mode` 事件 > 部署默认 | 每会话可切换，跨重启回放保留 | 决定允许的文件效果 |
| 4. 可写根 | 策略解析：会话 cwd，否则配置的 `workspaceRoot` 回退 | 随会话/调用 | 决定实际能写哪里 |

### 2.2 策略如何解析

官方解析优先级：

```text
effective mode = 已批准的显式模式
              ?? 会话最后一条 sandbox/mode 事件
              ?? 部署默认模式

workspaceRoot = 会话的不可变 cwd
             ?? 配置的 workspaceRoot（无 agent 调用或会话无 cwd 时的回退）
```

要点：

- 普通工具调用从**调用会话的不可变 cwd** 派生 `workspaceRoot`，不是从当前工作目录。[S1]
- 执行环境中的绝对路径写法保持不变；执行限制的提供方在文件实际存在的位置规范化根目录，因此包含 `symlink/..` 的 cwd 会标识配套子进程实际运行的目录。[S1]
- 配置里的 `workspaceRoot` 必须是绝对路径；相对值在加载时被拒绝。[S3]
- 会话模式切换记录在会话日志中，在该会话的下一次受限调用时生效；切换通过回放跨重启保留，每个会话保持自己的模式。[S3]

### 2.3 三种模式

| 模式 | 写入 | 读取 | 网络 | 进程可见性 | 备注 |
| --- | --- | --- | --- | --- | --- |
| `read-only` | 拒绝所有变更；POSIX 后端仍允许写 `/dev/null`，因此 `>/dev/null` 可用 | 不受限 | 不受限 | 取决于后端 | 插件层故障安全默认；适合评审、审计、只看不改 |
| `workspace-write` | 只允许目标规范化后位于工作区根或平台临时区域 | 不受限 | 不受限 | 取决于后端 | base profile 默认；临时区域属于策略内，不是越界 |
| `danger-full-access` | 不限制 | 不受限 | 不受限 | 不受限 | 绕过 `ctx.sandbox`；base profile 下审批为 `never`，不询问 |

表中的「不受限」指不受 DSH 沙箱模式限制；宿主 OS 权限、网络策略、容器/VM 配置仍可能限制。

补充：

- `read-only` 在 Windows ACL runner 上不授予任何显式可写根，并因环境 ACL 缺口报告部分强制执行。[S1]
- `workspace-write` 的临时区域具体路径因后端而异：bwrap 下是临时目录，Landlock 下是宿主 `/tmp`，Seatbelt 下是 `/private/tmp` 加每用户临时目录。[S7]
- `danger-full-access` 是有意无约束模式，不是「更宽的沙箱 profile」。[S7]

### 2.4 审批是独立的一道闸

| 策略 | 行为 | 失败方式 |
| --- | --- | --- |
| `ask`（默认） | 把需要批准的操作交给已组合的应答者；UI 通道可以提供人类应答者 | 应答者缺失、异常、不合规 → `unavailable`，按拒绝处理 |
| `never` | 在进入交互分发之前确定性地拒绝每个请求；严格无头模式 | 直接拒绝；模型不应再请求沙箱升级 |

审批结果是闭合词汇：`allowed-once`、`rejected`、`cancelled`、`unavailable`。除 `allowed-once` 外，调用方一律按拒绝处理。[S18]

三个硬限制：

- **一次性**：只有 `allowed-once`；没有 `allow-always`、没有记住的规则、没有撤销、没有授权存储；策略只有 `ask` / `never`。[S8]
- **审计只写日志**：`approval/asked` 与 `approval/decided` 只写入会话日志；模型只看到最终工具结果和当前策略，看不到人类权限 UI 或审计事件。[S8]
- **没有内置应答者**：无头或组合不完整的部署会返回 `unavailable` 并失败关闭；服务自身绝不会提示人类。[S8]

### 2.5 权限预设把两个旋钮打包

预设把一个沙箱模式与一个审批策略捆绑成具名选项，但执行服务仍各自保留自己的值。

| 预设（base） | 沙箱 | 审批 | 行为 |
| --- | --- | --- | --- |
| `read-only` | `read-only` | `ask` | 只读沙箱；需要批准的操作仍会询问 |
| `workspace-write` | `workspace-write` | `ask` | 只写当前工作区与临时区域；升级等操作需批准 |
| `danger-full-access` | `danger-full-access` | `never` | 全部直接执行，无批准门 |

要点：

- base profile 的配置表包含以上三项；官方包 README 的默认表只列 `workspace-write` 与 `danger-full-access`，base 额外加了 `read-only`。[S17]、[S9]
- 两个旋钮拼不出任何表项时，当前态显示为派生的 `custom`；`custom` 只能显示，不能选中。[S9]
- 用户用 `/permission` 切换：不带参数报告当前预设与可选条目，带参数切换；切换只追加日志事件，且只在有效值变化时落地。[S9]
- 新会话的默认值来自配置预设或 `permission.defaultPreset`；创建时把预设、`sandbox/mode`、`approval/policy` 固定进会话，之后改默认值不会改变已有会话。[S9]、[S12]

### 2.6 执行强度是事实，不是承诺

| 平台 | 后端 | 强度 | 已知边界 |
| --- | --- | --- | --- |
| Linux | 优先 bwrap，其次 Landlock | 通常 `full`；较旧 Landlock ABI 可能 `partial` | 较旧 ABI 只能限制自身公开的访问类别 |
| macOS | Seatbelt（`sandbox-exec`） | 通常 `full` | 依赖 Apple 已弃用的 `sandbox-exec`；若被移除，后端无法替换或探测 |
| Windows | ACL 受限令牌 | `partial` | NTFS 硬链接会把同一文件对象别名为多个路径；读取仍不受限；被其他 AppContainer 工具以包 SID 标记过的目录树可能不可读 |

官方要求：`partial` 是执行强度的事实，不是完整保证；需要绝对边界的消费方必须拒绝或向上暴露这一区别，不能把 `partial` 当成 `full`。[S1]、[S6]

### 2.7 `AGENTS.md`：软约束

| 项目 | 官方行为 |
| --- | --- |
| 加载链 | 先用户全局：`$DSH_HOME/AGENTS.md`，然后是 `<agentsHome>/AGENTS.md`（`$DSH_AGENTS_HOME` 或 `~/.agents`）；再按从宽泛到具体加载项目链：从项目根到会话工作目录的每个目录 |
| 候选文件 | 基础：`AGENTS.md`、`CLAUDE.md`；本地 overlay：`AGENTS.local.md`、`CLAUDE.local.md` |
| 项目根 | 默认以 `.git` 为标记；只有确认标记不存在才继续上溯；权限或 I/O 失败会停止发现，不会选择祖先项目 |
| 去重 | 同一候选组内，去除首尾空白后内容一致的文件只渲染一次 |
| 预算 | base 默认 65,536 字节；先丢弃完整的较宽泛文件，再截断最具体的文件；空指令链不添加内容 |
| 发现时机 | 第一次请求加载；成功的 `read`、`write`、`edit` 到达更深目录后，下一次请求包含新文件；**不跟随 shell 的 `cd`**；没有 watcher |
| 优先级 | 更具体优先；不覆盖 system、developer 或用户直接指令 |
| 安全注意 | 最终组件是 symlink 的候选文件会解析并加载目标；克隆仓库可以把树外文件内容呈现为较低优先级的工作区指引 |

来源：[S10]。**结论：`AGENTS.md` 是「让模型知道边界」的软约束，不是「让模型无法越界」的硬约束。** 真正的硬约束在第 2.3–2.4 节的沙箱与审批。

---

## 3. 配置与启用步骤

### 3.1 前置检查

| 检查项 | 做法 | 失败表现 |
| --- | --- | --- |
| 版本 | `dsh --version`；确认与团队约定一致（本材料按 `0.2.1-rc.3`） | 版本不符时，配置字段与默认值可能不同 |
| 平台 runner | Linux：bwrap 或启用 Landlock 的内核；macOS：可用的 `sandbox-exec`；Windows：ACL 受限令牌 runner 可启动 | `SANDBOX_UNAVAILABLE`，命令拒绝执行 |
| 工作区目录 | 必须是已存在的目录；Web 中需添加并选中 | Web 未选中工作区前，会话输入框不可用 |
| 应答者 | 交互式部署至少组合一个 `approval/request` 应答者 | `ask` 下请求返回 `unavailable`，按拒绝处理 |

`SANDBOX_UNAVAILABLE` 的官方错误文本会直接点名缺失的平台 runner：

```text
sandbox mode "<mode>" is requested but no sandbox backend is usable on this host;
refusing to run the command unconfined. Install bubblewrap or run a Landlock-enforcing
kernel (Linux), ensure sandbox-exec is usable (macOS), or ensure the ACL restricted-token
runner can start (Windows) — otherwise switch the consumer to danger-full-access.
```

来源：[S5]。

### 3.2 使用者：每个会话怎么做

1. **从项目根启动，或在 Web UI 里添加并选中工作区。**
   - `dsh` 进程会把启动时所在目录作为默认文件系统位置；base profile 把它作为默认 workspace 根。[S12]、[S20]
   - 全新的 Web UI 不会预选任何工作区；点击「选择工作区」，添加项目目录并选中；选中前输入框不可用。[S19]
2. **确认当前权限。** 输入 `/permission`（不带参数）查看当前预设与可选条目；需要切换时带预设名。[S9]
3. **按任务选模式。**
   - 正常开发：`workspace-write` + `ask`。
   - 只读评审/审计：`read-only` + `ask`。
   - 仅限一次性、可丢弃环境：才考虑 `danger-full-access`，并明确知道它不再询问。[S17]、[S15]
4. **需要写工作区外时，不要静默切全开。** 先让模型走一次升级审批；被拒绝就调整方案或调整工作区根。[S5]
5. **长任务/终端。** 若使用 agent 持久终端，改变沙箱模式前先关闭终端会话；有打开会话时模式变更会被拒绝。[S14]

### 3.3 部署者：把默认值固定下来

#### 3.3.1 配置层顺序

DSH 配置树以空根为起点，依次叠加：

1. `dsh.profile.bundles` 中各组合包的 patch；
2. profile 自身的 `cordis.patch.yml`；
3. home 级的 `$DSH_HOME/cordis.patch.yml`；
4. `--patch` 指定的覆盖层。

来源：[S20]。改完用 `dsh --profile <name> --dump-config` 查看组合后的最终值；不要只看自己写的那一层。

#### 3.3.2 最小配置模板

base profile 已经内置沙箱与审批接线；只有需要改默认值时才覆盖。以下模板只覆盖两个条目，避免动到预设表：

```yaml
# $DSH_HOME/cordis.patch.yml 或 profile 的 cordis.patch.yml
# 改完执行：dsh --profile web --dump-config

- id: sandbox-policy
  name: '@deepseek-ai/dsh-sandbox-policy'
  config:
    mode: workspace-write          # read-only | workspace-write | danger-full-access
    workspaceRoot: /absolute/path/to/workspace   # 仅无会话 cwd 时的回退；必须是绝对路径

- id: approval
  name: '@deepseek-ai/dsh-user-approval'
  config:
    policy: ask                    # ask | never
```

说明：

- `workspaceRoot` 是**回退值**；普通 agent 调用使用会话的不可变 cwd，配置值不会覆盖它。[S3]
- `mode` 是会话起始的部署默认；会话仍可通过 `/permission` 或 UI 切换。[S3]
- `policy: never` 是严格无头模式：需要批准的操作直接拒绝，不询问；适合 CI。若同时使用 `danger-full-access`，就是「完全无约束且不询问」，只应在一次性环境中使用。[S8]

#### 3.3.3 进程级环境变量

```sh
DSH_PERMISSION_MODE=workspace-write dsh web
```

- 它是**进程级后备值**，在启动时生效；base profile 用它改写沙箱模式，并据此推导审批策略：`danger-full-access` → `never`，否则 `ask`。[S17]、[S12]
- 它不会改变已打开会话的权限；General settings 中存储的权限也只影响后续 Web 会话。[S12]

#### 3.3.4 未来会话的默认预设

- 用户侧：Settings → General 中存储的权限影响后续 Web 会话，不改变已打开会话。[S12]
- 部署侧：`dsh-permission-presets` 的 `defaultPreset` 配置固定新会话默认值；创建会话时把预设、模式、审批策略固定进会话。[S9]
- 官方文档给出的是 `permission.defaultPreset` 设置命名空间与配置字段；**具体设置文件路径未在用户指南中明确**（见第 7 节）。

如果确实要在配置文件里固定默认预设，建议把整张预设表一并写出，避免依赖 patch 层的合并细节：

```yaml
- id: permission
  name: '@deepseek-ai/dsh-permission-presets'
  config:
    defaultPreset: workspace-write
    presets:
      read-only:
        sandbox: read-only
        approval: ask
      workspace-write:
        sandbox: workspace-write
        approval: ask
      danger-full-access:
        sandbox: danger-full-access
        approval: never
```

> 改完仍以 `--dump-config` 为准；`!!js` 表达式在 dump 中保持未求值，看到的是表达式本身，不是展开后的路径。

### 3.4 项目指令：把「团队约定」写进 `AGENTS.md`

推荐位置：

```text
<repo>/AGENTS.md                 # 项目级，提交到仓库
<repo>/<subdir>/AGENTS.md        # 子目录级，更具体，优先
<repo>/AGENTS.local.md           # 个人本地覆盖，不提交
$DSH_HOME/AGENTS.md              # 用户全局
~/.agents/AGENTS.md              # 共享 agents 根目录
```

最小示例：

```markdown
# 项目约定

## 边界
- 只修改 `src/`、`tests/` 与本文件；不要改 `vendor/`、`dist/`、`_archive/`。
- 需要写工作区外时，先说明原因并走审批，不要自行切换 `danger-full-access`。

## 构建与验证
- 构建：`node src/build.mjs`（示例，按项目替换）
- 只校验：`node src/build.mjs --check`（示例）

## 交付
- 改源，不改产物；交付前必须构建。
```

注意：

- 这些规则是**指导**，不是强制执行；模型仍可能违反，真正的硬边界要靠沙箱与审批。[S10]
- 内容会进入模型上下文；不要写入密钥、令牌或不应让模型看到的数据。[S10]、[S15]
- 默认预算 65,536 字节；过宽的规则文件会先被省略，最具体的文件最后被截断。[S10]
- 嵌套文件只在成功的 `read`/`write`/`edit` 到达更深目录后发现；`cd` 不会触发发现。[S10]

### 3.5 验证清单

| # | 验证项 | 期望结果 |
| --- | --- | --- |
| 1 | `dsh --profile web --dump-config` | 能看到 `sandbox-policy` 的 `mode`、`workspaceRoot`，以及 `approval` 的 `policy` |
| 2 | 新会话 `/permission` | 显示预期预设；`workspace-write` 对应 `workspace-write` + `ask` |
| 3 | 写工作区内文件 | 成功 |
| 4 | 写工作区外文件 | 失败，返回 `[sandbox: file access denied under workspace-write mode]` |
| 5 | 读工作区外文件 | 成功（在宿主权限允许的前提下；沙箱本身不拦读） |
| 6 | 触发一次升级并批准 | 同一操作以 `allowed-once` 成功；只作用于该次调用 |
| 7 | 触发一次升级并拒绝 | 操作失败；不产生永久授权 |
| 8 | 无应答者 + `ask` | 返回 `unavailable`，按拒绝处理 |
| 9 | 有打开的 agent 持久终端时切换模式 | 模式变更被拒绝；关闭终端后可切换 |
| 10 | 工作区外写入平台临时目录（POSIX `/tmp`，Windows `%TEMP%`） | 成功（临时区域在策略内，不是越界） |

---

## 4. 常见场景的推荐做法

> 本节是落地建议，不是官方原文；「原因 / 官方依据」列给出对应的官方事实。

| 场景 | 推荐做法 | 原因 / 官方依据 |
| --- | --- | --- |
| **新项目 / 单仓** | 从仓库根启动 `dsh web`，或在 Web 中选中仓库根；在根目录放 `AGENTS.md`；保持 `workspace-write` + `ask` | 启动目录是默认 workspace 根；根目录指令覆盖全仓。[S12]、[S20]、[S10] |
| **Monorepo / 多模块** | 需要跨包修改时，从仓库根开会话；只在确实要限制到某个包时，才从该包目录开会话；子目录放更具体的 `AGENTS.md` | 写边界是会话 cwd；从子目录开会话，兄弟目录就在边界外。[S1]、[S10] |
| **只读评审 / 审计** | 切到 `read-only`；需要看代码就直接读；不要为了「方便」切全开；不可信仓库放到一次性容器/虚拟机 | `read-only` 拒绝所有文件变更（POSIX 仅 `/dev/null` 例外）；读取不受限；官方安全说明建议一次性环境。[S1]、[S15] |
| **CI / 无人值守** | 用 `workspace-write` + `never`（或 `read-only` + `never`）；确保没有依赖交互式批准；把配置写进 profile patch，而不是临时命令 | `never` 确定性拒绝，不询问；没有应答者时 `ask` 也会失败关闭。[S8]、[S18] |
| **需要写工作区外** | 优先把会话开在共同父目录；或用平台临时目录；确需单次越界时走升级审批，批一次；不要长期 `danger-full-access` | `0.2.1-rc.3` 每个会话只有一个主可写根，额外可写根不属于该版本的策略。[S3]、[S5] |
| **多会话并行** | 每个会话单独确认 `/permission`；不要假设一个会话切了模式会影响另一个；需要统一默认值就改部署配置 | 模式逐会话存储；两个会话绝不会看到彼此状态。[S3] |
| **长任务 / 终端** | 先确定模式，再开持久终端；要改模式时先关闭终端；已有终端保留自己的目录 | 终端 shell 整个生命周期在有效沙箱边界下；有打开会话时模式变更被拒绝。[S14] |
| **不可信仓库 / 第三方插件** | 只读 + 一次性容器/虚拟机；不要暴露凭据；审查插件与 MCP 服务器；不要依赖 `AGENTS.md` 作为安全控制 | 沙箱不限制读取；MCP 官方明确在 agent 沙箱之外，插件按宿主代码审查；官方明确不保证隔离。[S15]、[S12] |
| **Windows 团队** | 不要假设 ACL 后端等于完整隔离；需要硬边界时用容器/远程执行器；把 `partial` 当作事实记录 | 硬链接、读取不受限、AppContainer ACL 边界导致 `partial`。[S6] |

---

## 5. 越界或违规情形的处理方式

> 官方没有定义「违规等级」「处罚」「自动告警」。官方提供的是**拒绝信号 + 一次性升级审批 + 审计事件**；本节按这些机制给出处置流程，不虚构官方处罚规则。

### 5.1 先识别信号

| 信号 | 含义 | 来源 |
| --- | --- | --- |
| `[sandbox: file access denied under <mode> mode]` | 受限命令被沙箱正常阻止 | [S5]、[S7] |
| `[sandbox: escalation available — retry this exact <subject> once with sandbox_permissions (the narrowest wider mode that suffices) + justification; the approval prompt asks the user]` | 组合声明了升级能力；可重试同一次操作 | [S5] |
| `FS_SANDBOX_DENIED` | 文件系统后端的结构化策略拒绝；与宿主内核的 `FS_PERMISSION_DENIED` 不同 | [S4]、[S2] |
| `SANDBOX_UNAVAILABLE` | 没有可用后端；前台命令未运行；绝不无隔离运行 | [S5]、[S7] |
| `[sandbox: the sandbox runner itself failed under <mode> mode — the command did not run; this is a sandbox problem, not a command failure]` | runner 自身失败，不是命令失败 | [S7] |
| `FS_NOT_OBSERVED` / `FS_STALE_VERSION` | 先读后写守卫：未读或读后已变；不是工作区越界 | [S2]、[S4] |
| `allowed-once` / `rejected` / `cancelled` / `unavailable` | 审批结果；只有 `allowed-once` 放行 | [S18] |

### 5.2 标准处置流程

1. **停一下，分类。** 先分清是「策略拒绝」「审批拒绝」「守卫拒绝」还是「沙箱基础设施失败」。不要把所有失败都当成同一种。
2. **策略拒绝且确有需要：走一次升级。** 只重试**完全相同**的操作；用 `sandbox_permissions` 指定「足以放行的最窄更宽模式」；用 `justification` 说明原因；等待审批。[S4]、[S5]
3. **审批拒绝或取消：改方案。** 不要循环重试，不要静默切到 `danger-full-access`；调整工作区根、改用临时目录、拆分任务，或由人明确决定是否放宽。[S8]
4. **基础设施失败：修后端，不要绕过。** `SANDBOX_UNAVAILABLE` 意味着命令没有运行；按错误文本安装/修复 runner，或更换组合；不要用 `danger-full-access` 掩盖。[S5]
5. **守卫拒绝：先读再改。** `FS_NOT_OBSERVED` 要求先读目标；`FS_STALE_VERSION` 要求重新读取后重试；这不是越界。[S2]、[S4]
6. **记录与复盘。** 查看会话日志中的 `approval/asked`、`approval/decided`、`sandbox/mode`、`permission/preset`；复盘时应问「边界设计是否合理」，而不只是「模型为什么不听话」。[S8]、[S9]

### 5.3 升级阶梯

| 当前模式 | 可升级到 | 是否需要审批 |
| --- | --- | --- |
| `read-only` | `workspace-write`、`danger-full-access` | 需要；更宽模式 |
| `workspace-write` | `danger-full-access` | 需要；更宽模式 |
| 任意 | 相同模式 | 不需要；重复指定当前模式直接成功 |
| 任意 | 更窄模式 | 无效；拒绝 |

来源：[S5]。审批结果是 `allowed-once`，只作用于该次调用；下一次越界仍会重新询问。[S8]

### 5.4 审计与复盘

- `approval/asked` 与 `approval/decided` 是仅日志事件，配对记录请求与最终结果。[S8]
- `sandbox/mode` 记录会话模式切换；`permission/preset` 记录预设选择；两者都只写日志，模型可见后果由各消费方承担。[S3]、[S9]
- 模型只看到最终工具结果与当前策略，不会看到人类权限 UI 或审计事件。[S8]
- 因此，团队复盘应看**会话日志 + 审批记录**，而不是只问模型「为什么越界」。

### 5.5 如果违反的是 `AGENTS.md`

- 官方没有「`AGENTS.md` 违规检测/处罚」机制；它只是模型可见的指导，不覆盖 system、developer 或用户直接指令。[S10]
- 处理方式：
  1. 检查规则是否足够具体、是否放在更具体的目录；
  2. 把真正不能碰的范围改成硬边界（工作区根、只读模式、审批策略）；
  3. 不要把「模型应该遵守」当成安全控制。
- 若需要「审计模型是否遵守」，只能基于工具调用与文件变更做外部检查；官方未提供内置合规引擎（见第 7 节）。

---

## 6. 落地时的注意事项

> 本节是落地建议；官方事实已在第 1–3 节标注来源。

### 6.1 安全定位

- **不是安全边界。** 官方明确：未接受安全审计，沙箱/审批不保证隔离；不要把 DSH 当作不可信工作负载唯一的安全控制。[S15]
- **最小权限。** 只授予完成任务所需的目录与凭据；优先一次性虚拟机/容器/专用环境；备份可访问文件。[S15]
- **不要暴露秘密。** 沙箱不限制读取；`AGENTS.md` 内容会进入模型上下文；插件/MCP 可访问宿主资源。[S15]、[S10]

### 6.2 边界相关

- **读取不受限。** 需要保密隔离时，用 OS 账户、容器、虚拟机或文件权限，不要指望 `workspace-write`。[S4]、[S12]
- **临时区域算策略内。** 写 `/tmp`、`os.tmpdir()` 成功不代表越界；不要把临时目录当持久交付位置。[S4]
- **一个会话一个主根。** `0.2.1-rc.3` 没有「额外可写根」的官方配置；跨目录工作请把会话开在共同父目录。[S3]
- **`cd` 不移动沙箱根。** 当前目录可变，但可写根仍绑定原始项目；从子目录开会话会真的把边界缩到子目录。[S11]
- **工作区注册表不是边界。** 删除注册不会删文件；它只影响 UI 分组与会话归属。[S13]
- **符号链接会被规范化。** 工作区路径用 `realpath` 规范化；指向已拥有目录的符号链接会冲突；`AGENTS.md` 最终组件是 symlink 时会跟随目标。[S13]、[S10]

### 6.3 配置相关

- **两层默认要分清。** 插件 fail-safe 是 `read-only`；base profile 显式 `workspace-write` + `ask`。自定义组合不写 `mode` 时，不要以为一定是 `workspace-write`。[S3]、[S17]
- **`workspaceRoot` 是回退。** 普通 agent 调用用会话 cwd；配置值只服务于无 agent 调用或无 cwd 的会话。[S3]
- **模式是每会话的。** 改默认值不会改变已打开会话；`General settings` 也只影响后续 Web 会话。[S12]
- **`DSH_PERMISSION_MODE` 是进程级。** 启动时生效，并推导审批策略；不是每会话开关。[S12]
- **改完必须 `--dump-config`。** 配置层多，最终值以组合结果为准；HMR 未启用时改动要重启才生效。[S20]
- **`partial` 要如实记录。** Windows ACL 与较旧 Landlock 只实现部分强制执行；不要对外宣称「完整隔离」。[S1]、[S6]

### 6.4 协作相关

- **`AGENTS.md` 提交，`.local.md` 不提交。** 团队规则进仓库；个人偏好放本地 overlay。[S10]
- **预算有限。** 默认 65,536 字节；把最重要的规则放在最具体的文件里；过宽的规则会先被省略。[S10]
- **嵌套发现靠文件工具。** 只在成功的 `read`/`write`/`edit` 到达更深目录后触发；`cd` 不会触发；没有 watcher。[S10]
- **不要放密钥。** 指令内容会进入模型上下文；敏感信息应走凭据存储，不要写进 `AGENTS.md`。[S15]

### 6.5 版本相关

- 本材料按 `0.2.1-rc.3` 整理；官方 master 文档可能已前进。例如 master 的沙箱文档出现 `additionalWritableRoots`（显式额外可写根），但 `0.2.1-rc.3` 的官方文档与配置说明没有这一项；不要直接把 master 的配置抄到 rc.3。[S1]、[S21]
- 升级 DSH 后，重新核对：默认模式、审批策略、平台 runner、`partial` 情形、`/permission` 的 UI 行为。
- macOS 的 Seatbelt 依赖已弃用的 `sandbox-exec`；Apple 移除后该后端无法替换或探测。[S6]

---

## 7. 官方未明确说明的部分

以下内容在官方文档/规范中没有明确说明。**本材料不替官方下结论**，只给出团队落地时的建议。

| 主题 | 官方状态 | 团队落地建议 |
| --- | --- | --- |
| 「违规」的定义、分级、处罚、自动告警 | 没有。官方只提供拒绝标记、一次性升级审批、审计事件 | 团队自行定义「越界事件」的复盘口径；以会话日志与审批记录为依据，不要虚构官方处罚规则 |
| 按工作区限制读取 | 没有。官方明确读取在所有模式下不受限 | 需要保密隔离时用 OS/容器/虚拟机；不要把 `workspace-write` 当读隔离 |
| 多可写根（额外目录） | `0.2.1-rc.3` 没有；master 文档出现 `additionalWritableRoots`，但该版本官方文档/配置说明没有 | 把会话开在共同父目录，或用临时目录；不要给 rc.3 写 master 才有的字段；版本差异见 [S21] |
| 每个工作区的默认模式 | 没有。模式按会话/进程；工作区注册表只管 UI 分组 | 用 `permission.defaultPreset`、`DSH_PERMISSION_MODE` 或会话内 `/permission` 管理 |
| 记住审批 / 永久授权 | 没有。只有 `allowed-once`，没有 `allow-always`、记住规则、撤销或授权存储 | 每次越界都重新审批；不要依赖「上次批过」 |
| 网络限制 / 进程隔离开关 | 没有。网络与进程可见性不在模式词汇内；进程可见性因后端而异 | 需要网络隔离时用容器/代理/远程执行器；不要把沙箱模式当网络策略 |
| `permission.defaultPreset` 的具体设置文件路径 | 官方文档给出设置命名空间与 General settings 行为，未在用户指南中明确文件路径 | 优先用 UI 设置或 profile patch；不要把某个路径当成跨版本稳定契约 |
| 对 `AGENTS.md` 不合规的自动检测/执行 | 没有。指令是 guidance，不覆盖 system/developer/用户直接指令 | 把硬要求落到沙箱/审批；`AGENTS.md` 只作为协作约定与模型提示 |
| 跨平台一致的 `full` 强制执行 | 没有。Windows ACL 与较旧 Landlock 可能是 `partial` | 需要绝对边界时用容器/虚拟机/远程执行器；把 `partial` 写进风险记录 |
| Web UI 具体控件名称/位置 | 官方文档描述「Permissions 选择器」「`/permission`」「General settings」，未固定每个版本的 UI 文案 | 以 `/permission` 的输出为准；升级后重新核对 UI |

---

## 8. 来源

> 除 S21 明确标注 master 外，以下链接均指向官方仓库 tag `dsh-v0.2.1-rc.3`；官方文档站为 <https://deepseek-harness.github.io/deepseek-harness/>。版本升级后请以新 tag 为准。

| 编号 | 文档 | 关键内容 |
| --- | --- | --- |
| S1 | [进程沙箱子系统](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/docs/subsystems/sandbox.zh.md) | 模式、逐调用策略、可写根、fail-closed、强制执行强度 |
| S2 | [文件系统子系统](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/docs/subsystems/filesystem.zh.md) | 文件效果围栏、错误分类、`FS_SANDBOX_DENIED` |
| S3 | [dsh-sandbox-policy README](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/sandbox/sandbox-policy/README.zh.md) | 默认 `read-only`、`workspaceRoot` 回退、解析优先级、单根限制 |
| S4 | [dsh-fs-sandbox README](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/fs/fs-sandbox/README.zh.md) | 读写围栏行为、临时区域、拒绝与升级、威胁模型 |
| S5 | [dsh-sandbox README](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/sandbox/sandbox/README.zh.md) | 模式、fail-closed、升级阶梯、`SANDBOX_UNAVAILABLE` |
| S6 | [dsh-sandbox-local README](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/sandbox/sandbox-local/README.zh.md) | 平台 runner、`full`/`partial`、Windows/macOS 边界 |
| S7 | [dsh-bash-sandbox README](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/shell/bash-sandbox/README.zh.md) | bash 文件影响、`/dev/null`、拒绝标记、升级重试 |
| S8 | [dsh-user-approval README](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/interaction/user-approval/README.zh.md) | `ask`/`never`、一次性审批、审计、无内置应答者 |
| S9 | [dsh-permission-presets README](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/interaction/permission-presets/README.zh.md) | 预设表、`defaultPreset`、`/permission`、会话固定 |
| S10 | [dsh-agent-instructions README](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/context/agent-instructions/README.zh.md) | `AGENTS.md` 加载链、预算、优先级、symlink 边界 |
| S11 | [工作目录子系统](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/docs/subsystems/working-directory.zh.md) | 当前目录可变；沙箱可写根仍绑定原始项目 |
| S12 | [CLI 行为参考](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/apps/cli/reference/README.zh.md) | base 默认值、读取/网络不受限、`DSH_PERMISSION_MODE`、MCP 在沙箱外 |
| S13 | [工作区子系统](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/docs/subsystems/workspace.zh.md) | 工作区注册表、规范路径、会话归属、用户终端 JSDoc |
| S14 | [dsh-terminal-bash README](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/terminal/terminal-bash/README.zh.md) | 持久终端在沙箱下；打开会话时模式变更被拒绝 |
| S15 | [安全说明](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/SAFETY.zh.md) | 实验性、未审计、不保证隔离、最小权限 |
| S16 | [dsh-tool-fs-search README](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/fs/tool-fs-search/README.zh.md) | `glob`/`grep` 直接 spawn，只读、不经 fs 沙箱 |
| S17 | [base 组合配置](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/bundle/base/cordis.patch.yml) | base 的 `workspace-write`/`ask`、预设表、`fs-sandbox`、指令预算 |
| S18 | [用户审批子系统](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/docs/subsystems/approval.zh.md) | 审批结果闭合词汇、`ask`/`never`、审计 |
| S19 | [Web UI 指南](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/docs/user/guide/index.zh.md) | 启动目录为默认位置；Web 必须选择工作区 |
| S20 | [CLI README](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/apps/cli/README.zh.md) | 启动目录为默认 workspace 根；配置层顺序；`--dump-config` |
| S21 | [master 沙箱文档](https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/subsystems/sandbox.zh.md) | 仅用于说明版本差异：master 出现 `additionalWritableRoots`，rc.3 没有 |
| S22 | [dsh-client-ui-sidebar-terminal README](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/client/ui-sidebar-terminal/README.zh.md) | Web 侧栏用户终端：系统用户权限，独立于 Agent 权限 |

---

## 附录 A：两个可直接套用的配置

### A.1 只读评审 / 审计

```yaml
# $DSH_HOME/cordis.patch.yml
- id: sandbox-policy
  name: '@deepseek-ai/dsh-sandbox-policy'
  config:
    mode: read-only
    workspaceRoot: /absolute/path/to/workspace

- id: approval
  name: '@deepseek-ai/dsh-user-approval'
  config:
    policy: ask
```

适用：代码评审、审计、不可信仓库的初步查看。注意：读取仍不受限；需要保密隔离时用容器/虚拟机。

### A.2 CI / 无人值守

```yaml
# $DSH_HOME/cordis.patch.yml
- id: sandbox-policy
  name: '@deepseek-ai/dsh-sandbox-policy'
  config:
    mode: workspace-write
    workspaceRoot: /absolute/path/to/workspace

- id: approval
  name: '@deepseek-ai/dsh-user-approval'
  config:
    policy: never
```

适用：无人在场的 CI、批量任务。工作区内写入不需要审批；工作区外写入被拒绝；升级请求被确定性拒绝，不询问。[S8]

> 注意：base 预设表没有 `workspace-write` + `never` 这一项，因此在挂载了权限预设的会话里，`/permission` 会把该组合显示为派生的 `custom`；两个旋钮的实际执行行为不受影响。

> 配置改完统一执行 `dsh --profile <name> --dump-config` 核对最终组合值；不要只看 patch 文件。

---

## 附录 B：上线核对清单

- [ ] 已确认 DSH 版本，并知道本材料对应 `0.2.1-rc.3`。
- [ ] 每个项目都已明确「会话从哪个目录启动」；跨目录任务从共同父目录启动。
- [ ] `/permission` 显示的预设符合预期（默认 `workspace-write` + `ask`）。
- [ ] 需要只读的场景已切到 `read-only`，且团队知道读取仍不受限。
- [ ] 需要无人值守的场景已设 `never`，并验证不会卡在审批。
- [ ] 已测试工作区内写入成功、工作区外写入被拒绝。
- [ ] 已测试一次升级审批：批准只生效一次，拒绝不产生永久授权。
- [ ] 已确认平台 runner 可用；没有出现 `SANDBOX_UNAVAILABLE`。
- [ ] 已确认 Windows/旧内核的 `partial` 情形，并记录风险。
- [ ] `AGENTS.md` 已提交；`.local.md` 未提交；没有写入密钥。
- [ ] 团队知道 `AGENTS.md` 是软约束，硬边界靠沙箱/审批。
- [ ] 团队知道 Web UI 用户终端、插件、MCP 不在 agent 沙箱内。
- [ ] 已约定越界事件的复盘口径与日志查看方式。
- [ ] 升级 DSH 后重新核对默认值、字段与平台行为。

---

<!-- 引用定义：保持文内 [S#] 可点击 -->

[S1]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/docs/subsystems/sandbox.zh.md
[S2]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/docs/subsystems/filesystem.zh.md
[S3]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/sandbox/sandbox-policy/README.zh.md
[S4]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/fs/fs-sandbox/README.zh.md
[S5]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/sandbox/sandbox/README.zh.md
[S6]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/sandbox/sandbox-local/README.zh.md
[S7]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/shell/bash-sandbox/README.zh.md
[S8]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/interaction/user-approval/README.zh.md
[S9]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/interaction/permission-presets/README.zh.md
[S10]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/context/agent-instructions/README.zh.md
[S11]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/docs/subsystems/working-directory.zh.md
[S12]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/apps/cli/reference/README.zh.md
[S13]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/docs/subsystems/workspace.zh.md
[S14]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/terminal/terminal-bash/README.zh.md
[S15]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/SAFETY.zh.md
[S16]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/fs/tool-fs-search/README.zh.md
[S17]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/bundle/base/cordis.patch.yml
[S18]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/docs/subsystems/approval.zh.md
[S19]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/docs/user/guide/index.zh.md
[S20]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/apps/cli/README.zh.md
[S21]: https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/subsystems/sandbox.zh.md
[S22]: https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/client/ui-sidebar-terminal/README.zh.md

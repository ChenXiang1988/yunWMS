# AGENTS.md 审校报告

| 项 | 内容 |
| --- | --- |
| 审校对象 | 工作区根 `AGENTS.md`（审校时 1,256 字节，26 行，UTF-8；修正版 2,411 字节，24 行） |
| 审校日期 | 2026-10-30 |
| 对照版本 | DSH `0.2.1-rc.3` 官方运行时契约 + DSH 仓库 `docs/AGENTS.md` 写作标准（后者仅作参考，适用范围见 §1） |
| 结论 | 运行时兼容性通过；发现 1 处硬约束误标、2 处范围/优先级不明确、2 处项目规范遗漏、若干写作标准与歧义问题；修正版已于 2026-10-30 应用 |

> 本报告把“DSH 官方规范”拆成两层：**运行时契约**（硬性、适用于任何工作区）与 **DSH 仓库自己的文档写作标准**（官方文件，但正文明确针对 DSH 仓库的文档树）。工作区自身还有 `docs/specs/` 规范；它不是 DSH 规范，但属于本工作区的权威约束。三者不能混为一谈。

---

## 0. 结论摘要

**通过（运行时兼容）**

- 文件名、根目录位置、`.git` 项目根标记、UTF-8 文本、1,256 字节预算、纯文本 Markdown、无 `@path` import、无 `.claude/rules` 依赖、无隐藏 `</system-reminder>` 标记，均未触发 DSH 的加载限制。[S1]
- 构建命令与 `--check` 语义经实测/源码核对成立：`--check` 在写文件之前返回，确实只校验不写。[P2] [P3]
- `dist/*.html` 已被 `.gitignore` 忽略；当前构建产物无外链资源引用。[P4]

**需要修正**

1. **`_archive/2026-10-29-handover/` 标为“只读”**：实际目录对当前用户可写；DSH 的 `workspace-write` 允许写工作区内任意路径，不会因为 `AGENTS.md` 写了“只读”就拦截。这是“约定”而非“硬约束”，必须改写或移到工作区外。
2. **“权威约束”未限定优先级**：DSH 官方明确工作区指令不覆盖 system、developer 或用户直接指令；“权威约束”容易被读成最高优先级。
3. **“没有 `package.json`”范围过宽**：根目录与 `proto-next/pc`、`proto-next/app` 确实没有；但 `_archive/` 旧原型有 `package.json`。应限定为“当前原型构建”。
4. **“状态跟预期不符就停下”不可操作**：`--check` 只比较“当前源码算出的 hash”与 `dist`，不是并发锁，也不会判断“预期”是什么；应改为记录基线 hash 的流程。
5. **遗漏工作区自身规范的强制项**：`docs/specs/00-规范索引.md` 要求所有会话遵守 `04-环境论断核查约定.md`，且“零外链”包含“不引图片外链”；当前 `AGENTS.md` 只列 01/02/03，也只列 CDN/网络字体/依赖。[P1]
6. **按 DSH 仓库写作标准，根 `AGENTS.md` 偏“项目叙事 + 目录表 + 操作步骤”**：该标准要求根 `AGENTS.md` 放“每次会话都需要的常驻指令”，每条 1–3 行，并链接到事实的归属页；故事、示例、情境化步骤不放这里。[S2] 但该标准是否强制适用于用户项目，官方未明确 → **规范未覆盖**，本报告只作为建议。

---

## 1. 审校口径：三种“规范”

| 层级 | 文件 | 性质 | 对 `AGENTS.md` 的约束力 |
| --- | --- | --- | --- |
| A. DSH 运行时契约 | `dsh-agent-instructions` README | 加载器实际行为：候选文件、项目根、预算、注入、刷新、优先级、转义、symlink | **硬性**；不满足会影响加载或注入 |
| B. DSH 仓库写作标准 | DSH 仓库 `docs/AGENTS.md` | DSH 仓库自己的文档分层与写作规则（root/subtree `AGENTS.md`、architecture、subsystems 等） | **官方文件，但正文明确针对 DSH 仓库文档树**；是否适用于用户项目，官方未明确 |
| C. 本工作区规范 | `docs/specs/00-规范索引.md` 及 01–04 | 本工作区的设计/汇报/环境论断约束 | 本工作区权威；**不是 DSH 规范**，但 `AGENTS.md` 应正确转述 |

> 因此，下面说“不符合 DSH 规范”时，指的是 A 层；说“按 DSH 写作标准不推荐”时，指的是 B 层，并会标注其适用范围；说“与工作区规范不一致”时，指的是 C 层。

---

## 2. 逐条对照

| 行 | 原文（节选） | 判定 | 依据 / 说明 |
| --- | --- | --- | --- |
| 1 | `# AGENTS.md` | 合规（文件名/位置）；标题格式 **规范未覆盖** | `AGENTS.md` 是默认候选；根目录有 `.git`，发现链正常。[S1] 标题不是加载条件。 |
| 3 | `「西之月 WMS」仓储库存模块的原型工作区。……后续会有其他模块的原型与设计资料进来。` | 运行时不违规；按 DSH 仓库写作标准 **不推荐** | 根 `AGENTS.md` 应是常驻指令，不是项目叙事/未来计划；写作标准要求“记录当前状态”。[S2] 适用范围见 §1。 |
| 5–9 | 路径表：`proto-next/`、`docs/`、`_archive/...` | 运行时不违规；其中 `_archive ... 只读` **不符合/不明确** | 目录表本身是项目上下文；但“只读”不是 DSH 能强制的属性，见 §4.1。 |
| 11 | `设计规范在 docs/specs/，是 PC / APP / 汇报交付的权威约束，优先于模型默认审美。` | **不明确** | “权威约束”未说明与 DSH system/developer/用户指令的关系；官方明确工作区指令不覆盖这些。[S1] 见 §4.2。 |
| 11 | `做任何 PC 后台、APP 手持或汇报 HTML 前，先读对应规范（01 PC / 02 APP / 03 汇报）` | 与工作区规范 **不完整**；表述有歧义 | `docs/specs/00` 要求所有会话同时遵守 04；且“01 PC / 02 APP / 03 汇报”不是实际文件名。[P1] |
| 13 | `构建。没有 package.json，不要用 npm，只用 Node 标准库：` | **范围过宽/不准确** | 根与 `proto-next/pc`、`proto-next/app` 没有 `package.json`；`_archive/` 旧原型有 2 个。见 §4.3。 |
| 15–18 | `cd proto-next/pc && node src/build.mjs` 等 | 合规（项目事实） | 命令路径正确；`--check` 实测通过。[P2] [P3] DSH 不规定项目构建命令 → 这部分属“规范未覆盖”。 |
| 20 | `末尾加 --check 只校验不写。` | 合规（项目事实） | `--check` 分支在 `writeFileSync` 之前 `return`；实测只校验。[P2] [P3] |
| 22 | ``dist/*.html` 不入库，交付前必须构建。改源，不改产物。` | 合规（项目事实） | `.gitignore` 忽略 `proto-next/dist/`。[P4] “必须构建/不改产物”是项目规则，DSH 不评价。 |
| 24 | `产物不得有外链：不引 CDN、不引网络字体、不装依赖。` | **部分合规/不完整** | 当前产物无 `<script src>`、`<link href>`、`url(http)`、CDN/网络字体；但 `docs/specs/00` 的零外链还包含“不引图片外链”，本行未写。[P1] 见 §4.6。 |
| 26 | `这个工作区可能同时有多个会话在改。动手前跑一次 --check，状态跟预期不符就停下。` | **不明确** | 多会话是事实；但 `--check` 是“源码→产物”漂移检查，不是并发锁；“预期”未定义。见 §4.4。 |

---

## 3. 符合规范的部分

### 3.1 加载与格式兼容

| 检查项 | 实测/依据 | 结论 |
| --- | --- | --- |
| 文件名 | `AGENTS.md` 是默认基础候选 | 符合 [S1] |
| 位置 | 工作区根，存在 `.git` 项目根标记 | 符合 [S1] |
| 编码 | `file` 显示 UTF-8 文本；官方未明确要求编码 | 实际可用；编码要求 **规范未覆盖** |
| 预算 | 1,256 字节，远低于 base 的 65,536 字节 `maxBytes` | 符合 [S1] |
| 纯文本 | 无二进制、无隐藏状态标记 | 符合 [S1] |
| `@path` import | 文件中没有 `@...` 引用；官方明确不解释 `@path` import | 不触发问题 [S1] |
| `.claude/rules` | 文件未依赖该目录；官方明确不解释 | 不触发问题 [S1] |
| 隐藏 `</system-reminder>` | 文件中没有；即使有也会被转义 | 符合 [S1] |
| 常规文件 | `-rw-------`，不是 symlink；官方对 symlink 有信任边界提示 | 不触发 [S1] |
| 发现链 | 当前会话的 `system-reminder` 已加载该文件 | 加载正常 |

### 3.2 项目规则的可执行性

| 检查项 | 实测/依据 | 结论 |
| --- | --- | --- |
| PC 构建命令 | `cd proto-next/pc && node src/build.mjs --check` 通过 | 命令正确 |
| APP 构建命令 | `cd proto-next/app && node src/build.mjs --check` 通过 | 命令正确 |
| `--check` 只校验 | `pc/src/build.mjs` 在 `writeFileSync` 前返回；APP 同构 | 符合原文描述 [P2] [P3] |
| `dist` 不入库 | `.gitignore` 第 24 行忽略 `proto-next/dist/` | 符合 [P4] |
| 只用 Node 标准库 | 当前构建脚本只 import `node:*` 与本地模块 | 符合当前事实 |
| 产物无资源外链 | 当前 `dist` 无 `<script src>`、`<link href>`、`url(http)`、CDN/网络字体；`@import` 仅出现在“无 @import”注释中 | 符合本行口径 |
| 单行段落 | 每个段落一条物理行 | 若按 DSH 仓库写作标准，符合 [S2] |
| 根文件预算 | 1,256 字节；DSH 仓库标准 root ≤ 2,080 词 | 远低于上限 [S2] |

> 说明：`--check` 通过不代表“没有并发修改”。它只证明“当前源码算出的 hash”与现有 `dist` 一致；如果另一个会话改了源码并重建，它也会通过。见 §4.4。

---

## 4. 不符合或不明确的部分

### 4.1 `_archive/...` 标为“只读”，但 DSH 不会强制

**原文（第 9 行）**

> `` `_archive/2026-10-29-handover/` | 接管前快照，只读 ``

**问题**

- 实测 `_archive/2026-10-29-handover` 是 `drwxr-xr-x`，当前用户可写；`README.md` 也只是 `600`，不是只读挂载。
- DSH 的 `workspace-write` 允许写入“工作区根目录或平台临时区域下的目标”，没有“某个子目录只读”的例外。[S3]
- `AGENTS.md` 的规则是模型可见的指导，不是强制执行；DSH 不会读取这行文字来阻止写入。[S1]

**结论**

- 如果“只读”是**约定**：应改为“约定不修改；DSH 不强制”。
- 如果“只读”是**硬要求**：需要把 `_archive` 移出工作区，或以 `read-only` 模式运行整个会话（会同时禁止改其他文件）；DSH 没有“单目录只读”的官方配置。[S4]

### 4.2 “权威约束”未限定与 DSH 指令的关系

**原文（第 11 行）**

> `设计规范在 docs/specs/，是 PC / APP / 汇报交付的权威约束，优先于模型默认审美。`

**问题**

- DSH 官方注入模板明确：工作区指令是 guidance，更具体优先，但 **不覆盖 system、developer 或用户直接指令**。[S1]
- “权威约束”没有说明它只在“本工作区项目规则”内权威，容易被读成高于 DSH 系统指令。
- `docs/specs/00-规范索引.md` 自己说的是“规范优先于模型默认审美”，不是“优先于 system/developer/user”。[P1]

**结论**

- 建议改为“在本工作区的项目规则内，以 `docs/specs/` 为准；它不覆盖 DSH 的 system、developer 或用户直接指令”。

### 4.3 “没有 package.json”范围过宽

**原文（第 13 行）**

> `构建。没有 package.json，不要用 npm，只用 Node 标准库：`

**问题**

- 根目录、`proto-next/pc`、`proto-next/app` 确实没有 `package.json`。
- 但 `_archive/2026-10-29-handover/proto/admin/package.json` 与 `_archive/2026-10-29-handover/proto/PDA/package.json` 存在。
- 如果读者把“没有 `package.json`”理解为“整个工作区没有”，与事实不符；如果理解为“当前原型构建不需要”，则成立但需要写明。

**结论**

- 改为“当前原型构建不需要 npm 或 `package.json`（`_archive/` 旧快照除外），只用 Node 标准库”。

### 4.4 “状态跟预期不符”不可操作，且 `--check` 不是并发锁

**原文（第 26 行）**

> `这个工作区可能同时有多个会话在改。动手前跑一次 --check，状态跟预期不符就停下。`

**问题**

- `--check` 的实现是：用当前源码重新渲染 HTML，算 sha256，与现有 `dist` 比较；相同则通过，不同则失败。[P2]
- 它不检查“是否有其他会话在写”，也不提供锁；两个会话可以同时通过 `--check`，然后各自覆盖文件。
- “预期”没有定义：是上一次 `--check` 的输出？是 git 状态？是某个固定 hash？读者无法执行。
- 多会话是 DSH 的正常使用方式；官方没有提供工作区写锁（**规范未覆盖**）。

**结论**

- 建议改成“记录基线 hash”的可操作流程：开工前分别跑 PC/APP 的 `--check`，记录输出中的 sha256；若失败，或 hash 与本次会话开始时不同，先停下确认是否有其他会话/未提交改动；`--check` 通过不等于没有并发修改。

### 4.5 按 DSH 仓库写作标准，根 `AGENTS.md` 内容偏多

**原文（第 3、5–9、13 行）**

> `「西之月 WMS」仓储库存模块的原型工作区。……后续会有其他模块的原型与设计资料进来。`
>
> `| 路径 | 角色 |` ……
>
> `构建。没有 package.json，不要用 npm，只用 Node 标准库：`

**问题**

- DSH 仓库的写作标准把根 `AGENTS.md` 定义为“常驻指令（standing orders）”：每条 1–3 行，链接到事实的归属页；**故事、完整示例、情境化步骤不属于根文件**。[S2]
- 该文件包含项目叙事、未来计划、目录表、构建步骤；按该标准应分别移到 README/项目文档/构建文档，根文件只留一行链接 + 规则。
- **但是**：该标准正文明确针对 DSH 仓库的文档树（root/subtree `AGENTS.md`、architecture、subsystems 等），是否强制适用于用户项目，官方未明确 → **规范未覆盖**。因此这条是“按该标准不推荐”，不是“违反 DSH 运行时契约”。

**结论**

- 如果团队要严格套用 DSH 仓库标准：把项目背景、目录表、构建步骤移到 `README.md`/`docs/`，根 `AGENTS.md` 只留常驻规则与链接。
- 如果团队只要求运行时兼容：可以保留精简后的项目地图，但建议压缩到 1–2 行，避免根文件变成“项目百科”。

### 4.6 遗漏工作区自身规范的两项强制内容

**原文（第 11、24 行）**

> `做任何 PC 后台、APP 手持或汇报 HTML 前，先读对应规范（01 PC / 02 APP / 03 汇报）`
>
> `产物不得有外链：不引 CDN、不引网络字体、不装依赖。`

**问题**

- `docs/specs/00-规范索引.md` 第 15 行明确：“**任何会话都须遵守 04（环境论断核查）**”。当前 `AGENTS.md` 只列 01/02/03，没有 04。[P1]
- 同文件第 17 行明确：零外链包括“**不引图片外链**”。当前 `AGENTS.md` 只列 CDN、网络字体、依赖，遗漏图片外链。[P1]
- 这两项不是 DSH 规范，而是本工作区自己的权威规范；但 `AGENTS.md` 作为根指令应正确转述。

**结论**

- 在规范行补 04；在零外链行补“不引图片外链”。
- 同时说明：`docs/specs/00` 的机器检查目前标注“待实现”，所以这些规则当前主要靠评审/人工检查，不应假设有自动拦截。[P1]

---

## 5. 可能引起歧义的表述

| 原文 | 歧义点 | 建议 |
| --- | --- | --- |
| `01 PC / 02 APP / 03 汇报` | 不是实际文件名；且遗漏 04 | 用完整文件名/相对链接；补 04 |
| `只读` | 可能是“约定”也可能是“硬约束”；实际可写 | 改为“约定不修改；DSH 不强制”，或说明硬只读需 `read-only`/移出工作区 |
| `权威约束` | 可能被读成高于 system/developer/user | 限定“在本工作区项目规则内”；补“不覆盖 DSH 指令” |
| `没有 package.json` | 全局没有 vs 当前构建不需要 | 限定“当前原型构建”；注明 `_archive` 例外 |
| `产物不得有外链` | 资源引用 vs 数据里的 URL 字符串；是否含图片外链 | 定义资源引用类型；补“不引图片外链”；说明演示数据 URL 不算 |
| `状态跟预期不符` | “预期”未定义；`--check` 不是锁 | 记录基线 sha256；说明检查边界 |
| `多个会话在改` + `--check` | 容易被读成“`--check` 能防并发” | 明确“不是锁”；给出暂停/确认流程 |
| `交付前必须构建` + `改源，不改产物` | 可能被读成“任何时候都不能重建产物” | 说明“交付前按源重建；不要手改 dist” |
| `不装依赖` | 与 `_archive` 旧原型的 `package.json` 是否矛盾 | 限定“当前原型构建”；旧快照不算 |

---

## 6. 修改建议

按优先级排列；具体替换文本见 §7。

1. **在文件开头加“指导而非强制 + 优先级”声明。** 明确 `AGENTS.md` 不改变 DSH 沙箱/审批；与 system/developer/用户直接指令冲突时以后者为准。
2. **改写 `_archive` 行。** 把“只读”改为“约定不修改；DSH 不强制只读”；如要硬只读，说明需把目录移出工作区或以 `read-only` 模式运行整个会话。
3. **改写“权威约束”。** 改成“在本工作区项目规则内以 `docs/specs/` 为准；不覆盖 DSH 的 system/developer/用户直接指令”。
4. **补 04 与图片外链。** 规范行加 `04-环境论断核查约定.md`；零外链行加“不引图片外链”；说明机器检查“待实现”。
5. **限定 `package.json` 范围。** 改成“当前原型构建不需要 npm 或 `package.json`（`_archive/` 旧快照除外）”。
6. **把 `--check` 说明改成可操作流程。** 记录 PC/APP 的 sha256 基线；失败或 hash 变化就暂停确认；明确“不是并发锁”。
7. **用完整文件名/链接替代 01/02/03 缩写。** 便于模型和人不猜。
8. **压缩项目叙事/目录表。** 若采纳 DSH 仓库写作标准，移到 README/`docs/index.md`；若保留，压到 1–2 行，避免根文件变成项目百科。
9. **修 `.gitignore` 的旧注释（跨文件一致性，非 `AGENTS.md` 内容）。** 第 18 行仍写“npm run build”，与“不要用 npm”冲突；建议改为 `node src/build.mjs` 或删除该注释。[P4]
10. **考虑把 `AGENTS.md` 纳入版本控制。** DSH 不要求入库（**规范未覆盖**）；但当前 `git status` 显示 `AGENTS.md` 未跟踪，团队共享/换机时需要提交。

---

## 7. 修正后的完整版本

> 下面这版已按“运行时契约 + 工作区规范”修正，并尽量贴近 DSH 仓库写作标准；项目背景/目录压缩为 1–2 行。修正版已于 2026-10-30 应用到根 [`AGENTS.md`](../AGENTS.md)。

```markdown
# AGENTS.md

本文件是本工作区的常驻指令，只约束本工作区。它是模型可见的指导，不是强制执行；不改变 DSH 的沙箱/审批配置。与 DSH 的 system、developer 或用户直接指令冲突时，以后者为准。

## 范围与地图

- 「西之月 WMS」仓储库存模块原型工作区；交付零依赖、双击即开的单文件 HTML，分 PC 后台与 APP 手持。
- 当前原型在 `proto-next/`：`pc/` 与 `app/` 平级，共享 `shared/`。文档入口 [docs/index.md](docs/index.md)；历史快照 `_archive/2026-10-29-handover/`。

## 项目规则

- 设计规范以 [docs/specs/](docs/specs/) 为准：做 PC 后台、APP 手持或汇报 HTML 前，先读 [01-PC端设计规范.md](docs/specs/01-PC端设计规范.md)、[02-APP端设计规范.md](docs/specs/02-APP端设计规范.md)、[03-汇报材料设计规范.md](docs/specs/03-汇报材料设计规范.md)；所有会话同时遵守 [04-环境论断核查约定.md](docs/specs/04-环境论断核查约定.md)。配色、字号、间距、圆角一律取自规范令牌，不自行新值。
- 规范中的机器检查目前标注“待实现”，当前靠评审与人工检查；不要假设有自动拦截。
- 当前原型构建不需要 npm 或 `package.json`（`_archive/` 的旧快照除外），只用 Node 标准库。
- 构建：PC 用 `cd proto-next/pc && node src/build.mjs`；APP 用 `cd proto-next/app && node src/build.mjs`。末尾加 `--check` 只校验、不写。
- `dist/*.html` 不入库；交付前必须构建。改源，不改产物。
- 产物不得引用外部资源：不引 CDN、不引网络字体、不引图片外链、不装依赖。演示数据中的 URL 字符串不属于资源外链。
- `_archive/2026-10-29-handover/` 是历史快照，约定不修改；DSH 不会因本行强制只读。需要硬只读时，把该目录移出工作区，或以 `read-only` 模式运行（整会话只读）。
- 本工作区可能同时有多个会话在改。开工前在 `proto-next/pc` 与 `proto-next/app` 各跑一次 `--check`，记录 sha256 基线；若失败或 hash 与基线不同，先停下确认是否有其他会话/未提交改动。`--check` 是产物漂移检查，不是并发锁。

## 优先级

- 本文件与 `docs/specs/` 冲突时，以 `docs/specs/` 为准。
- 本文件与 DSH 的 system、developer 或用户直接指令冲突时，以后者为准。
```

---

## 8. 规范未覆盖

以下内容在 DSH 官方文档中没有明确说明；本报告不替官方下结论。

| 主题 | 官方状态 | 处理方式 |
| --- | --- | --- |
| `AGENTS.md` 的内容 schema/标题/语言 | 运行时只把它当文本注入，不规定内容结构；标题不是加载条件 | 运行时兼容即可；结构按团队/写作标准 |
| `AGENTS.md` 的编码 | 官方未明确要求 UTF-8；实际文件是 UTF-8 | 保持 UTF-8 纯文本；换编码前先验证 |
| DSH 仓库写作标准是否适用于用户项目 | `docs/AGENTS.md` 正文针对 DSH 仓库文档树；是否强制用于用户项目未明确 | 作为参考；不当作运行时硬要求 |
| 单目录只读（如 `_archive`） | DSH 只有整会话 `read-only`，没有按目录 ACL 的官方配置 | 约定不修改，或移出工作区/整会话只读 |
| 多会话写锁/并发控制 | 官方未提供工作区写锁；多会话是正常用法 | 用基线 hash + 人工确认；不要依赖 `--check` 当锁 |
| `--check` 的语义 | 这是本工作区脚本行为，不是 DSH 规范 | 以脚本源码为准；本报告已核对 |
| “外链”的定义 | DSH 不定义交付物的“外链”；这是项目规范 | 由 `docs/specs/00` 定义；区分资源引用与演示数据 URL |
| `AGENTS.md` 是否必须入库 | DSH 不要求；只从磁盘加载 | 当前未跟踪；团队共享时提交 |
| `AGENTS.md` 的 word/byte 预算 | 运行时 `maxBytes` 是 65,536；DSH 仓库标准另有 root ≤2,080 词 | 当前 1,256 字节，均满足 |
| 项目构建命令/`package.json` 归属 | 不是 DSH 规范；由项目脚本决定 | 以实际脚本为准 |

---

## 9. 来源

| 编号 | 来源 | 用途 |
| --- | --- | --- |
| S1 | [dsh-agent-instructions README](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/context/agent-instructions/README.zh.md) | 候选文件、项目根、预算、加载/刷新、优先级、转义、symlink、限制 |
| S2 | [DSH 仓库 docs/AGENTS.md](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/docs/AGENTS.md) | DSH 仓库的 AGENTS.md 写作标准（root/subtree 分层、写作规则、预算） |
| S3 | [dsh-fs-sandbox README](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/fs/fs-sandbox/README.zh.md) | `workspace-write` 的写入边界；无单目录只读例外 |
| S4 | [dsh-sandbox-policy README](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/packages/sandbox/sandbox-policy/README.zh.md) | 每会话单主根；模式解析；`read-only` |
| S5 | [进程沙箱子系统](https://github.com/deepseek-ai/deepseek-harness/blob/dsh-v0.2.1-rc.3/docs/subsystems/sandbox.zh.md) | 模式、可写根、fail-closed |
| P1 | [docs/specs/00-规范索引.md](specs/00-规范索引.md) | 本工作区规范：04 强制、零外链含图片、机器检查待实现 |
| P2 | [proto-next/pc/src/build.mjs](../proto-next/pc/src/build.mjs) | `--check` 只校验、不写 |
| P3 | [proto-next/app/src/build.mjs](../proto-next/app/src/build.mjs) | APP 侧 `--check`/写入分支 |
| P4 | [.gitignore](../.gitignore#L18-L24) | `dist` 不入库；旧 “npm run build” 注释 |
| P5 | [AGENTS.md](../AGENTS.md) | 审校对象 |

---

## 附：审校时实测

- `AGENTS.md`：1,256 字节，26 行，UTF-8；无 `</system-reminder>`、无 `@path`、无 `.claude/rules`。
- `node src/build.mjs --check`：PC、APP 均通过（审校时）。
- `_archive/2026-10-29-handover`：`drwxr-xr-x`，当前用户可写；不是只读挂载。
- `package.json`：根/`proto-next/pc`/`proto-next/app` 无；`_archive/.../proto/admin`、`_archive/.../proto/PDA` 有。
- `dist`：无 `<script src>`、`<link href>`、`url(http)`、CDN/网络字体；数据中存在 `https://`/`wss://` 字符串，是演示端点。
- `docs/specs`：01/02/03/04 均存在；00 的机器检查标注“待实现”。

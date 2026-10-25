# 审计记录

- change: `implement-owner`
- anchor: `7c938a6`（`git merge-base main HEAD`）
- 范围: `7c938a6..HEAD`，10 个提交
- 审计时间：2026-10-25
- 审计者：`delivery-auditor`（只读），结论由主 Agent 原样转录

## 逐项结论

| 检查项 | 结论 | 证据 |
| --- | --- | --- |
| 1. `audit.md` 是否存在 | 不存在，**本次审计建立** | `openspec/changes/implement-owner/` 原仅含 `.openspec.yaml`、`proposal.md`、`tasks.md` |
| 2. 路径白名单 | 见「主 Agent 裁决 ①」 | 39 文件中 3 个 `.codebuddy/**`，其余全部落在 `code/backend/wms-auth/**`、`code/backend/wms-web/**/ErrorCode.java`、`docs/**`、`openspec/**` |
| 3. 单次提交 ≤400 行 | **不通过** | `c0f8904` 863 行（超 463）、`f948e10` 405 行（超 5） |
| 4. 迁移与代码分离 | **通过** | 范围内无 `db/migration/**`；`V022` 由 `778b8d2` 单独提交，在 anchor 之前 |
| 5. GPL/AGPL/SSPL 依赖 | **通过** | `git diff 7c938a6 -- '*pom.xml' 'package.json' 'pnpm-lock.yaml'` 为空；关键词扫描仅 2 处文字命中 |
| 6. 前端色值 / 第二套 UI 库 | **不适用** | 本分支未改 `code/frontend` |
| 7. 修改测试断言 | **通过** | 702 insertions / 6 deletions，6 条删除全为构造签名适配与 Javadoc 改写，无断言被削弱、无用例被删；测试方法数 24 → 55 |
| 8. 交付三件事 | **部分不通过** | `f948e10` 缺「还有什么没做」；任务 1.8 的子 Agent 回传为空 |
| 9. `tasks.md` 可勾选 | **基本一致，1 处陈述不实** | 见「勾选核对」 |

## 勾选核对

| 条目 | 单测声明 | 实测 | 代码事实 | 结论 |
| --- | --- | --- | --- | --- |
| 1.1 | 9 | 9 | 双主体登录与令牌签发 | 一致（anchor 前） |
| 1.2 | 21 | 21 | `LoginProtectionPolicy` 5 次 / 30 分钟 | 一致（anchor 前） |
| 1.3 | 44 | 44 | `MustChangePasswordGate` + `RestAuthResponses` 40104→401 | 一致 |
| 1.4 | 55 | 55 | `SaTokenSessionInvalidator`；`invalidateAllOf` 仅测试调用 | 一致 |
| 1.7 | 24 | 24 | `SysUserMapper.xml` 含 `FOR UPDATE` | 一致（anchor 前） |
| 1.8 | 50 | 50 | `FailedAttemptRecorder`；改密不写 `last_login_at` | 一致 |

**不实的一处**：`c0f8904` 提交正文写「已在 tasks.md 登记」，但 `tasks.md` 的存量违约登记表当时只有 `7b1e705`。已在本轮补齐。

## 逐提交行数

口径：`git diff <c^> <c> --numstat` 增删合计，排除 `primitive.css`、`docs/api/inventory.md`、`docs/api/openapi.json`、`pnpm-lock.yaml`。

| 提交 | 说明 | 合计 | 超限 |
| --- | --- | --- | --- |
| `9054435` | 新增错误码 40104 | 11 | 否 |
| `c0f8904` | 首次登录强制改密 | **863** | **是** |
| `bde0093` | 台账重跑与勾选 | 3 | 否 |
| `d653555` | 从 DDD 抽取三条 | 8 | 否 |
| `f948e10` | 改密失败计入计数 | **405** | **是** |
| `2d4ba5c` | 勾选 1.8 | 3 | 否 |
| `88e6f9e` | 会话失效能力 | 282 | 否 |
| `4a1db9b` | 勾选 1.4 | 8 | 否 |
| `5bd2987` | 新增前端 Agent | 158 | 否 |
| `6f8d8f2` | 澄清 audit.md 归属 | 6 | 否 |
| **合计** | 10 个提交 | 1747 | — |

## spec 与实现一致性抽查

**A. 「首次登录强制改密」→ 一致。** spec 要求「仅允许访问改密界面，其他接口返回未授权」。实现：登录照发令牌并写会话标记，`SaTokenConfig` 在 `checkLogin()` 后调 `MustChangePasswordGate`，放行清单仅 `/api/auth/password`，`RestAuthResponses` 把 `MUST_CHANGE_PASSWORD` 映射为 HTTP 401。前端改密界面属任务 4.1，未实现，不构成后端偏差。

**B. 「管理员踢下线」→ 不一致（已声明的缺口）。** spec 要求系统支持管理员主动使指定会话失效。实现有 `invalidateAllOf` 能力，但无 Controller 暴露，调用点只有测试。已登记为任务 2.9。

## 主 Agent 裁决

**① `.codebuddy/**` 不记为越界。**

任务包白名单漏写了 `.codebuddy/**`，而 `AGENTS.md` 与 `docs/agents.md` 都明确主 Agent 可直接做 `.codebuddy/` 配置类基建。这是**任务包缺陷**，不是交付违约——子 Agent 没有越界，是我写漏了。

防再发：任务包第 2 项白名单今后必须包含主 Agent 自己的配置改动路径。

**② `f948e10` 405 行记不通过，不开例外。**

超出 5 行（1.25%）。一旦为「超一点」开口子，下一次就是「超 20 行也算一点」。规则的价值在于边界清楚。

**③ 任务 1.4 措辞缩小，接受。**

原文「实现会话失效**与管理端踢人下线**」改为「实现会话失效能力」，同时新增 2.9 承接踢人入口。改动有交代、有承接，不是悄悄降低标准。

**④ `proposal.md` 表述更正。**

原文称「不新增表、不改表结构」，但 `V022` 是 `ALTER TABLE sys_user ADD COLUMN`。这是我在写 proposal 时的疏漏，已更正。

## 存量违约

| 提交 | 违规 | 处置 |
| --- | --- | --- |
| `7b1e705` | 1536 行；一提交做两件事（登录 + 模块改名） | 已进 main，不改历史 |
| `c0f8904` | 863 行 | 已进 main，不改历史 |
| `f948e10` | 405 行 | 已进 main，不改历史 |

三笔均为「派活时未估算规模」造成。已写入 `docs/agents.md` 派活契约第 9 项：预估 >400 行必须拆包。

## 结论

**不通过。**

两项硬性违规（`c0f8904` 863 行、`f948e10` 405 行）已发生且不可逆，登记为存量。除此之外：测试断言未被削弱、无违规依赖、迁移与代码分离、spec 与实现抽查一致。

分支可合并进 main；本 change 未完成，不归档。

# Design

## Context

见 `proposal.md` 与 `docs/research/architecture-boundary-snapshot-2026-10-27.md`。

当前状态的关键事实：

- 后端 Maven/Java 依赖单向无环，唯一架构测试是模块方向的 `ModuleBoundaryTest`。
- 持久化层存在跨模块表访问与外键，Java 字节码门禁不可见。
- 货主状态流转是“读当前状态 → 判定 → 无条件 UPDATE”，没有并发保护。
- 响应侧 ID 已按字符串返回，请求侧部分 DTO 仍使用 `Long`，OpenAPI 因而是 integer。
- README、API 说明和工具夹具存在可被源码直接推翻的描述。

本变更只固化事实、决策和验证方案，并修正文档与工具；不改变运行时行为。

## Goals / Non-Goals

**Goals:**

- 用一份带基线与时效的快照记录完整模块—表—外键—Mapper 关系。
- 对 `OwnerNameMapper`、跨模块外键、门禁缺口、并发行为和契约口径给出可执行决策。
- 修正工具夹具与文档状态，使后续验证不被错误夹具或过期说明误导。
- 为后续修复 change 提供不替实现选型的验收不变量。

**Non-Goals:**

- 不在本轮新增 ArchUnit 规则、并发修复、DTO 类型变更、数据库迁移或前端接线。
- 不把人工验证写成当前必然失败的自动化测试。
- 不在调用方完成构建与并发验证前归档 `migrate-network-console`。

## Decisions

### D1. 完整边界清单放在快照，审计结论放在 change

- 选择：`docs/research/architecture-boundary-snapshot-2026-10-27.md` 保存带时间、基线和完整表格的只读快照；`audit.md` 保存结论、门禁矩阵和后续动作。
- 理由：完整矩阵是“当时看到什么”，属于快照；整改决策属于 change，不能混成一份会持续变化的半规范文档。
- 备选：写进 `docs/coding-project.md`。否决，规则文档不应承载 24 张表与 29 条外键的动态快照；README 已证明会漂移。

### D2. `OwnerNameMapper` 保留，但明确登记为待替换债务

- 选择：本轮保留 auth 侧实现，替换前置条件写死为“master 提供按 ID 批量取名、返回 id+name 轻量投影、且可包含已逻辑删除货主的 Application 能力”；条件满足后由 `wms-app` bridge 适配 `OwnerNameQuery`，再删 auth Mapper/XML/实现。
- 理由：`wms-master` 当前只有 `findById` 和分页查询，且所有读路径都排除 `deleted_at`；直接替换会让“账号绑定已删除货主”和“未绑定货主”在用户列表中无法区分。当前实现是已收口到单一端口的显式临时方案，不是新的任意越界。
- 备选：立即把 XML 搬进 `wms-app`。否决，bridge 仍要读 owner 表，只是移动越界位置，且需要 master 提供同样的软删除查询语义；不能解决能力缺口。

### D3. 三个跨模块外键全部保留

- 选择：
  - `sys_user.owner_id → owner.id` 保留；
  - `sys_data_scope_owner.owner_id → owner.id` 保留；
  - `owner_assignee.user_id → sys_user.id` 保留，并登记为清单新增发现。
- 理由：三者都提供应用层当前没有的引用完整性。删除会允许悬空引用；改成 CASCADE 会让误物理删除静默扩散。FK 只保证“存在”，不保证“未软删可用”，后者必须由未来用例在应用层判断。
- 备选：删除外键、改 CASCADE。均否决，前者削弱最后一道数据保护，后者掩盖误操作。
- 重新评估条件：引入货主物理清理、跨库拆分或数据范围能力正式落地时，必须同时检查外键与逻辑删除语义。

### D4. 架构门禁只记录缺口，不本轮加规则

- 选择：审计确认只有一条模块方向 `@ArchTest`；domain 框架依赖、Application→Web、Mapper 跨表、`wms-app` 业务判据、前端包边界均无自动门禁。后续另开 `harden-architecture-guards`。
- 理由：新增 ArchUnit 规则会立即改变构建结果，属于行为门禁变更；本轮范围是审计与文档修正。混入会让“证据清单”和“规则收紧”无法单独验收。
- 备选：顺手补规则。否决，可能引入存量红灯，且需要单独决定冻结策略与规则边界。

### D5. 并发验证使用人工/接口验证，不提交当前会红的测试

- 选择：交付三组并发步骤与不变量，由调用方执行：
  1. ACTIVE 同时 TERMINATED / SUSPENDED；
  2. SUSPENDED 同时 TERMINATED / ACTIVE；
  3. ACTIVE 同时 TERMINATED / TERMINATED。
- 记录：两个响应的 `code/message`、最终 `status`、`updated_at`、`updated_by`。`updated_by` 只能查库，因为接口不返回。
- 理由：当前实现无版本列、无锁、无条件更新，前两组在竞态下可能违反“终止后不可回退”。直接写自动化会红，并提前锁定修复方案；MockMvc 单线程也无法制造真并发。
- 备选：本轮直接修。否决，需在“条件更新 / 乐观锁 / 行锁”之间选型，属于独立行为 change。

### D6. 契约最终口径

- **请求体与响应体中的标识**：JSON 中一律字符串，以 `docs/api-convention.md:63` 为准。
- **路径参数**：可继续保持 `long`，OpenAPI 描述为 `integer(int64)`；路径不是 JSON，不触发 JS 大整数精度问题。前端拼路径时直接使用响应中的字符串 ID，不转 number。
- **现状判定**：响应侧已合规；请求侧 `CreateUserRequest.ownerId/roleIds`、`AssignRolesRequest.roleIds`、`CreateRoleRequest.menuIds`、`UpdateRoleRequest.menuIds` 仍是数字类型，列为契约缺陷，另开 `align-api-id-contract` 修复。
- **货主状态**：`DRAFT / POTENTIAL / ACTIVE / SUSPENDED / TERMINATED` 五态唯一来源为 `OwnerStatus`，数据库 CHECK 是值集合副本。
- **货主列表字段**：`id/code/name/ownerType/status/updatedAt`，不含 contact、mobile、负责人或 SKU 数。前端 `customer` 页的 mock 四态、联系人/手机号列和 `type` 命名均不得反向成为契约。
- 备选：把规范改成数字 ID。否决，超过 2^53 会丢精度，且响应侧已按字符串实现。

### D7. 文档修正以“可验证事实”为限

- 选择：
  - 修正 `verify_constraints.py` 的非法 SKU 前缀及关联 SKUID，使全部迁移后 SETUP 可执行；
  - 修正 API 说明中的接口数量与源 commit 状态，不手改生成物；
  - 修正根 README、前后端 README 的已实现/未实现范围；
  - 在 `migrate-network-console` 补 `completion.md` 和“待调用方验证、暂不归档”的状态，不回改历史 proposal 的方案描述。
- 理由：生成物只能由工具重跑；历史 proposal 是决策记录，不应改写，只需补充完成记录。
- 备选：直接手改 inventory/openapi 或重写 proposal。否决，前者破坏生成链，后者篡改决策历史。

### D8. 文档真源层级

- 当前行为：`openspec/specs/`。
- 本轮证据：`docs/research/architecture-boundary-snapshot-2026-10-27.md`。
- 本轮决策：本 `design.md` 与 `audit.md`。
- README 只描述使用方式与已验证状态，不再作为架构事实来源。

## Risks / Trade-offs

- **人工并发验证结果受调度影响** → 每个场景重复多轮；记录响应与数据库终态，不以单次成功下结论。
- **快照会过时** → 头部标注基线和时效，只读不回改；后续变化以新快照或当前源码为准。
- **修正工具夹具改变覆盖** → 保留原有“合法/非法”用例数量与目标，只替换被 V035 判为非法的字面量，不删除断言。
- **文档再次漂移** → 明确生成物、规格、快照、README 的职责；后续变更必须同步对应层级。
- **`migrate-network-console` 已全勾但未验证** → 只补完成记录，不归档；归档需调用方完成构建与走查。
- **外键保留不等于业务可用了** → 在快照中记录软删除语义缺口，避免把“FK 通过”误读成“货主可用”。

## Migration Plan

无数据库与运行时迁移。

文档/工具改动按以下独立提交落地：

1. 审计快照与 change 工件；
2. `verify_constraints.py` 夹具修正；
3. README 与 API 说明修正；
4. `migrate-network-console` 完成记录。

回滚方式为逐笔 `git revert`，不涉及数据回滚。

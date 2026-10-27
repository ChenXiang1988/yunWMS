# 审计结论：架构边界与门禁

- change：`audit-architecture-boundaries`
- anchor：`main@865d2ae4be3fa5e4ec47d07994ab0e252eb3c264`
- 审计时间：2026-10-27
- 完整证据快照：`docs/research/architecture-boundary-snapshot-2026-10-27.md`
- 决策记录：`design.md`
- 方法：只读源码、迁移、测试与文档；未执行构建、测试或数据库。

## 1. 结论摘要

1. 后端 Maven/Java 依赖单向无环，`wms-web` 未变成 `common` 垃圾抽屉，domain 当前未依赖框架。
2. 模块边界没有覆盖持久化层：`OwnerNameMapper` 仍跨模块读 `owner`；29 条外键中有 3 条跨模块，其中 `owner_assignee.user_id` 是本次新增发现。
3. 全后端只有 1 条 `@ArchTest`，只检查四个 Maven 模块方向；`docs/coding-project.md:68` 声称的 domain/infrastructure Web 隔离没有 ArchUnit 断言。
4. `wms-app` 的 `DictLookupAdapter` 含业务语义，违反或逼近“业务代码不得写进 wms-app”的边界，且无自动门禁。
5. 货主状态流转是读后写且 UPDATE 无状态前置条件，存在终止被并发暂停/恢复覆盖的风险；当前无并发测试。
6. 响应体 ID 已按字符串返回，请求体仍有 5 个字段是数字类型，OpenAPI 因此生成 integer；这是待修复契约缺陷。
7. `verify_constraints.py` 的 SKU 前缀夹具已被 `V035` 判为非法，文档也有多处过期。

## 2. MUST 约束与自动门禁

路径简写：

- `CP` = `docs/coding-project.md`
- `MBT` = `code/backend/wms-app/src/test/java/com/yunwms/app/ModuleBoundaryTest.java`
- `CI` = `.github/workflows/ci.yml`

| # | 约束 | 当前源码 | MBT / 其他门禁 | 结论 |
|---|---|---|---|---|
| C1 | `code/` 只含 `backend/frontend/tools` | 符合 | 无 | 无门禁 |
| C2 | 根目录不得有业务代码 | 符合 | 无 | 无门禁 |
| C3 | 后端四模块清单固定 | 符合 `code/backend/pom.xml:20-25` | 无 | 只能人工 |
| C4 | 模块按业务语义划分 | 无法机器判定；auth 已含字典/取号 | 无 | 只能人工 |
| C5 | `app → auth/master → web` 单向无环 | 符合 | MBT `:79-90`，CI 执行 | 已自动拦截（类引用级） |
| C6 | `wms-web` 不依赖业务模块 | POM/源码符合 | MBT 部分覆盖类引用 | 已自动拦截（类引用级） |
| C7 | auth 与 master 互不依赖 | 符合 | MBT `:86-87` | 已自动拦截 |
| C8 | `wms-app` 只装配，不写业务代码 | `DictLookupAdapter` 含启用品过滤、省属国、邮编默认必填 | 无 | 无门禁，存量灰区 |
| C9 | `wms-web` 仅 5 类白名单 | 当前恰好 5 个类 | 无；新增任意类型不会被拦 | 无门禁，存量合规 |
| C10 | 仅 interfaces/application 可依赖 web | domain/infrastructure 当前 0 import，application 20 个类引用 | MBT 允许 auth/master 访问 web，不区分内部层 | 无门禁，存量部分债务 |
| C11 | 三条 Web 隔离 MUST 由 ArchUnit 拦截 | 实际只有 1 条模块层规则 | 无 | 规范冲突 |
| C12 | domain 不依赖技术共享模块 | 当前仅 `java.*` 与本模块 | 无 | 无门禁，存量合规 |
| C13 | domain/infrastructure 不 import web | 当前 0 | 无 | 无门禁，存量合规 |
| C14 | Application→Web 是技术债，不得扩大 | 20 个 application 类引用 web | 无基线、无冻结 | 无门禁 |
| C15 | `interfaces → application → domain` | 当前符合 | MBT 层定义只到 `com.yunwms.<module>..` | 无门禁，存量合规 |
| C16 | domain 不依赖 Spring/MyBatis/Jackson | 当前 0 | 无 | 无门禁，存量合规 |
| C17 | `*Entity` 只在 infrastructure/persistence/entity | 5 个 Entity 全部符合 | 无 | 无门禁，存量合规 |
| C18 | domain/model 不带持久化注解 | 当前符合 | 无 | 无门禁，存量合规 |
| C19 | 聚合创建与重建分离、重建不绕过校验 | 当前人工可核 | 无 | 只能人工 |
| C20 | request/response 不被 application/domain import | 当前 0 | 无 | 无门禁，存量合规 |
| C21 | 三层之间显式转换类 | assembler 独立 | 无 | 只能人工 |
| C22 | 迁移只在 wms-app | 36 个迁移均在规定目录 | `BootstrapIntegrationTest` 只断言数量/表数 | 放错模块无门禁 |
| C23 | Mapper 不跨模块读表（实际债务） | `OwnerNameMapper` 读 `owner` | 无 XML 扫描 | 无门禁 |
| C24 | 前端 apps/packages 布局 | 符合 | CI 仅 typecheck/test/build | 无门禁 |
| C25 | 前端包职责、单一 UI、无跨端引用 | 当前符合 | `.codebuddy/rules` 未接 lint/CI | 无门禁 |
| C26 | `code/tools` 独立、不进镜像、不被后端依赖 | 符合 | 无 | 只能人工 |
| C27 | 新模块满足门槛才建 | 当前无新模块 | 无 | 只能人工 |

统计：已自动拦截 3 项；只能人工 6 项；无门禁 16 项；规范冲突 1 项。

## 3. 四个重点问题

### 3.1 domain 框架依赖

- 现状合规：domain 中 Spring、MyBatis、Jackson、Jakarta 等 import 为 0。
- 没有 ArchUnit 规则保护。
- `docs/coding-project.md:68` 的“由 ArchUnit 拦截”不成立。

### 3.2 Application 依赖 Web

- 20 个 application 类引用 `BizException/ErrorCode`。
- `MBT:85` 明确允许 auth/master 访问 web 层，因此这个耦合永远不会触发模块方向失败。
- 没有基线、冻结规则或专门的“新增引用”检查。

### 3.3 Mapper 跨表

- `OwnerNameMapper.xml:14-16` 是唯一跨模块读表。
- `MBT` 只分析 Java 字节码，不读 Mapper XML。
- `verify_constraints.py` 只验证 DDL 约束，且不在 CI 中。

### 3.4 `wms-app` 业务代码

- 生产代码只有启动类、OpenAPI 配置和 3 个 bridge。
- `DictLookupAdapter` 实现的不只是类型转换：
  - `:32` PROVINCE 字典类型；
  - `:40` postalCodeRequired 扩展键；
  - `:56-62` 仅启用项有效；
  - `:71-77` 按 parentValue 判州省归属；
  - `:90-99/:111-113` 缺州省或缺失键时默认必填。
- 该行为未全部写入 `DictLookup` 端口契约，且无门禁判定 app 是否含业务规则。

## 4. 货主状态流转并发验证

### 4.1 当前实现事实

- 入口：`PUT /api/console/owners/{id}/status`，权限点 `system:owner:update`。
- 请求体：`targetStatus` 字符串，`acknowledgedImpact` 可空；终止必须显式 `true`。
- 状态机：ACTIVE 可到 SUSPENDED/TERMINATED；SUSPENDED 可到 ACTIVE/TERMINATED；TERMINATED 为终态。
- 读取与判定：`OwnerStatusChangeService.java:80-105`。
- 写入：`OwnerMapper.xml:136-143` 只按 `id + deleted_at IS NULL` 更新，不比较旧状态。
- 没有 `version` 列、没有行锁、没有事务。
- 响应不返回状态；只能随后 GET 详情，`updated_by` 只能查库。

### 4.2 场景与不变量

| 场景 | 前置 | 并发请求 | 失败判据 |
|---|---|---|---|
| 终止 vs 暂停 | ACTIVE | `TERMINATED + acknowledgedImpact=true`；`SUSPENDED` | 终止返回 `code=0` 后最终状态仍为 SUSPENDED |
| 终止 vs 恢复 | SUSPENDED | `TERMINATED + true`；`ACTIVE` | 终止返回 `code=0` 后最终状态为 ACTIVE |
| 重复终止 | ACTIVE | 两份 `TERMINATED + true` | 任一请求非 0，或最终状态不是 TERMINATED |

记录项：两个响应的 `code/message`、GET 详情的 `status/updatedAt`、数据库 `status/updated_at/updated_by`、执行时间。

### 4.3 执行方式

由调用方在已启动的本地环境执行：

1. 准备两个拥有 `system:owner:update` 的账号，便于归因 `updated_by`。
2. 建货主到 ACTIVE；场景二先单发一次 SUSPENDED。
3. 同一时刻发送两个请求，可用 `curl ... & curl ... & wait` 或 `xargs -P2`。
4. 每轮后 GET 详情并查库；每个场景重复多轮，避免单次调度偶然性。
5. 任一场景违反不变量，记录为并发缺陷，不在本 change 修复。

不把上述写为自动化测试的原因：

- 当前实现可能直接红灯；
- 条件更新、版本列、行锁三种修复方案会改变测试断言位置；
- MockMvc 单线程无法制造真并发；
- 现有任务已登记并发覆盖缺口。

## 5. 契约最终口径

### 5.1 ID

- **请求体/响应体 JSON 标识一律字符串**，以 `docs/api-convention.md:63` 为准。
- **路径参数可保留 `long`**，OpenAPI 的 integer 仅描述路径参数，不参与 JSON 精度问题。
- 响应侧已合规；请求侧以下字段待修：
  - `CreateUserRequest.ownerId`
  - `CreateUserRequest.roleIds`
  - `AssignRolesRequest.roleIds`
  - `CreateRoleRequest.menuIds`
  - `UpdateRoleRequest.menuIds`
- 修复时需同步入参服务、重新生成 OpenAPI/inventory；另开 `align-api-id-contract`。

### 5.2 货主状态

唯一口径为五态：

```text
DRAFT / POTENTIAL / ACTIVE / SUSPENDED / TERMINATED
```

来源：`OwnerStatus.java:26-41,70-78`；数据库 CHECK 与规格是值集合副本。

前端 `customer/index.vue` 当前只有四态小写 mock，且把 DRAFT 标成“潜在”、POTENTIAL 缺失，属于待重写页面，不得反向修改契约。

### 5.3 货主列表字段

最终字段：`id`、`code`、`name`、`ownerType`、`status`、`updatedAt`。

不包含：联系人、手机号、负责人、SKU 数。前端现有 `type/contact/mobile` 与 `row-key=code` 均为临时 mock，应以后端 DTO/OpenAPI 为准。

### 5.4 前端消费范围

两个前端应用当前只消费：

- `POST /api/auth/login`
- `PUT /api/auth/password`

其余 23 个 operation 尚未接线。`customer` 页无请求、无 handler，属未完成页面。

## 6. 文档与工具修正范围

- `verify_constraints.py`：替换违反 `V035` 的 `AMZ1/UNL1` 及关联 SKUID 字面量，期望值不变，保持用例数量与约束目标不变。
- `docs/api/README.md`：接口数改为 25，说明源 commit `2d7ccc9` 是已核对的祖先版本，未重新生成。
- `README.md`：只描述实际已实现的 Controller 能力。
- `code/backend/README.md`：迁移数改为 36；已完成/未完成能力按源码修正。
- `code/frontend/README.md`：删除“登录为本地模拟”“尚未接后端”等过期描述。
- `migrate-network-console`：补 `completion.md`，记录 16/16 任务完成、迁移已实现、运行验证待调用方执行；不归档。

另记一处本轮范围外漂移：`OwnerCreateRequest.java:65`、`OwnerDetailResponse.java:72`、由其生成的 OpenAPI 示例以及 `openspec/specs/product-center/spec.md` 中仍有 `AMZ1` 示例。它不影响运行，但与 V035 的排除字符规则不一致，后续并入契约/文档清扫。

## 7. 后续 change

- `refactor-owner-name-query`：master 提供批量含软删的名称查询，经 app bridge 替换。
- `fix-owner-status-concurrency`：先决定条件更新、乐观锁或行锁，并补并发回归测试。
- `align-api-id-contract`：请求体 ID 改字符串，重生成台账。
- `harden-architecture-guards`：补 domain、层间、白名单、Mapper 跨表或前端 lint 门禁。
- `wire-owner-console-contract`：前端客户页按真实契约接线，删除 mock 四态与多余列。

## 逐项结论

截至 `716b928` 的 10 笔提交：

| 检查项 | 结论 | 证据 |
| --- | --- | --- |
| 路径白名单 | 通过 | `main...716b928` 共 13 个文件，全部为审计文档、README、Python 校验工具与 OpenSpec 记录 |
| 单次提交 ≤400 行 | 通过 | 10 笔提交增删合计为 128、202、283、60、29、57、7、36、15、37 行 |
| 迁移与代码分离 | 通过 | 全分支无 `db/migration`、`.sql` 或数据库变更 |
| 违规依赖 | 通过 | 无 `pom.xml`、`package.json`、lock 文件改动 |
| 测试断言 | 通过 | 未改任何业务测试；`verify_constraints.py` 的 SETUP 5、CASES 63、True/False 期望序列均未变 |
| 生成物 | 通过 | `openapi.json`、`inventory.md`、`primitive.css` 均未改 |
| OpenSpec 工件 | 通过 | `openspec validate audit-architecture-boundaries` 通过；migrate-network-console 仍 complete 未归档 |
| 运行时验证 | 已执行 | 后端 331 测试、前端、Python、DDL 63/63 均通过；并发缺陷已复现，详见 `completion.md` |

## 存量违约

| 提交 | 违规 | 处置 |
| --- | --- | --- |
| 本分支 | 无 | 不适用 |
| main 存量 | 14 笔提交超过 400 行 | 已在 `docs/agents.md:130` 登记，不重写历史；不属于本 change |

## 结论

**通过。** 交付范围、路径和提交纪律满足要求；运行时验证已在用户授权后执行，并发缺陷已复现，修复另开 change，详见 `completion.md`。

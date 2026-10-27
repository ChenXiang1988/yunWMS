# 交付记录：harden-architecture-guards

## 目标与实际交付

- 目标：把最容易静默破坏的架构规则变成构建期失败。
- 实际：新增 5 条 ArchUnit 规则、模块注册测试、Mapper 表所有权扫描、前端 UI 边界检查与 CI 步骤。

新增：

- `wms-app/src/test/java/com/yunwms/app/ArchitectureGuardTest.java`
- `wms-app/src/test/java/com/yunwms/app/ModuleRegistrationTest.java`
- `wms-app/src/test/java/com/yunwms/app/MapperTableAccessTest.java`
- `wms-app/src/test/java/com/yunwms/app/MapperTableAccessScanner.java`
- `wms-app/src/test/java/com/yunwms/app/TableOwnershipRegistry.java`
- `wms-app/src/test/resources/table-ownership.json`
- `code/frontend/scripts/check-ui-boundaries.mjs`

修改：

- `code/frontend/package.json`：新增 `check:ui`
- `code/frontend/packages/tokens/scripts/gen-css.test.ts`：色值断言改用 `primitives` 真源
- `.github/workflows/ci.yml`：前端 UI 检查与 Python 工具测试 job

## 关键设计

- ArchUnit 规则只覆盖当前已合规的硬边界，不引入业务语义判断。
- 模块注册测试要求新增顶层包先登记，避免 ArchUnit 空层静默通过。
- Mapper 表所有权清单覆盖 24 张表；`wms-auth` 读 `owner` 作为已登记只读例外，未知表失败。
- 前端检查零依赖，唯一色值白名单是 `primitives.ts`；令牌测试改为从真源构造断言。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| 后端全量 `./mvnw -B verify` | BUILD SUCCESS；wms-auth 167 / wms-master 83 / wms-app 102，合计 352 tests，0 失败 |
| 新增门禁反向验证 | 移除 owner 例外、改 sku 归属、删 app 注册、篡改各 ArchUnit 规则均能让测试变红 |
| 前端 `pnpm check:ui` | 通过；扫描 60 个源文件、2 个应用、3 个共享包 |
| 前端 `pnpm -r typecheck` | 通过 |
| 前端 `pnpm -r test` | 3 个令牌测试通过 |
| CI | 新增 `pnpm check:ui` 与 Python 工具测试 job；未在本地执行 GitHub Actions |

## 未验证项

- 未在 GitHub Actions runner 上实际运行工作流。
- 未在 CI 中运行 OpenSpec CLI。
- Mapper 扫描不解析 schema 限定名或复杂子查询；遇到未登记表会主动失败。
- `label_print_record`、`sys_config`、`sys_audit_log` 标为 `unassigned`，当前无 Mapper 访问；一旦访问会失败并要求先定归属。

## 已知风险

- ArchUnit 包名模式若随目录改名而漂移，规则可能静默空转；模块注册测试只能覆盖顶层包。
- 前端新增 app 需要同步更新脚本里的 UI 库登记表。
- 令牌测试改动是把硬编码色值改为引用真源，不改变断言目标。

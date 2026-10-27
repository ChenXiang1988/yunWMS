# Design

## Context

详见 `openspec/changes/archive/2026-10-27-audit-architecture-boundaries/audit.md`。当前门禁只覆盖四个 Maven 模块之间的类引用。

## Goals / Non-Goals

**Goals:**

- 让最容易静默破坏的规则在构建期变红。
- 不引入新依赖，不制造大量假阳性。
- 新模块或跨模块表访问必须留下显式登记。

**Non-Goals:**

- 不自动判定业务语义是否正确。
- 不把 `wms-app` 的 bridge 业务规则强行重写。
- 不做全量 SQL 解析器。

## Decisions

### D1. ArchUnit 规则放 `wms-app`

- 选择：新增 `ArchitectureGuardTest`，扫描 `com.yunwms` 生产类。
- 理由：只有 wms-app 同时包含全部模块类；ArchUnit 依赖已在 test scope。
- 规则：
  - domain 不依赖 Spring/MyBatis/Jackson/Jakarta/MyBatis；
  - domain/infrastructure 不依赖 `com.yunwms.web`；
  - application 不依赖 `interfaces.controller.request|response`；
  - `com.yunwms.web` 的简单类名必须在 5 类白名单；
  - `*Entity` 必须位于 `infrastructure.persistence.entity`。

### D2. 模块注册检查

- 选择：扫描 `com.yunwms` 下的顶层包，只允许 `web/auth/master/app`；新增模块必须先更新测试中的注册表与 `ModuleBoundaryTest` 层定义。
- 理由：ArchUnit 的空层会静默通过，新增模块最容易漏。
- 备选：Maven enforcer。只检查 pom，不检查包结构。

### D3. Mapper 表所有权用清单 + 轻量扫描

- 选择：`wms-app/src/test/resources/table-ownership.json` 记录 24 张表归属与禁止例外；测试扫描 `wms-auth`/`wms-master` 的 Mapper XML，提取 FROM/JOIN/INSERT/UPDATE/DELETE 表名，要求表 Owner 等于模块，或命中显式例外。
- 例外：`wms-auth` 读 `owner`，对应 `OwnerNameMapper` 的已登记债务；清单里写明原因。
- 限制：不解析复杂子查询；CTE 名称在忽略列表中。未知表名视为失败，迫使登记。
- 备选：SQL 解析库。否决，增加依赖且当前 SQL 简单。

### D4. 前端边界脚本不引入依赖

- 选择：Node ESM 脚本读取 package.json 和源码文件，检查：
  - 每个 app 只允许 `ant-design-vue` 一套 UI 库；
  - `packages/request`、`packages/tokens` 不依赖 UI 库；
  - `.vue/.ts/.scss` 中禁止 `#hex`、`rgb(`、`rgba(`、`hsl(`，例外 `packages/tokens/src/primitives.ts`。
- 理由：无 lint 框架也能在 CI 拦截硬规则；避免新增 ESLint 配置与依赖。
- 备选：ESLint/stylelint。后续可替换，本 change 不引入。

### D5. CI 最小增量

- 前端 job 增加 `pnpm check:ui`。
- 后端 `mvn verify` 自动包含新 ArchUnit/Mapper 测试。
- 新增 tools job：api-inventory、cad-parser、audit-delivery 的 unittest。
- 不在 CI 中运行 OpenSpec CLI（本地工具，不保证 runner 安装）。

## Risks / Trade-offs

- **ArchUnit 规则误伤合法代码** → 规则只覆盖当前已合规的硬边界；先在本地跑全量。
- **Mapper 扫描漏掉复杂 SQL** → 清单要求未知表失败；当前 SQL 无复杂 CTE 之外的跨表。
- **前端色值扫描误报** → 只排除 primitives.ts，生成物 primitive.css 不在 `.vue/.ts/.scss` 范围。
- **CI 增加耗时** → Python 测试与前端检查均为秒级。

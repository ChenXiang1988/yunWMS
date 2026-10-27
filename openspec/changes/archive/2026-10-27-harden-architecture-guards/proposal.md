# Proposal

## Why

`audit-architecture-boundaries` 确认：全后端只有一条模块方向 ArchUnit 规则；domain 框架隔离、模块内分层、request/response 归属、Entity 归属、Mapper 跨表、前端包与色值规则都没有自动门禁。文档承诺大于实际拦截。

## What Changes

- 新增后端 ArchUnit 规则：domain 框架隔离、domain/infrastructure 不依赖 Web、application 不依赖 request/response、Web 白名单、Entity 归属。
- 新增模块注册检查：出现新的 `com.yunwms.<module>` 顶层包时测试失败，强制同步模块边界。
- 新增 Mapper 表所有权清单与静态扫描；`OwnerNameMapper` 作为显式例外。
- 新增前端 `check:ui` 脚本：单一 UI 库、request/tokens 不依赖 UI、业务源码无色值字面量。
- 接入 CI：前端 `check:ui`；后端已有 `mvn verify`；新增 Python 工具测试步骤。

**BREAKING**：无业务行为变更。违规的新代码会让构建失败，这是本变更的目的。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。

## Impact

- **新增**：ArchUnit 测试、Mapper 表所有权测试与清单、前端边界脚本
- **修改**：`ModuleBoundaryTest`、`code/frontend/package.json`、`.github/workflows/ci.yml`
- **不变**：业务代码、数据库、API、前端页面

# Tasks

## 1. 后端门禁

- [x] 1.1 新增 ArchUnit 规则：domain 框架隔离、domain/infrastructure 不依赖 web、application 不依赖 request/response、web 白名单、Entity 归属，验证：现有代码通过。
- [x] 1.2 新增模块注册检查，验证：新增未登记顶层包时测试失败。
- [x] 1.3 新增表所有权清单与 Mapper 扫描测试，验证：现有 24 表通过，改为跨模块访问时测试失败。

## 2. 前端门禁

- [x] 2.1 新增 `scripts/check-ui-boundaries.mjs` 与根脚本 `check:ui`，验证：当前前端通过，出现第二套 UI 库或色值字面量时失败。
- [x] 2.2 在 CI 前端 job 调用 `pnpm check:ui`，验证：工作流语法与命令路径正确。

## 3. CI 与交付

- [x] 3.1 新增 Python 工具测试 job/步骤，覆盖 api-inventory、cad-parser、audit-delivery，验证：本地命令可通过。
- [x] 3.2 运行后端全量与前端 typecheck/test/build/check:ui，验证：全部通过。
- [x] 3.3 运行只读审计并写 `completion.md`，验证：路径、行数、无业务行为改动合规。

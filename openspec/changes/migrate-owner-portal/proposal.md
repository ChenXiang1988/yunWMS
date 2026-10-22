# Proposal

## Why

Web 端已决定迁移到 Ant Design Vue 4（产品侧要求，见 `docs/research/frontend-stack.md`）。调研同时确认了一个硬约束：ant-design-vue 4.2.6 的 `ThemeConfig` 只有 `token` / `components` / `algorithm` / `hashed` / `inherit`，**没有 `cssVar` 开关**，包内 `cssVariables.js` 是 v3 时代 `theme.primaryColor` 的兼容路径。这意味着 antd 的颜色只能来自 JS 对象，现有"CSS 变量单一真源"在 Web 端守不住。

因此迁移不能只是换组件，必须先把令牌真源从 CSS 移到 TS，再由脚本生成 CSS 供 Element Plus 与 PDA 使用。这一步不完成，antd 侧取色就没有合法来源。

选 `owner-portal` 先行，是因为它只有 3 个页面（登录 / 工作台 / 商品管理）、单层布局、无嵌套菜单，是验证"antd 能否接住现有令牌"这一最大风险的最小面积；失败代价最低。

## What Changes

- **令牌真源**：新增 `packages/tokens/src/primitives.ts` 为唯一真源，`primitive.css` 转为生成物，由 `scripts/gen-css.ts` 产出
- **antd 适配**：新增 `packages/tokens/src/web-antd.ts`，把语义值映射为 `ThemeConfig`，不含任何色值字面量
- **组件包分叉**：新建 `packages/ui-web`（antd 版 `PageHeader` / `StatusTag` / `EmptyState`），`owner-portal` 切换过去；旧 `packages/ui` 保留给未迁移的 `network-console`
- **应用迁移**：`owner-portal` 移除 `element-plus`，改用 `ant-design-vue`；错误提示改由 `App.useApp()` 注入
- **文档同步**：`openspec/config.yaml` 技术栈行标注"迁移中"
- **BREAKING**：无业务行为变更；`owner-portal` 视觉细节可能与 EP 版存在差异，由人工验收确认

**不做**：`network-console` 迁移（下一个变更）、`apps/pda`、菜单改造（`add-inline-menu`）、`/_tokens` 对照页。

## Capabilities

### New Capabilities

- `design-token-pipeline`: 令牌唯一真源位于 TS、生成物可复现、各端适配器只做映射不引入字面量。

### Modified Capabilities

无。本次不改变任何业务规则，`product-center` 等能力仅更换实现所用的 UI 库。

## Impact

- **代码**：`packages/tokens`（新增 3 个文件、改 2 个）、新建 `packages/ui-web`、`apps/owner-portal` 全部页面与布局
- **依赖**：`apps/owner-portal` 增加 `ant-design-vue`；移除 `element-plus` 与 `@element-plus/icons-vue`
- **构建**：CI 保持 `typecheck + build`，无新增 job
- **文档**：`openspec/config.yaml` 技术栈行；`docs/frontend.md` 的令牌分层与已验证事项表
- **风险**：antd 组件 token 名（`itemHeight` 一类）尚未核对，若与预期不符，只影响适配器取值，不影响结构

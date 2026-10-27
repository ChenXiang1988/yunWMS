# Proposal

## Why

`owner-portal` 已完成迁移（见 `migrate-owner-portal`），仓网端仍运行在 Element Plus 上，两个 Web 应用从 UI 库层面已经分叉。仓网端是 66 处 EP 组件的最大使用方，也是 `packages/ui`（EP 版）与 `@yunwms/tokens/element-plus` 适配器唯一剩余的依赖来源——只要它还留着，这两份过渡产物就删不掉。

## What Changes

- **组件包**：`apps/network-console` 从 `@yunwms/ui` 切到 `@yunwms/ui-web`
- **布局壳**：`BasicLayout.vue` 换成 antd 组件，左侧菜单为静态 `a-menu`，规格与货主端完全一致
- **页面**：6 个页面迁移（login / dashboard / system / warehouse / customer / product）
- **清理**：删除 `packages/ui`、`@yunwms/tokens/element-plus` 适配器及其 `exports` 条目、`element-plus` 与 `@element-plus/icons-vue` 依赖
- **文档**：`openspec/config.yaml` 技术栈行去掉"进行中"；`docs/frontend.md` 端表与已验证项同步
- **BREAKING**：无业务行为变更；视觉细节可能与 EP 版不同，由人工走查确认

**不做**：后端树驱动的递归菜单（属 `add-inline-menu`）、密度层 TS 化（B2 决定暂不）、`apps/pda`。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。本次只更换实现所用的 UI 库，不改变任何业务规则。

## Impact

- **代码**：`apps/network-console` 的布局与 6 个页面、`main.ts`；删除 `packages/ui` 与一个令牌适配器
- **依赖**：新增 `ant-design-vue`、`@ant-design/icons-vue`；移除 `element-plus`、`@element-plus/icons-vue`
- **令牌**：`packages/tokens/src/element-plus.css` 删除，`exports` 移除 `./element-plus`
- **风险**：`customer` 页有 22 处 EP 组件，是最大的一块；`el-table` 插槽式列定义需改写为 `columns` + `bodyCell`

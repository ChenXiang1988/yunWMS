# 交付记录：migrate-network-console

## 状态

**实现与验收完成，可归档。**

- `tasks.md` 的 16 条任务全部勾选。
- 仓网端已从 Element Plus 切到 Ant Design Vue 4，`packages/ui` 与 `tokens/element-plus` 已按任务记录删除。
- 2026-10-27 完成前端 typecheck/test/build、UI 边界检查与浏览器页面走查，结果见下文。

## 目标与实际交付

- 目标：仓网端与货主端统一到 Ant Design Vue 4，并删除 Element Plus 过渡组件包和令牌适配器。
- 实际：`apps/network-console` 使用 `ant-design-vue`、`@ant-design/icons-vue`、`@yunwms/ui-web`；`packages/` 当前为 `request`、`tokens`、`ui-web`。
- 未改变业务行为与后端接口。

证据：

- `openspec/changes/migrate-network-console/tasks.md`
- `code/frontend/apps/network-console/package.json`
- `docs/frontend.md:30`

## 关键设计

- 菜单保持静态 `a-menu`，规格与货主端一致；后端树驱动留给 `add-inline-menu`。
- 表格改为 `columns + bodyCell`，每列显式宽度并设置 `scroll.x`。
- 组件包先切换、页面迁完后才删除旧包，避免中间态不可渲染。
- 详细决策见 `design.md` D1-D7。

## 验证结果

已完成且可由文件核对：

- 16/16 任务在 `tasks.md` 勾选。
- 仓网端依赖中无 `element-plus`。
- `packages/ui` 与 `packages/tokens/src/element-plus.css` 不存在。
- `pnpm -r typecheck`、`pnpm -r test`、`pnpm -r build`、`pnpm check:ui` 通过。
- 浏览器走查：真实后端完成登录、首次强制改密、进入客户管理；工作台、商品中心、仓库网络、系统管理页面均能打开并渲染标题。
- 客户页进一步完成新建、详情、编辑、状态流转、终止确认与删除草稿。

## 归档条件

归档条件已满足：构建与页面走查通过，Element Plus 无残留。像素级视觉差异仍建议在后续 UI 走查中人工确认。

## 已知风险

- 两个应用若同 origin 部署，`@yunwms/request` 的固定 token key 会让令牌跨端可见；已在 `implement-owner/audit.md` 登记。
- 仓网端客户页仍是静态 mock，不代表 `customer-management` 契约已接通。
- 本次没有重跑视觉对比，迁移后的像素级差异无法从静态文件确认。

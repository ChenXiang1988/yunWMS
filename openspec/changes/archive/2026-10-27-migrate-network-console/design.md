# Design

## 1. 主题：沿用同一份 antdTheme，不为 compact 另造一套

`web-antd.ts` 目前只映射颜色、字体族与圆角，字号与控件高度保持 antd 默认。仓网端迁过来后**继续使用同一份主题**。

理由：

- B2 已决定暂不做密度层 TS 化。若要给 compact 单独一套 antdTheme，就得在 TS 里再写一份 `fontSize: 13`、`controlHeight: 28`，这正是我们一路在消灭的"第二份真源"
- 与"两端观感一致"的目标同向：antd 组件内部尺寸在两端完全相同

代价与处理：

- 仓网端 `data-density="compact"` 仍然生效，但**只作用于自研 CSS**（表格内边距、页面留白、卡片间距），不影响 antd 组件内部
- 因此仓网端会出现"自研文本 13px、antd 组件 14px"的混排。若某处明显违和，用局部 CSS 变量覆盖，不在主题层解决

## 2. 菜单：静态 a-menu，规格与货主端逐项相同

沿用 `migrate-owner-portal` 的结论：

- 导航尺寸来自 `shell.css`（`--menu-item-height: 44px` 等），不随密度变化
- antd 的左内边距由 `inlineIndent` 以行内样式下发，必须传 `:inline-indent="8"`，CSS 覆盖无效
- `Menu` 用 `import { Menu as AntMenu }` 别名，避开原生 `<menu>`
- 菜单项 `key` 用路由 path，`selected-keys` 由 `route.path` 派生

本变更只做静态菜单；后端树驱动与递归渲染留给 `add-inline-menu`。

## 3. 表格：复用上次的教训

`el-table` 的插槽式列定义改为 `columns` 数组 + `#bodyCell`。必须同时处理两点，否则行高会被撑开：

1. **每一列都要有宽度**（含原本未设宽的名称列）
2. 设置 `:scroll="{ x: 列宽合计 }"`，否则列被挤压导致文字折行

上一轮货主端就是因为名称列没设宽度，行高从 49px 被撑到 91px。

## 4. 组件包切换顺序

1. 先让 network-console 依赖 `@yunwms/ui-web` 并切换 3 个组件引用
2. 全部页面迁完后，才删除 `packages/ui`
3. 中间态两份组件并存，属规则允许的过渡

## 5. 删除 token 适配器

`packages/tokens/src/element-plus.css` 在最后一个 EP 使用方消失后即成为死代码，连同 `exports` 里的 `./element-plus` 一起删除。留着会让下一个人以为 EP 仍受支持。

## 6. 被否方案

| 方案 | 落选原因 |
| --- | --- |
| 给 compact 单独一套 antdTheme | 会在 TS 里造出第二份密度真源，与已建立的"单一真源"原则冲突 |
| 保留 `packages/ui` 不删 | 无人使用后会腐烂；且会让"每端一个组件包"的规则形同虚设 |
| 保留 `element-plus.css` 备用 | 死配置，与已删除的深色 shell profile 同类问题 |
| 与 `add-inline-menu` 合并做 | 范围翻倍，且菜单需要后端接口，会被后端进度拖住 |

## 7. 风险

| 风险 | 缓解 |
| --- | --- |
| `customer` 页 22 处 EP 组件，可能单页超 400 行 | 拆成多个提交：表单/筛选、表格、弹窗分开 |
| 迁完后自研 CSS 与 antd 组件字号混排 | 交付时人工走查；必要时局部覆盖 |
| 表格列宽遗漏导致行高异常 | 每列显式宽度 + `scroll.x`，验证时实测行高 |

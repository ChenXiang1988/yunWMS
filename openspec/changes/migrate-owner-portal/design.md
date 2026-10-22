# Design

## 1. 令牌真源

### 1.1 结构

`packages/tokens/src/primitives.ts` 按组导出，与现有 CSS 变量一一对应：

```ts
export const primitives = {
  color: { blue: { 50: '#eef4ff', ... }, gray: {...}, green: {...}, ... },
  font: { family: {...}, size: { xs: 12, sm: 13, ... }, leading: {...}, weight: {...} },
  space: { 0: 0, 1: 4, ... },
  radius: { sm: 2, md: 6, lg: 8, full: 999 },
  shadow: { xs: '0 1px 2px 0 rgba(20,24,29,0.06)', ... },
  z: { dropdown: 1000, ... },
  motion: { durationFast: '120ms', easeOut: 'cubic-bezier(...)' }
} as const
```

**取值类型约定**：尺寸类存**数字**（`14` 而非 `'14px'`），因为 antd 的 `fontSize` / `borderRadius` 要数字；CSS 生成时补 `px`。颜色、阴影、缓动存字符串。`space[0]` 与 `0` 特判，不补单位。

### 1.2 生成

`packages/tokens/scripts/gen-css.ts` 读取 `primitives`，输出 `src/primitive.css`，首行写入「由 gen-css.ts 生成，禁止手改」。

运行：`pnpm --filter @yunwms/tokens gen`。

不引入第三方构建工具（style-dictionary 已评估落选），用 Node 内置能力 + 类型剥离运行；`engines` 已为 `>=22.13`。

**输出顺序固定**：颜色 → 字体 → 字号 → 行高 → 字重 → 间距 → 阴影 → 层级 → 动效。顺序固定才能保证生成结果稳定可比。

### 1.3 迁移正确性判据

判据不是「文件长得一样」，而是「**变量集合完全一致**」：把迁移前 `primitive.css` 与生成后的文件分别解析成 `{变量名: 值}` 映射，两者必须相等。格式差异（空行、注释位置）允许存在。

## 2. antd 适配

`packages/tokens/src/web-antd.ts` 导出 `antdTheme: ThemeConfig`，全部值引用 `primitives`，**不出现任何色值字面量**。

```ts
token: {
  colorPrimary: primitives.color.blue[500],
  fontSize: primitives.font.size.sm,
  borderRadius: primitives.radius.md,
  ...语义色、文字色、背景色、边框色
}
```

### 已知限制：密度轴在 antd 侧无法用 CSS 变量切换

antd 的 token 是 JS 对象，密度切换（`compact` / `comfortable`）无法靠 `data-density` 选择器生效。

- **本期处理**：`owner-portal` 固定 `comfortable`，`antdTheme` 只输出一套值
- **将来若 Web 端需要密度切换**：按密度生成两套 `antdTheme`，切换时替换 `ConfigProvider` 的 `theme`，代价是一次全量样式重算

这条限制写入 `docs/frontend.md`，避免以后误以为 antd 侧也能跟着 `data-density` 变。

## 3. 错误提示注入

`createHttp` 的 `onError` 在模块加载时就创建，无法直接拿到 `App.useApp()` 的实例。

方案：`apps/owner-portal/src/api/client.ts` 暴露 `setHttpErrorHandler(fn)`，模块内保存；`AppBootstrap` 组件在 `<App>` 子树内取得 `message` 实例，再注入。

**API 名称必须按 Vue 版写**（实测 ant-design-vue 4.2.6）：

- `<App>` 组件不是根入口的具名导出，而是 `ant-design-vue/es/app` 的**默认导出**
- 没有 React 的 `App.useApp()`；对应物是 `ant-design-vue/es/app/context` 的 `useInjectAppContext()`，返回 `{ message, notification, modal }`
- `message/index.js` 中不含任何全局配置引用，**静态 `message.error` 确实不消费 ConfigProvider**，因此必须走上下文实例

注入点必须是 `<App>` 的**子组件**，在 `App.vue` 同层调用会拿到空上下文。

### 3.1 功能色浅底需显式指定

实测：只设 `colorSuccess` 时，antd 会用自己的算法派生浅底，`Tag` 背景为 `rgb(192,209,199)`，与 `--color-success-bg: #e7f6ee` 不一致。显式补上 `colorPrimaryBg` / `colorSuccessBg` / `colorWarningBg` / `colorErrorBg` / `colorInfoBg` 后，背景变为 `rgb(231,246,238)`，与令牌一致。这些 token 位于 `es/theme/interface/maps/colors.d.ts`，可通过 `AliasToken extends MapToken` 设置。

**结论：antd 的派生色不是免费对齐的，凡是界面上会出现的浅底、边框，都要逐个显式映射。**

## 4. 组件包分叉

- 新建 `packages/ui-web`，依赖 `ant-design-vue`
- `StatusTag` 用 `a-tag`，`EmptyState` 用 `a-empty`，`PageHeader` 用纯布局 + 令牌（不需要库组件）
- 旧 `packages/ui` 原样保留，供 `network-console` 使用，`migrate-network-console` 归档时删除
- 过渡期两份组件并存，属已知中间态

## 5. 被否方案

| 方案 | 落选原因 |
| --- | --- |
| 用 antd 的 `theme.cssVar` 维持 CSS 单源 | 已实测 4.2.6 无该字段，方案不存在 |
| `colorPrimary: 'var(--color-primary)'` | antd 用 cssinjs 从主色派生色板，需要可解析色值，CSS 变量字符串无法参与派生 |
| 保留 Element Plus 作为第二套库并存 | 违反「单应用单库」，且两套 CSS 会互相污染 |
| 先迁 `network-console` | 页面多、布局复杂、含未来菜单，失败代价远大于 owner-portal |

## 6. 风险

| 风险 | 缓解 |
| --- | --- |
| antd 组件 token 名与预期不符（Menu / Button / Table） | 任务 2.1 先行核对，结论回写本文档；只影响取值，不影响结构 |
| antd 静态 message 不跟主题 | §3 的注入方案，验收项 4.2 专门验证 |
| 视觉差异（圆角、间距、控件高度） | 由人工走查 3 个页面确认；令牌值来自同一真源，差异应限于 antd 组件内部实现 |
| 生成物被手改 | 生成文件首行标注禁止手改；`gen` 后 `git diff` 非空即说明手改过 |

## 7. ant-design-vue 4.2.6 组件级 token 核对（任务 2.1 产出）

来源：`npm pack ant-design-vue@4.2.6` 解包读取类型定义，未安装到工作区。

| 位置 | 结论 |
| --- | --- |
| `es/config-provider/context.d.ts` | `ThemeConfig` 五字段：`token` / `components` / `algorithm` / `hashed` / `inherit`，无 `cssVar` |
| `es/button/style/index.d.ts` | `ComponentToken` 为空对象，Button 无组件级 token |
| `es/table/style/index.d.ts` | `ComponentToken` 为空对象 |
| `es/menu/style/index.d.ts` | 有组件级 token，但为 **antd v4 命名**：`radiusItem`、`radiusSubMenuItem`、`colorItemText*`、`colorItemBg*`、`itemMarginInline`、`dropdownWidth`、`zIndexPopup` |
| `es/menu/style/index.js` L312 | `menuItemHeight: controlHeightLG` —— 菜单项高度来自全局 alias token，组件层无法直接设置 |
| `es/index.d.ts` | 根入口不导出 `ThemeConfig`；包无 `exports` 字段，深路径导入可用 |

**一处更正**：此前讨论中出现的 `itemHeight` / `collapsedWidth` 是 antd（React v5）的命名，**在 ant-design-vue 4.2.6 中不存在**。菜单项高度只能通过 `controlHeightLG` 调整，侧栏配色用 `Menu` 的 `colorItem*` / `colorItemBg*`。该结论作为 `add-inline-menu` 的前置输入。

**对本期的影响**：`owner-portal` 只用全局 token；Button 与 Table 没有可调组件 token，视觉差异只能由全局 token 与 CSS 覆盖控制。

## 8. 圆角归属调整

任务 2.2 需要 `borderRadius` 的数值，而圆角原本只存在于 `density.css`。处理方式：

- 圆角并入 `primitives.radius`，由生成器输出 `--radius-*`
- `density.css` 的 comfortable 块不再重复声明（取值与 primitive 相同），只在 compact 档覆盖为 2/4/6
- 效果：圆角与颜色、字号一样只有一个真源；compact 仍可覆盖

`web-antd.ts` 不带 `ThemeConfig` 类型注解，因为 `packages/tokens` 不得依赖 UI 库（类型导入同样要求安装该包）。结构正确性由 `ConfigProvider` 在调用点校验。

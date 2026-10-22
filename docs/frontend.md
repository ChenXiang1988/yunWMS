# 前端约定

本文件承载前端实现细节。原则性约束在 `AGENTS.md` 的「前端」「设计令牌」两节，机器可检的硬门禁在 `.codebuddy/rules/frontend-ui/RULE.mdc`，本文不重复。

---

## 1. 多端与包结构

### 1.1 端与应用

| 端 | 应用 | UI 库 | 组件包 |
| --- | --- | --- | --- |
| 仓网端 | `apps/network-console` | Element Plus（待迁移至 Ant Design Vue 4） | `packages/ui`（过渡） |
| 货主端 | `apps/owner-portal` | Ant Design Vue 4 | `packages/ui-web` |
| PDA | `apps/pda`（待建） | Vant 4 | `packages/ui-pda`（待建） |

单仓端形态未定（独立应用或多集群部署形态），定案前不新增应用与包。

### 1.2 包职责

| 包 | 职责 | 禁止 |
| --- | --- | --- |
| `packages/tokens` | 设计令牌唯一真源与各端适配器 | 依赖任何 UI 库 |
| `packages/request` | HTTP 封装、错误码、Token | 依赖任何 UI 库；提示通过注入适配器 |
| `packages/ui-web` | Web 端基础组件 | 被 PDA 引用 |
| `packages/ui-pda` | PDA 端基础组件 | 被 Web 端引用 |

跨端通用组件在各自包内分别实现，不做"一套代码两端跑"的抽象。

过渡期：现有 `packages/ui`（Element Plus 版）在 `migrate-network-console` 归档前保留，由未迁应用使用；归档时删除，功能由 `packages/ui-web` 承接。

### 1.3 新增一个端

1. 在 `packages/tokens` 增加该端的语义档与适配器，并在 `exports` 增加子路径
2. 新建 `packages/ui-<端>`，只依赖该端 UI 库
3. 新建 `apps/<应用>`，引入令牌 + 对应组件包
4. 更新 `AGENTS.md`「前端」节的包清单与本节表格

---

## 2. 设计令牌

### 2.1 三个正交轴

| 轴 | 属性 | 取值 | 管什么 |
| --- | --- | --- | --- |
| 主题 | `data-theme` | `light`（预留 `dark`） | 颜色 |
| 密度 | `data-density` | `compact` / `comfortable` / `touch` | 尺寸：控件高度、字号、行高、间距 |
| 平台 | `data-platform` | `shell-network` / `shell-owner` / `pda` | 布局、外壳色、安全区 |

三个轴互不代替：`compact` 只表示信息密度，**不得**同时决定外壳颜色；`touch` 只表示触摸端尺寸，不得表示"这是 PDA"。

### 2.2 分层与引用

```
primitives.ts（原始值，唯一真源）
  ├─ primitive.css（由 primitives.ts 生成，禁止手改）
  ├─ semantic.css（语义色：color-bg-*、color-text-*、color-border-*）
  ├─ density.css（尺寸：control-height-*、menu-item-*、table-*、radius-*）
  ├─ shell.css（外壳色：sidebar-*）
  └─ adapters（element-plus.css / web-antd.ts / pda-vant.ts）
```

- `tokens.css` 只做 `@import` 聚合，不写任何变量
- 业务代码只引用 semantic 与 component 层（density / shell 属于 component 层）
- primitive 只允许被 semantic、density、shell 与各端适配器引用
- 色值字面量只允许出现在 `primitives.ts`；`primitive.css` 是生成物，手改会被单测拦截

### 2.3 跨端命名

语义名在两端保持**同名**，只有值不同。平台专有令牌才加前缀。

| 语义名 | Web 值 | PDA 值 |
| --- | --- | --- |
| `control-height-md` | 32px | 56px |
| `font-size-base` | 14px | 16px |
| `list-row-height` | 48px | 72px |
| `tap-target-min` | —（不适用） | 56px |
| `action-bar-height` | —（不适用） | 64px |

禁止出现 `--pda-danger` 这类"同语义不同名"的写法。

### 2.4 域色（不引入）

2026-10-22 决定不设域色令牌。六个业务域（入库 / 出库 / 库内 / 承运 / 计费 / 基础资料）统一用语义色加图标区分，不维护一套没有真实使用场景的色板。

若将来某个域确实需要专属色，须在该域页面落地时连同使用场景一并提出，不在令牌层预先占位。

### 2.5 新增或修改令牌

1. 改 `packages/tokens/src/primitives.ts`
2. 运行 `pnpm --filter @yunwms/tokens gen` 重新生成 `primitive.css`
3. 运行 `pnpm --filter @yunwms/tokens test`，确认生成物与源码一致（该单测同时用于拦截手改）
4. 同步各端适配器
5. 以上放在**同一次提交**，不得只改一侧
6. 若影响视觉，更新 `/_tokens` 对照页截图

### 2.6 已验证与待验证

| 事项 | 状态 |
| --- | --- |
| Element Plus 通过 CSS 变量桥接 | 已验证，现有实现 |
| antd-vue 4 是否支持 `theme.cssVar` 模式 | **已验证不支持**（4.2.6 的 `ThemeConfig` 无该字段）。Web 端令牌将改为 TS 真源，生成 CSS；`primitive.css` 届时转为生成物，不再手改 |
| antd-vue 4 的 Menu 组件 token 名 | **已验证**：Menu 为 antd v4 命名（`radiusItem`、`colorItemText*`、`itemMarginInline`）；Button / Table 无组件级 token；菜单项高度来自全局 `controlHeightLG` |
| antd 侧能否用 CSS 变量切换密度 | **不支持**。`ConfigProvider` 的 theme 是 JS 对象，不随 `data-density` 变化；需要按密度准备两套主题并替换 `ConfigProvider` |
| Vant 4 变量对 PDA 场景的覆盖度 | **未验证**，见 `docs/research/frontend-stack.md` |

---

## 3. 菜单与导航

### 3.1 后端接口字段

```ts
interface MenuNodeRaw {
  id: string
  path: string            // 路由路径，必须以 / 开头
  title: string           // 后端按 Accept-Language 返回的单语文案
  titleI18n?: Record<string, string>  // 可选，多语言 map
  icon?: string           // 前端图标注册表里的标识
  children?: MenuNodeRaw[]
  hidden?: boolean
}
```

- 前端用 `path` 作为菜单 key，`id` 只做业务引用
- `title` 由后端提供，前端不维护业务词表
- `icon` 只传标识，组件映射留在前端

### 3.2 key 规则

| 节点类型 | key |
| --- | --- |
| 叶子 | 完整路由 path，如 `/outbound/wave/list` |
| SubMenu | 路径前缀，如 `/outbound/wave` |

- 全局唯一；重复即报错，不做容错
- 禁止用数组下标、`title`、业务 `id` 当 key
- 分组节点与其首页节点的 path 不得相同
- 子节点必须位于父节点前缀之下，转换层校验，不满足直接抛错

### 3.3 当前路由到菜单状态

1. 精确命中叶子 → 该 key 为选中项
2. 否则取**最长前缀命中**（按路径段边界匹配，避免 `/outbound/wave` 误命中 `/outbound/waveform`）
3. 祖先链取自节点预存的 `parentKeys`，只保留 SubMenu
4. 都没命中 → 选中项为空，主动清除高亮

动态段（如 `/outbound/wave/picking/detail/:id`）回落到菜单里登记的那一层，不要求详情页登记为菜单。

### 3.4 折叠态

- 折叠后只保留一级图标与文字，深层通过浮层逐级展开
- 折叠态不维护展开集合；展开回来时按当前路由还原祖先链
- 一级节点必须提供 `icon`，否则折叠后无法辨识

### 3.5 渲染约束

- 菜单树必须递归渲染，层数由数据决定
- 跳转统一在菜单事件里 `router.push()`，禁止用链接包裹菜单项
- 高亮与展开由路由派生，组件内部不持有权威状态

---

## 4. 组件约定

### 4.1 放哪里

| 类型 | 位置 |
| --- | --- |
| 跨端基础组件（标题、状态、空态） | `packages/ui-web` 与 `packages/ui-pda` 各自实现 |
| 单应用内通用组件 | `src/components/<模块>/` |
| 业务页面 | `src/views/<domain>/` |

### 4.2 什么不该抽公共组件

- 只有两处用到、且两处参数差异大的组件
- 依赖具体业务字段的"万能表格"、"万能表单"
- 需要同时适配 Web 与 PDA 的组件（两端交互范式不同，抽象必然失败）

### 4.3 组件写法

- 一律 Composition API + `<script setup lang="ts">`
- 样式只用令牌变量，不写色值
- 组件内不直接发起请求，数据由页面注入

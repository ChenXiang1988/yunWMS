# PC 后台 · DESIGN.md

> 适用范围：`proto-next/pc/` 下的所有页面与样式。
> 取值权威：`proto-next/shared/ui/baseline-tokens.css.mjs`（实际生效）+ `tokens.css`（`--wds-*` 全量）。
> 规范上限：**本仓库无独立 PC 设计规范文档**（`docs/specs/` 不存在）。
> 唯一可执行的上限是 `shared/ui/` 的实现与本文件，以及校验脚本 `check-design.mjs`。

> **本文件不列页面清单与菜单项。** 菜单结构、页面数量和列名变化频繁，
> 写进来必然过期。要查当前有哪几页、每页几列，直接读 `pc/src/menu.mjs` 与 `pc/src/pages/`。

---

## 1. 两套令牌，作用域不同

PC 端同时存在两套令牌，**不要混用**：

| 家族 | 文件 | 用量 | 用于 |
| --- | --- | --- | --- |
| `--primary-*` / `--gray-*` / `--error-500` 等 | `baseline-tokens.css.mjs` | 源码引用 221 次 | **自绘组件**：`.status`、`.tag`、页签、分页、表头 |
| `--wds-*` | `tokens.css`（423 个） | 源码引用 105 次 | 布局骨架、间距、字号；以及经 `antd-wds-bridge.css` 供给 antd 组件 |

判据：**手写样式用 baseline，antd 组件用 `--wds-*`**。

## 2. 机器可读令牌

```yaml
name: 西之月 WMS · PC 后台
version: v1
colors:
  primary: "#6424fe"        # 品牌紫，主操作
  primaryHover: "#8a5cff"
  primaryActive: "#4f1bcc"
  error: "#e5484d"
  success: "#1f9d65"
  warning: "#d97706"
  textPrimary: "#171717"
  textSecondary: "#687386"
typography:
  base: 13px          # 表格与正文主力字号（非令牌，硬编码，见第 5 节）
layout:
  topbar: 54px
  sidebar: 188px
  sidebarCollapsed: 64px
rounded:
  sm: 6px
  md: 6px
  lg: 8px
  pill: 999px
```

## 3. 颜色（全表）

### 品牌主色

| 令牌 | 值 |
| --- | --- |
| `--primary-50` | `#f3efff` |
| `--primary-100` | `#ece4ff` |
| `--primary-200` | `#d9caff` |
| `--primary-400` | `#8a5cff` |
| `--primary-500` | `#6424fe` |
| `--primary-600` | `#6424fe` |
| `--primary-700` | `#4f1bcc` |

### 灰阶

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `--gray-0` | `#ffffff` | 卡片底 |
| `--gray-50` | `#f5f5f5` | 页面底 |
| `--gray-100` | `#f1f2f4` | 次级底色 |
| `--gray-200` | `#e5e5e5` | 边框 |
| `--gray-300` | `#d1d5db` | 强边框 |
| `--gray-400` | `#8a8f98` | 禁用文字 |
| `--gray-500` | `#687386` | 次要文字 |
| `--gray-600` | `#545b68` | 标签 |
| `--gray-700` | `#414753` | 控件文字 |
| `--gray-800` | `#30343b` | 正文强调 |
| `--gray-900` | `#171717` | 标题、正文 |

### 语义色

| 令牌 | 值 | 浅底 | 用途 |
| --- | --- | --- | --- |
| `--error-500` | `#e5484d` | `--danger-soft #fff0f0` | 失败、异常、危险操作 |
| `--success-500` | `#1f9d65` | `--success-soft #eaf8f1` | 成功、正常、在线 |
| `--warning-500` | `#d97706` | `--warning-soft #fff7e6` | 警告、待处理 |
| `--disabled-soft` | — | `#f1f2f4` | 禁用态底 |

### 行状态底

| 令牌 | 值 |
| --- | --- |
| `--surface-sunken` | `#fafafa` |
| `--row-hover` | `#fcfbff` |
| `--row-selected` | `#f7f3ff` |

**注意**：`tokens.css` 里另有 `--wds-color-error` = `#ff4d4f`、`--wds-color-success` = `#52c41a`，
那是 AntD 桥接专用值，**不要拿来画自绘状态胶囊**。裁决见根 `DESIGN.md` 第 3 节。

## 4. 布局与圆角

| 项 | 值 | 令牌 |
| --- | --- | --- |
| 顶栏高度 | 54px 固定 | `--topbar-height` |
| 侧栏宽度 | 188px 固定 | `--sidebar-width` |
| 侧栏收起 | 64px | `--sidebar-collapsed-width` |
| 圆角小/中 | 6px | `--radius-sm` / `--radius-md` |
| 圆角大 | 8px | `--radius-lg` |
| 胶囊 | 999px | `--radius-pill` |

**圆角允许集只有 `{6, 6, 8, 999}`**。已知越界：`l3.mjs` 进度条用了 `3px`（2 处）、
`l4.mjs` 一处 `3px 3px 0 0`。属历史遗留，不要求立刻改，但**不要新增**。

## 5. 字号与间距：没有令牌，靠基线

PC 端**没有字号令牌、也没有间距令牌**（`baseline-tokens.css.mjs` 里 font / space 相关令牌数为 0，
`specs/01` 也无字号章节）。实际取值直接写在样式里：

- 实际字号分布：12px（17 处）、**13px（26 处，主力）**、14px、15px、16px、18px、20px、22px、11px
- 间距走 `--wds-layout-gap`（7 处）、`--wds-card-padding`（4 处）、`--wds-toolbar-gap`（3 处）等少数几个

写新样式时：**字号照抄邻居，不要自创档位**；间距优先用 `--wds-layout-gap*` / `--wds-panel-padding`。
`--wds-size-font-xs`（12px）等字号令牌可以用于新代码，但不要为了统一去批量改存量硬编码。

## 6. 页面骨架：只有一套 `ListPage`

列表页一律用 `shared/ui/l3.mjs` 的 `ListPage`，骨架固定：

```
page-card › statusTabs › queryPanel › actionBar › table-wrap/dl › pagerBar
```

**不加**：

- 统计卡片（`StatRow`）—— 基线列表页没有。计数放 `statusTabs` 的 count 或 `actionBar` 的 note。
- 页内大标题（`PageHeader`）—— 三级页签已表明当前页。
- 自绘筛选栏 —— 用 `filters` 传，不写 `<div class="xxx-filter">`。

**不拼 HTML**：页面模块只写 `columns` / `rows` / `filters` / `toolbar`，结构交给 L3 组件。
客户端脚本落 `pc/src/shell/*.js`，由 `build.mjs` 读入内联。

**分页必须真能切页**：`ListPage` 与 `BaseTable` 都支持 `pager` / `pageSize`（默认 20） / `unit`。
全量 `<tr>` 带 `data-row-idx` 写入产物，脚本（`shell/pager.js`）只做显隐。

## 7. 布局硬约束

- 顶栏 54px 固定；侧栏 188px（收起 64px）固定，主区 `margin-left` 跟随。
- **页头三级页签钉住**，不随列表滚动。实现：`.app-shell` 用
  `height:calc(100vh - var(--topbar-height))`（不是 `min-height`），
  `.main` 与 `.page-body` 补 `min-height:0`，使 `.page-body` 成为唯一滚动容器。
- **不放面包屑。**
- **抽屉与弹层必须挂 `body`**，不得写在 `.page` 内——`.page{display:none}` 会连带隐藏，
  切页后按钮点了没反应。这个 bug 已发生过（A.11.9.122）。

## 8. 组件

| 组件 | 出处 | 令牌家族 | 说明 |
| --- | --- | --- | --- |
| `ListPage` | `l3.mjs` | 混合 | 列表页唯一骨架 |
| `BaseTable` | `l3.mjs` | `--wds-*` + baseline | 表格 |
| `StatusTabs` | `l3.mjs` | baseline | 状态页签，可选 `viewSwitch` 视图切换 |
| `QueryPanel` | `l3.mjs` | baseline | 筛选面板，`type`: text/select/date/daterange |
| `ActionBar` | `l3.mjs` | baseline | 操作条 |
| `PagerBar` | `l3.mjs` | baseline | 分页条 |
| `Tag` | `l3.mjs` | `--wds-component-tag-*` | 通用标签，色型 default/success/warning/danger/info |
| `StatusTag` | `l3.mjs` | baseline | 状态胶囊 |
| `AppLayout` / `Page` | `l4.mjs` | baseline | 外壳与页面容器 |

### 已知缺口：`StatusTag` 有 3 个色型未定义

`l3.mjs` 只定义了 `.status.pending / .enabled / .disabled / .rejected / .info`，
但页面在传 `ok` / `out` / `low`：

| 传入 | 想要 | 实际 |
| --- | --- | --- |
| `ok` | 成功绿 | 落基类灰底灰字 |
| `out` | 危险红 | 落基类灰底灰字 |
| `low` | 警告橙 | 落基类灰底灰字 |

产物中 `status ok` 出现数百处。写新代码时若要表达成功/危险/警告，
**暂时改用 `enabled` / `rejected` / `pending`**，或等该缺口补齐。
`node docs/proto-next/check-design.mjs` 会持续报告这 3 个缺口。

## 9. Do / Don't

✅ 菜单名、层级、列名、页签文案、按钮文案逐字取自基线，不发明。
✅ 状态用语义色 + 文字，不只靠颜色。
✅ 数字列右对齐 + `tabular-nums`。
✅ 一个区域最多 1 个主按钮，危险操作二次确认。

❌ 不加 `StatRow` / `PageHeader` / 自绘筛选栏。
❌ 不在页面模块里拼接大段 HTML 字符串。
❌ 不手改 `dist/pc.html`。
❌ 不用 `#000` 作正文色（用 `--gray-900 #171717`）。
❌ 不引 CDN / 网络字体 / 图片外链。
❌ 不读 `proto-next/app/` 目录（构建期 `assertNoPeerReads()` 会抛错）。

## 10. 构建

```bash
cd docs/proto-next/pc && node src/build.mjs          # 构建
cd docs/proto-next/pc && node src/build.mjs --check  # 只校验产物漂移
```

改 `docs/proto-next/shared/` 下的文件后，`docs/proto-next/app` 也要重新构建并校验。

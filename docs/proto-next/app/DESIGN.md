# APP 手持端 · DESIGN.md

> 适用范围：`app/` 下的所有页面与样式。
> 取值权威：`pda-ui-tokens.json`（结构化令牌，52 项）+ `app-shell.mjs` 的 `:root` 块。
> 规范上限：`specs/02-APP端设计规范.md`。冲突时以规范为准，本文件只做索引。

---

## 0. 这条最重要

APP 与 PC **不同源**。APP 是绿系（`#018D71`），PC 是紫系（`#6424fe`）。
不要因为「统一视觉」把两边颜色改成一样——手持端在仓库强光下作业，走另一套对比度与触控标准。

## 1. 机器可读令牌

```yaml
name: 西之月 WMS · APP 手持端
version: v1
colors:
  primary: "#018D71"          # 品牌绿
  actionPrimary: "#007D64"    # 主操作（比品牌绿深，用于按钮填充）
  brandSoft: "#E6F4EF"
  page: "#F2F2F2"
  surface: "#FFFFFF"
  textPrimary: "#333333"
  textSecondary: "#666666"
  textMuted: "#999999"
  danger: "#DD524D"
  border: "#DDDDDD"
typography:
  caption: 12px
  body: 14px
spacing:
  micro: 4px
  small: 8px
  content: 12px
  page: 16px
  large: 24px
rounded:
  operational: 0px    # 业务页，无阴影
  button: 7px
  contextual: 8px
  dialog: 12px
layout:
  viewport: 375px
  touchTarget: 44px
  actionBarMinHeight: 56px
```

## 2. 颜色（全表）

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `--pda-color-brand` | `#018D71` | 品牌色，标题强调 |
| `--pda-color-action-primary` | `#007D64` | 主操作按钮填充 |
| `--pda-color-brand-soft` | `#E6F4EF` | 品牌浅底，选中区 |
| `--pda-color-page` | `#F2F2F2` | 页面底色 |
| `--pda-color-surface` | `#FFFFFF` | 卡片、列表行 |
| `--pda-color-text-primary` | `#333333` | 正文 |
| `--pda-color-text-secondary` | `#666666` | 副标题、辅助 |
| `--pda-color-text-muted` | `#999999` | 占位、禁用说明 |
| `--pda-color-text-inverse` | `#FFFFFF` | 深色底上的文字 |
| `--pda-color-divider` | `#F2F2F2` | 分隔线 |
| `--pda-color-border` | `#DDDDDD` | 边框 |
| `--pda-color-danger` | `#DD524D` | 危险操作、异常 |
| `--pda-color-danger-text` | `#B42318` | 危险文字（浅底上）※ 未实现 |
| `--pda-color-danger-soft` | `#FEF3F2` | 危险浅底 |
| `--pda-color-warning-soft` | `#FFF7ED` | 警告浅底 |
| `--pda-color-warning-text` | `#B45309` | 警告文字 |
| `--pda-color-mask` | `rgba(0,0,0,.40)` | 遮罩 |

**禁止**：暗底上用纯白正文；用 `#000` 作正文；自造品牌绿色阶。

## 3. 字号

实际只有两个字号令牌落地，其余值直接写在样式里：

| 层级 | 值 | 令牌 | 用途 |
| --- | --- | --- | --- |
| caption | 12px | `--pda-font-caption` | 辅助说明、次要标签 |
| body | 14px | `--pda-font-body` | 正文 |

规范允许的字号集：`{12, 14, 15, 16, 17, 18, 36}`。源码实际用到 12/14/15/16/18/36。
**不要新增 13px、11px 这类中间档。**

## 4. 间距与触控

间距令牌 5 档，已成体系：

| 令牌 | 值 |
| --- | --- |
| `--pda-space-micro` | 4px |
| `--pda-space-small` | 8px |
| `--pda-space-content` | 12px |
| `--pda-space-page` | 16px |
| `--pda-space-large` | 24px |

触控硬下限：

- 最小触控目标 **44px**（`--pda-size-minimum-touch-target`）
- 按钮 / 输入最小高 **44px**
- 底部操作栏最小高 **56px**（`--pda-layout-action-bar-min-height`）

## 5. 圆角

| 令牌 | 值 | 用途 |
| --- | --- | --- |
| `--pda-radius-operational` | `0px` | 业务页容器，无阴影 |
| `--pda-radius-button` | `7px` | 按钮 |
| `--pda-radius-contextual` | `8px` | 卡片（带阴影） |
| `--pda-radius-dialog` | `12px` | 弹窗、底部面板 |

允许集只有 `{0, 7, 8, 12}`。源码里还散落着 `50%`（圆形头像/图标）、`2px`（细条）、
`10px`（机身按键区），属机身装饰件，不用于业务内容。

## 6. 机身与舞台（这个端特有）

APP 产物是「浏览器里的一台模拟 PDA」，这层外壳有独立规则：

| 部件 | 尺寸 | 说明 |
| --- | --- | --- |
| 屏幕 | 375 × 720 | `--pda-device-screen-w/h`，设计基准 |
| 机身圆角 | 30px | `--pda-device-radius` |
| 状态栏 | 28px | 顶部 |
| 导航栏 | 最小 48px | 标题 + 返回 |
| 操作栏 | 56px | 底部常驻 |
| 按键区 | 92px | `--pda-device-keypad-h`，窄屏降到 80px |
| 机身外壳 | `#2b2f33` | `--pda-device-shell` |
| 扫描键 | `#f5b301` | `--pda-device-scan-key`，唯一黄色 |

**舞台显隐是硬约束**：`.app-stage` 默认 `display:none`，靠路由显示当前一屏：

```css
.app-stage{display:none}
.app-stage:not([hidden]){display:flex; ...}
```

必须显式写这两条。原因是 `display:flex` 会压过浏览器默认的 `[hidden]{display:none}`，
漏写会变成「所有屏一起渲染成长列表」——这个 bug 已发生过一次。

## 7. 适配

- 设计基准 375px，适配用 flex / grid / 百分比 / media query。
- **禁止 `rpx`**，禁止整体随视口缩放字号与触控目标。
- 窄屏（≤430px）机身等比收窄，`375` 基准不变形。

## 8. 已知缺口（写成清单，别当不存在）

`pda-ui-tokens.json` 定义了 52 个令牌，`app-shell.mjs` 只实现了 24 个。以下 28 项**尚未落地**：

| 类别 | 缺失令牌 |
| --- | --- |
| 颜色 | `--pda-color-danger-text`（`#B42318`） |
| 字号 | `--pda-font-item-title`(15) `--pda-font-section-title`(16) `--pda-font-key-value`(17) `--pda-font-dialog-title`(18) `--pda-font-dialog-body`(16) `--pda-font-metric`(36) |
| 字重/行高 | `--pda-font-weight-regular`(400) `--pda-font-weight-strong`(700) `--pda-font-line-height-body`(1.5) |
| 圆角 | `--pda-radius-operational`(0) `--pda-radius-button`(7) `--pda-radius-contextual`(8) `--pda-radius-dialog`(12) |
| 尺寸 | `--pda-border-divider-width` `--pda-size-button-min-height` `--pda-size-input-min-height` `--pda-size-minimum-touch-target` `--pda-size-sku-image` `--pda-size-back-icon` `--pda-size-home-icon` `--pda-size-success-icon` |
| 布局 | `--pda-layout-selection-sheet-max-height`(70vh) |
| 阴影 | `--pda-shadow-contextual` |
| 层级 | `--pda-z-index-action-bar`(10) `--pda-z-index-mask`(1000) `--pda-z-index-overlay`(1010) `--pda-z-index-feedback`(1100) |

现状：这些值以裸数字直接写在样式里（例如 `font-size:15px`、`border-radius:8px`）。
写新样式时**优先按上表用令牌名**；已有硬编码不要求立刻改，但不要新增。

`node tools/check-design.mjs` 会把这份缺口清单打印出来。

## 9. Do / Don't

✅ 主操作放底部操作栏，单手拇指可达。
✅ 扫描优先：能扫码就不让人打字。
✅ 一屏一件事，不堆多个主操作。
✅ 状态用语义色 + 文字，不只靠颜色区分。

❌ 不要把 PC 的密集表格搬进 APP。
❌ 不要暗底纯白正文。
❌ 不要触控目标小于 44px。
❌ 不要 `rpx`。
❌ 不要为了「与 PC 统一」改掉绿系主色。
❌ 不要漏写 `.app-stage` 的 `display:none`。

## 10. 构建

```bash
cd app && node src/build.mjs          # 构建
cd app && node src/build.mjs --check  # 只校验产物漂移
```

# @yunwms/tokens

yunWMS 设计令牌。一套基础 + 两套密度主题，货主端与仓网管理端共用。

## 结构

分三层，**组件只允许使用第二层和第三层**。

| 层 | 内容 | 前缀 | 是否可直接用 |
| --- | --- | --- | --- |
| Primitive | 原始色值、字号、间距、阴影、层级、动效 | `--blue-500`、`--space-4` | 否 |
| Semantic | 语义角色：主色、文字、背景、边框、状态 | `--color-primary`、`--color-text-secondary` | 是 |
| Component | 密度、控件尺寸、表格行高、侧栏配色 | `--table-row-height`、`--sidebar-bg` | 是 |

直接写 `--blue-500` 的问题：换主题时这一处不跟随。必须写 `--color-primary`。

## 两端差异

两端不拆两套令牌，用密度切换。

| 令牌 | `compact`（仓网端） | `comfortable`（货主端） |
| --- | --- | --- |
| `--font-size-base` | 13px | 14px |
| `--control-height-md` | 28px | 36px |
| `--table-row-height` | 36px | 48px |
| `--table-cell-px` | 8px | 16px |
| `--page-padding` | 16px | 24px |
| `--card-padding` | 12px | 20px |
| `--form-item-gap` | 12px | 20px |
| `--radius-md` | 4px | 6px |
| `--header-height` | 48px | 56px |
| `--sidebar-width` | 200px | 220px |
| `--content-max-width` | 不限 | 1440px |
| `--sidebar-bg` | 深色 `--gray-900` | 浅色 `--gray-0` |

默认值是 `comfortable`。仓网端在根节点加属性即可：

```html
<html data-density="compact">
```

局部区域需要不同密度时，加在容器上即可，令牌按 CSS 变量正常继承。

## 使用

```ts
// main.ts
import '@yunwms/tokens'                 // tokens.css
import '@yunwms/tokens/element-plus'    // Element Plus 变量映射
import 'element-plus/dist/index.css'    // 顺序：tokens 在 EP 样式之前或之后均可
```

引入顺序建议：`element-plus/dist/index.css` → `@yunwms/tokens` → `@yunwms/tokens/element-plus`。

Element Plus 的 CSS 变量会被 v0.1 这张映射表覆盖，组件源码不需要改。

## 命名规则

```
--{类别}-{语义}-{状态}

--color-primary            主色
--color-primary-hover      主色悬停
--color-text-secondary     次级文字
--color-bg-container       容器背景
--color-border-strong      强边框
--table-row-height         表格行高
```

状态后缀统一用 `hover` / `active` / `disabled` / `bg` / `border`，不用 `light`、`dark`。

## 禁止事项

1. 组件里不写十六进制色值，一律用语义令牌。
2. 组件里不写 `padding: 13px` 这类裸数值，间距用 `--space-*`，其余用组件令牌。
3. 不直接用 Primitive 层（`--blue-500`、`--gray-200`）。
4. 不为两端各写一套组件。差异走密度令牌，组件只有一份。

## 扩展

暗色模式预留了结构：新增 `[data-theme="dark"]` 覆盖 Semantic 层即可，Primitive 不动。首期不做。

新令牌的准入条件：至少两个页面会用到，且语义明确。只在一处用的样式不要提令牌。

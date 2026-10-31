# APP 端（手持）设计规范

> 适用范围：所有手持端原型（`proto-next/app/`）与手持汇报 / 演示 HTML。
> 资料来源：`../pda-ui-tokens.json`（结构化令牌 v2.0.0，唯一权威来源）+ `../pda-原型骨架.html`（视觉基线）。

## 1. 设计令牌（节选自 pda-ui-tokens.json）

### 颜色
- 品牌 `--pda-color-brand #018D71` / 主操作 `--pda-color-action-primary #007D64` / 品牌浅 `--pda-color-brand-soft #E6F4EF`
- 页底 `--pda-color-page #F2F2F2` / 表面 `--pda-color-surface #FFFFFF`
- 文字 primary `#333` / secondary `#666` / muted `#999` / inverse `#FFF`
- 分隔 `--pda-color-divider #F2F2F2` / 边框 `--pda-color-border #DDDDDD`
- 危险 `--pda-color-danger #DD524D`（文字 `#B42318`，浅底 `#FEF3F2`）
- 警告文字 `#B45309`（浅底 `#FFF7ED`）/ 遮罩 `rgba(0,0,0,.40)`

### 字体（参考视口 375px，rpx/2 换算）
- caption 12 / body 14 / item-title 15 / section-title 16 / key-value 17 / dialog-title 18 / dialog-body 16 / metric 36
- 字重 regular 400 / strong 700；行高 body 1.5

### 间距
- micro 4 / small 8 / content 12 / page 16 / large 24

### 圆角
- operational 0（业务页，无阴影）/ contextual 8（卡片，带阴影）/ button 7 / dialog 12

### 尺寸 / 手势
- 按钮 / 输入最小高度 44px；最小触控目标 44px（含图标动作）
- 图标：返回 20 / 首页 31 / 成功 40；SKU 图 80

### 布局
- action bar 最小高 56px（含底部安全区）；选择面板最大高 70vh

## 2. 坐标与适配
- 设计基准 375px；rpx = px×2；适配用 flex / grid / 百分比 / media query，不得整体随视口缩放字号与触控目标。
- 生成 CSS 不得含 `rpx`。

## 3. 结构约定（页面模板）
- 导航 → 业务上下文 → 主操作区 → 对象摘要 → 数量与进度 → 操作（完整 pageTemplates 见令牌文件）。
- 单一主操作 / 区域；底部常驻操作最多 2 个。
- 扫描 / 确认：仅一条 DOM Enter / 提交 / 显式确认路径。

## 4. 机器检查项（部分已实现）
- [ ] 交付 HTML 颜色须为令牌 `--pda-color-*` 子集，无其它硬编码色
- [ ] 字号仅限 `{12,14,15,16,17,18,36}`；间距仅限 `{4,8,12,16,24}`；圆角仅限 `{0,7,8,12}`
- [ ] 按钮 / 输入 / 可点目标最小 44px
- [ ] CSS 不得含 `rpx`
- [ ] 不得外链 CDN / 网络字体
- [ ] 主操作每区域 ≤1，底部常驻 ≤2
- [x] **已实现**：不同模块不得定义同名 CSS 类，构建期由 `shared/verify-class-names.mjs` 拦截
- [ ] 多屏舞台须只显一屏：`.app-stage` 默认 `display:none`，仅 `:not([hidden])` 时布局；
      不得用作者 `display` 覆盖 `[hidden]`，否则全部舞台会渲染成长列表（见 A.11.9.122）

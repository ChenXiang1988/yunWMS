# 西之月 WMS · DESIGN.md（设计简报 · 仓库级）

> 用途：给 AI 编码代理读的**视觉约束索引**。本文件只管跨端硬约束与「真值在哪」，
> 不重复任何具体色值——取值一律以代码为准，见第 2 节真值路由表。
>
> 与 `AGENTS.md` 分工：`AGENTS.md` 说「在这个仓库怎么干活」，本文件说「这个产品该长什么样」。
> 端内细则见 `pc/DESIGN.md` 与 `app/DESIGN.md`。

---

## 1. 这个产品是什么

「西之月 WMS」仓储库存模块原型。交付物是**零依赖、双击即开的单文件 HTML**，两种形态：

| 端 | 源目录 | 产物 | 形态 |
| --- | --- | --- | --- |
| PC 后台 | `pc/` | `dist/pc.html` | 桌面中后台，紫系（`#6424fe`） |
| APP 手持 | `app/` | `dist/app.html` | 375 视口 PDA 模拟机身，绿系（`#018D71`） |

两端**故意不同源**：颜色体系、字号阶梯、圆角集、触控尺寸全部各一套。这不是不一致，
是两种作业场景的物理差异——后台看表格密度，手持看单手触控与强光可读性。

## 2. 真值分层（最重要的一节）

本工作区同时存在四层信息，**权限严格不同**：

| 层 | 文件 | 权限 | 说明 |
| --- | --- | --- | --- |
| 规范层 | `specs/01-PC端设计规范.md`、`02-APP端设计规范.md`、`03-汇报材料设计规范.md` | 权威 | 人工裁决上限，冲突时以它为准 |
| 实现层（PC） | `shared/ui/baseline-tokens.css.mjs` + `tokens.css` | 取值权威 | PC 实际生效的令牌；源码引用 `--primary-*`/`--gray-*` 共 221 次，`--wds-*` 105 次 |
| 实现层（APP） | `pda-ui-tokens.json`（52 项）+ `app/src/app-shell.mjs` 的 `:root` | 取值权威 | json 是结构源，CSS 是实现 |
| 索引层 | **本文件**、`pc/DESIGN.md`、`app/DESIGN.md` | 只许引用 | **不得新增任何取值** |

**硬规则**：索引层出现的每一个 hex 值，都必须能在实现层文件里原样找到。
这条由 `node tools/check-design.mjs` 强制，不通过即退出码 1。

这样做的原因：本工作区已经有过一次「文档写一套、代码跑另一套」——
`specs/01` 只列 43 行摘要，而 `tokens.css` 有 573 行 423 个令牌，两者语义色取值还不一致。
再复制一份取值进来，只会腐烂得更快。

## 3. 语义色裁决（已定，勿再议）

同一个「错误红」在本工作区有两个值，作用域如下：

| 用途 | 取值 | 出处 | 作用域 |
| --- | --- | --- | --- |
| 业务语义红/绿 | error `#e5484d` / success `#1f9d65` / warning `#d97706` | `specs/01` + `baseline-tokens.css.mjs` | **所有自绘组件**（`.status`、`.tag`、表格状态列、汇报材料） |
| AntD 组件内部红/绿 | error `#ff4d4f` / success `#52c41a` | `tokens.css` 的 `--wds-color-*` | 仅经 `antd-wds-bridge.css` 映射给 antd 组件 |

判据：**手写 HTML/CSS 用第一行，antd 组件内部用它自己的**。不要拿 `#ff4d4f` 去画自绘状态胶囊。

## 4. 全仓禁止项

- ❌ 不引 CDN、不引网络字体、不引图片外链、不装依赖。产物必须完全自包含。
  （演示数据里的 URL 字符串不算资源外链。）
- ❌ 不手改 `dist/*.html`。改源，产物按 `node src/build.mjs` 重建。
- ❌ 不新增令牌档位。字号、间距、圆角只取实现层已有的值。
- ❌ 不让一端读另一端的目录。两端的 `build.mjs` 都有 `assertNoPeerReads()`，读到 `pc`（或从 PC 读到 `app`）立即抛错。
- ❌ 不用纯黑 `#000` 作正文色（PC 用 `--gray-900 #171717`，APP 用 `#333333`）。
- ❌ APP 端不使用 `rpx`；不使用小于 44px 的触控目标。

## 5. 构建与校验

```bash
# 构建（改完源必须重建，产物不入库）
cd pc  && node src/build.mjs
cd app && node src/build.mjs

# 只校验不写：产物漂移检查（不是并发锁）
cd pc  && node src/build.mjs --check
cd app && node src/build.mjs --check

# 设计简报校验：索引层取值是否都能在实现层找到
node tools/check-design.mjs
```

**开工前必须记 hash**：本工作区可能多会话并行。先跑两端 `--check` 记下 sha256，
若失败或 hash 与上次不同，先停下确认是否有其他会话在改，不要直接动手。

## 6. 汇报材料路由

`reports/` 下的汇报 HTML 不是原型，样式手写、不走构建，但令牌必须复用：

- PC 汇报 → 用 `01-PC端设计规范.md` 的令牌（紫系）
- 手持汇报 → 用 `02-APP端设计规范.md` 的令牌（绿系）
- 零外链、结论先行、图表用内联 SVG

写汇报材料前先跑 `node tools/preflight.mjs`，写完跑 `node tools/check-report-copy.mjs`。

## 7. 端内细则去哪看

| 我要做 | 读这个 |
| --- | --- |
| PC 页面 / 列表 / 表格 / 抽屉 | `pc/DESIGN.md` |
| APP 页面 / 作业流 / 触控 | `app/DESIGN.md` |
| PC 页面骨架与菜单纪律 | `specs/05-PC页面构筑约定.md` |
| 环境论断（不许凭印象断言环境） | `specs/04-环境论断核查约定.md` |

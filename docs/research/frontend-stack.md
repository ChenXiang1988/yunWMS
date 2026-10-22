# 前端技术栈调研：Web 端与 PDA 端 UI 库选型

- 调研时间：2026-10-22
- 状态：结论已确认，待进入实现
- 关联 change：`migrate-owner-portal`、`apps-pda`、`add-inline-menu`

---

## 1. R1 需求梳理

### 1.1 功能范围

| 端 | 应用 | 特征 |
| --- | --- | --- |
| 仓网端 | `apps/network-console` | 高密度表格、多级菜单、`compact` 密度 + 深色侧栏 |
| 货主端 | `apps/owner-portal` | 中等密度、表单为主、`comfortable` 密度 + 浅色侧栏 |
| PDA | `apps/pda`（待建） | 触摸作业、全屏流程、无侧栏 |

### 1.2 关键特征（已确认的硬约束）

| 项 | 结论 | 来源 |
| --- | --- | --- |
| PDA 设备基线 | 优博讯 DT40，4 英寸 480×800，dpr 1.5 → **320×533 CSS px** | 官网参数 + Android density 推导 |
| PDA 硬件 | RAM **2GB** 起，八核，IP67 | 同上 |
| 作业方式 | **戴手套**，点按区 ≥ **56px** | 用户确认 |
| 扫描 | 内置扫描头，键盘模拟（HID），不接外接扫描枪 | 用户确认 |
| 令牌结构 | primitive / semantic / density / shell 四层已落地，三轴（theme / density / platform） | 本仓库实现 |
| 令牌共享 | Web 与 PDA 共享 primitive，语义名同名、取值不同 | 用户确认 |
| 菜单 | 后端树驱动，key = 路由 path | 用户确认 |

### 1.3 边界与非目标

- 本期不做六大业务域页面（入库/出库/库内/承运/计费/基础资料），一期沿用现有 5 个页面
- 单仓端形态未定（多集群部署），本次不纳入
- 不做"三端共用一套组件"的抽象
- 不含设备协议对接（`wms-integration` 范畴）

### 1.4 现存约束

- Vue 3.5 + Vite 6 + TypeScript 5.7，pnpm workspace
- 现有 UI 库 Element Plus 2.14.7，通过 CSS 变量桥接令牌
- 共享包 `packages/{tokens,request,ui}`，其中 `request` 已去除 UI 库依赖
- 零商业授权预算，禁止 GPL / AGPL / SSPL

---

## 2. R2 检索

检索时间 2026-10-22，数据来源 GitHub REST API 与 npm registry，检索词包括：
`vue3 mobile ui library`、`vue3 admin template`、`design tokens build`、`vant vue3`、`nutui vue3`。

三层覆盖：

| 层次 | 处理 |
| --- | --- |
| 完整同类系统（WMS） | 已由 `wms-open-source-landscape.md`（同日）覆盖，本次引用不重复 |
| 单域可复用模块（后台骨架、菜单、权限） | 本次检索，见 §2.1 |
| 可直接嵌入的组件/库 | 本次检索，见 §2.2、§2.3 |

### 2.1 后台模板（仅借鉴，不引入）

| 仓库 | Star | 许可证 | 最近推送 | 备注 |
| --- | --- | --- | --- | --- |
| [vbenjs/vue-vben-admin](https://github.com/vbenjs/vue-vben-admin) | 33651 | MIT | 2026-10-22 | 5.x 基于 ant-design-vue，菜单与权限实现可参考 |
| [soybeanjs/soybean-admin](https://github.com/soybeanjs/soybean-admin) | 15149 | MIT | 2026-10-21 | 基于 Naive UI，路由元信息驱动菜单，可参考思路 |
| [kailong321200875/vue-element-plus-admin](https://github.com/kailong321200875/vue-element-plus-admin) | 3698 | MIT | 2026-09-01 | 近期推送放缓（约 51 天） |

### 2.2 UI 组件库

| 仓库 | Star | 许可证 | 最近推送 | 目标端 |
| --- | --- | --- | --- | --- |
| [element-plus/element-plus](https://github.com/element-plus/element-plus) | 27825 | MIT | 2026-10-22 | Web（现状） |
| [vueComponent/ant-design-vue](https://github.com/vueComponent/ant-design-vue) | 21685 | MIT（GitHub 标 NOASSERTION，已核实 LICENSE 文件首行为 MIT） | 2026-10-13 | Web（目标） |
| [youzan/vant](https://github.com/youzan/vant) | 24395 | MIT | 2026-10-22 | PDA |
| [jd-opensource/nutui](https://github.com/jd-opensource/nutui) | 6511 | MIT | **2026-04-02** | PDA |
| [varletjs/varlet](https://github.com/varletjs/varlet) | 5337 | MIT | 2026-10-21 | PDA |
| [Tencent/tdesign-mobile-vue](https://github.com/Tencent/tdesign-mobile-vue) | 437 | MIT | 2026-10-21 | PDA |
| [ant-design/ant-design-mobile](https://github.com/ant-design/ant-design-mobile) | 12076 | MIT | 2026-09-14 | 仅参考（React） |

### 2.3 令牌工具

| 仓库 | Star | 许可证 | 最近推送 | 备注 |
| --- | --- | --- | --- | --- |
| [amzn/style-dictionary](https://github.com/amzn/style-dictionary) | 4868 | Apache-2.0 | 2026-10-20 | 一份源生成多端产物 |
| [argyleink/open-props](https://github.com/argyleink/open-props) | 5551 | MIT | 2026-10-11 | 现成尺寸/缓动梯度 |
| [radix-ui/colors](https://github.com/radix-ui/colors) | 1716 | MIT | 2025-12-17 | 12 阶色板，近 10 个月无推送（色板类项目低频变更） |

---

## 3. R3 比较

### 3.1 Web 端 UI 库

| 维度 | Element Plus 2.14.7（现状） | Ant Design Vue 4.x（目标） |
| --- | --- | --- |
| 功能覆盖 | 表格、表单、树、菜单齐备 | 齐备，组件语义更贴近中后台 |
| 活跃度 | 2026-10-22 推送，日更 | 2026-10-13 推送，周更量级 |
| 许可证 | MIT | MIT（LICENSE 文件已核实） |
| 令牌注入 | **CSS 变量**（`--el-*`），纯 CSS 可覆盖 | **仅 Design Token + cssinjs（JS 对象）**，4.0 起移除 CSS 变量方案 |
| 与现有令牌兼容 | 已打通，四层结构直接映射 | 需新增 TS 真源与生成链路 |
| 移动端能力 | 无 | **无**（Ant Design 的移动端是 antd-mobile，仅 React） |
| 二次开发成本 | 零（已在用） | 中：14 个文件迁移 + 令牌改造 |

**关键技术事实**：ant-design-vue 4.0 迁移文档明确「弃用 less，采用 CSS-in-JS」「产物中不再包含 css 文件」「移除 CSS 变量及其之上的动态主题方案」。因此 Web 端令牌无法再走"CSS 变量单一真源"，必须提供 JS 侧令牌源。

⚠️ **未验证**：ant-design-vue 4 是否提供后续可选的 `theme.cssVar` 模式。若提供，令牌可维持 CSS 单一真源；若不提供，须新增 `primitives.ts` + 生成脚本。该验证安排在 `migrate-owner-portal` 内完成。

### 3.2 PDA 端 UI 库

| 维度 | Vant 4（4.10.2） | NutUI 4 | Varlet | TDesign Mobile Vue | 自建最小集 |
| --- | --- | --- | --- | --- | --- |
| 功能覆盖 | 移动端组件最全，含 NumberKeyboard、Picker、虚拟列表 | 全 | 全 | 中 | 需自写 8–10 个 |
| 活跃度 | 日更 | **2026-04-02 后无推送（约 6.7 个月）** | 活跃 | 活跃但 star 437 | — |
| 许可证 | MIT | MIT | MIT | MIT | — |
| 令牌注入 | **CSS 变量 `--van-*`** + ConfigProvider `theme-vars`（分 dark/light） | CSS 变量 | CSS 变量 | CSS 变量 | 自定义 |
| Vue 3 兼容 | peer `vue ^3.0.0` | 支持 | 支持 | 支持 | — |
| 包体（2GB 设备预算） | 按需引入可接受 | 未验证 | 未验证 | 未验证 | 最小 |
| 二次开发成本 | 低 | 低 | 中（Material 风格需改造） | 中（生态最小，遇问题难查） | 高（无障碍、焦点、虚拟列表全自写） |

### 3.3 令牌工具

| 方案 | 结论依据 |
| --- | --- |
| style-dictionary | 能力强，但当前只需输出「一个 TS 对象 + 一个 CSS 文件」，20 行脚本即可，引入为时过早 |
| open-props / radix colors | 色阶质量高，但本项目的功能色已按 UI 库 light-N 档位手工补齐（每色 4 档），直接引入会带来风格漂移与依赖负担 |

---

## 4. R4 结论

| 决策项 | 结论 | 理由 | 被否候选与落选原因 |
| --- | --- | --- | --- |
| Web 端 UI 库 | **基于现有项目改造**：迁移至 Ant Design Vue 4 | 产品侧明确要求，2026-10-22 决策 | 维持 Element Plus：技术代价最低（CSS 变量单源、已有实现），但不符合产品侧选型要求，故落选。**技术代价须如实记录**：令牌需新增 TS 真源与生成链路，且拿不到移动端能力 |
| PDA 端 UI 库 | **直接复用** Vant 4 | 唯一日更的 Vue 3 移动端库；CSS 变量驱动与令牌生成链路天然契合；NumberKeyboard / Picker / 虚拟列表覆盖扫码作业场景 | NutUI：6.7 个月无提交，活跃度风险不可接受；Varlet：Material 风格与后台视觉体系冲突，二次改造成本高；TDesign Mobile Vue：star 437，遇问题社区支持不足；自建：8–10 个组件的无障碍与焦点管理成本被低估 |
| 令牌实现 | **自行实现** 四层结构 + 生成脚本 | 三轴需求（theme / density / platform）与两个 UI 库的注入方式差异（JS 对象 vs CSS 变量）决定了没有现成方案可直接套用 | style-dictionary：当前产物只有两个，引入成本大于收益；radix colors / open-props：色阶已手工补齐，引入会带来风格漂移 |
| 后台模板 | **不引入**，仅借鉴 | 只缺菜单与权限两块实现，引用整模板会引入不需要的依赖与约定 | vue-vben-admin / soybean-admin / vue-element-plus-admin 均落选 |

---

## 5. R5 风险与待确认

### 5.1 风险

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| ant-design-vue 4 无 `theme.cssVar` | 令牌须改为 TS 真源 + 生成，Web 端丧失"CSS 单源" | `migrate-owner-portal` 内第一时间验证；失败即启用 TS 真源方案 |
| antd 静态 `message` 不消费 ConfigProvider 上下文 | 错误提示不跟令牌，违反"禁色值字面量"约束的延伸要求 | 用 `<a-app>` + `App.useApp()`，注入点放在 `<a-app>` 子组件 |
| Vant 在 320px 视口下的表单/弹层适配 | DT40 屏宽仅 320 CSS px，可能出现溢出 | `apps/pda` 首个页面（上架任务）即为验收探针 |
| 2GB 设备包体预算 | 首屏卡顿 | 按需引入，首屏 JS 预算 300KB gzip；不引入图表库 |
| NutUI 停更（若后续改选） | 无安全更新 | 已排除 |
| 单仓端形态未定 | 可能新增第四个端 | 令牌三轴已容纳；若是独立应用，复用 `packages/ui-web` 即可 |
| 许可证识别异常 | ant-design-vue 在 GitHub 显示 NOASSERTION | 已核实 LICENSE 文件首行为 MIT，登记时以文件原文为准 |

### 5.2 待确认

1. `apps/pda` 除上架任务外，一期还需要哪些页面（决定 Vant 组件清单）
2. DT40 实机 WebView 的 `devicePixelRatio` 是否确为 1.5（官网参数推导，建议开发时读一次）
3. 是否存在戴厚手套作业的场景（若是，56px 需上调至 64px）

---

## 6. R6 产物与引用

- 本文档：`docs/research/frontend-stack.md`
- 增量更新：`docs/research/wms-open-source-landscape.md` 的「6.3 技术栈」与「6.6 MVP 顺序」
- 引用方式：`migrate-owner-portal`、`apps-pda`、`add-inline-menu` 三个 change 的 `proposal.md` 均须引用本文档
- 有效期：6 个月内（至 2027-04-22）可直接引用；需求变化时增量更新

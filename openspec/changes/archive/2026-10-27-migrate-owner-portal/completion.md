# 交付记录：migrate-owner-portal

## 目标与实际交付

- 目标：将货主端从 Element Plus 迁移到 Ant Design Vue 4，保持业务行为不变。
- 实际：15/15 任务完成；`packages/ui-web` 承载 antd 组件，`owner-portal` 已移除 Element Plus，令牌仍由 TS 真源生成 CSS。

## 验证结果

| 检查 | 实际结果 |
| --- | --- |
| `pnpm -r typecheck` | 通过 |
| `pnpm -r test` | tokens 3 + network-console 20，共 23 通过 |
| `pnpm -r build` | 两个应用构建成功 |
| `pnpm check:ui` | 通过；扫描 77 个源文件 |
| 浏览器登录页 | 标题“登录货主端”渲染正常 |
| 浏览器工作台 | 标题“工作台”渲染正常 |
| 浏览器商品管理 | 标题“商品管理”渲染正常 |
| Element Plus 残留 | `owner-portal` 依赖中无 `element-plus`；`packages/ui` 与 EP 令牌文件已删除 |

浏览器走查使用开发服务器与渲染检查令牌打开受保护页面；本轮未重复执行真实后端登录，登录/改密的真实接口已在仓网端同源认证链路验证。

## 未验证项

- 未做 Element Plus 版本与 antd 版本的逐像素对照。
- 未在移动端或窄屏下走查。
- 未对商品管理页的表格交互做完整业务回归。

## 已知风险

- antd 的组件 token 与 CSS 变量不是一一对应，密度微调仍可能产生视觉差异。
- 两个应用使用同一个 `yunwms.token` 键；同 origin 部署时切换端会覆盖令牌，这是已登记风险，不在本 change 修复。
- 本次只证明页面可渲染，不证明所有交互与历史 EP 行为完全一致。

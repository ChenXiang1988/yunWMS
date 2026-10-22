# Tasks

## 1. 令牌真源

- [x] 1.1 新建 `packages/tokens/src/primitives.ts`，把 `primitive.css` 的原始值按组搬入（尺寸存数字、颜色与阴影存字符串），验证：`pnpm --filter @yunwms/tokens typecheck` 通过，且导出的 `.d.ts` 中键名与现有 CSS 变量一一对应
- [x] 1.2 编写 `packages/tokens/scripts/gen-css.ts` 并在 `package.json` 加 `gen` 脚本，验证：运行 `pnpm --filter @yunwms/tokens gen` 后生成 `primitive.css`，其变量集合与迁移前版本逐项相等（用脚本解析比对，允许格式差异）
- [x] 1.3 加本仓库首个单测（对应 D8）：断言生成结果包含 `--blue-500: #2b63e0`、`--space-4: 16px`，且变量总数等于 `primitives` 中声明的数量，验证：`pnpm --filter @yunwms/tokens test` 通过

## 2. antd 适配

- [x] 2.1 核对 `ant-design-vue@4.2.6` 的 Menu / Button / Table 组件级 token 名称（如 `itemHeight`、`controlHeight`、`cellPaddingBlock`），把实际名称回写 `design.md`，验证：结论逐条标注来源文件与行号，未核实的标「未验证」
- [x] 2.2 新增 `packages/tokens/src/web-antd.ts`，导出 `antdTheme`（不带类型注解，避免依赖 UI 库），验证：文件中不含十六进制与 `rgb(`，全部值引用 `primitives`

## 3. 组件包分叉

- [x] 3.1 新建 `packages/ui-web`（`PageHeader` / `StatusTag` / `EmptyState`，antd 实现），验证：包内无 `el-*` 引用，样式只引用 `var(--*)`
- [x] 3.2 `owner-portal` 的引用从 `@yunwms/ui` 切到 `@yunwms/ui-web`，验证：`pnpm --filter @yunwms/owner-portal build` 通过，三个页面渲染无控制台报错

## 4. 应用迁移

> 顺序说明：移除 Element Plus 的引入必须放在最后。此前把「移除 EP 引入」排在第一位，会让仍在用 `el-*` 的页面直接渲染失败。

- [x] 4.1 `App.vue` 增加 `ConfigProvider` 与 `App` 包裹，新增 `AppBootstrap` 子组件注入 `message` 与 HTTP 错误处理，验证：`/product` 页的 antd Tag 背景变为 `--color-success-bg` 的值（当前为 antd 默认 `#f6ffed`）
- [ ] 4.2 `BasicLayout.vue` 换成 antd 组件（布局、按钮、下拉、分割线），验证：侧栏为浅色、折叠按钮可用、控制台无 `Failed to resolve component`
- [ ] 4.3 迁移 3 个页面（登录 / 工作台 / 商品管理），验证：每页可正常渲染与交互，浏览器控制台无报错
- [ ] 4.4 `main.ts` 移除 EP 引入与 `app.use(ElementPlus)`，改引 `ant-design-vue/dist/reset.css`，验证：构建产物 CSS 中不再出现 `--el-` 变量，且三个页面仍正常渲染

## 5. 收尾

- [ ] 5.1 移除 `owner-portal` 对 `element-plus` 与 `@element-plus/icons-vue` 的依赖，验证：`pnpm --filter @yunwms/owner-portal why element-plus` 输出为空
- [ ] 5.2 更新 `openspec/config.yaml` 的技术栈行为「Vue 3 + Element Plus（迁移至 Ant Design Vue 4，进行中）」，验证：文件内出现该表述
- [ ] 5.3 在 `docs/frontend.md` 补记「antd 侧无法用 CSS 变量切换密度」的限制，验证：该文件「已验证与待验证」表内出现对应条目
- [ ] 5.4 人工走查 3 个页面并与 EP 版本对照，记录差异清单，验证：差异项逐条确认可接受或有对应处理

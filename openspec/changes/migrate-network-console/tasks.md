# Tasks

> 顺序原则：先切组件包与布局，再逐页迁移，最后才移除 EP 依赖——否则仍在用 `el-*` 的页面会直接渲染失败。

## 1. 组件包与布局

- [x] 1.1 `apps/network-console` 依赖加入 `@yunwms/ui-web`、`ant-design-vue`、`@ant-design/icons-vue`，三个基础组件引用切到 `@yunwms/ui-web`，验证：`pnpm --filter @yunwms/network-console build` 通过
- [x] 1.2 `BasicLayout.vue` 换成 antd 组件（Menu/MenuItem/Button/Divider），菜单 `:inline-indent="8"`、`selected-keys` 由路由派生，验证：浏览器实测菜单项高 44px、左内边距 8px、选中底 `rgb(238,244,255)`，与货主端逐项相同

## 2. 页面迁移

- [x] 2.1 `login` 页（6 处），验证：提交后跳转、提示走注入的 message
- [x] 2.2 `dashboard` 页（5 处），验证：页面渲染无报错，卡片间距与令牌一致
- [x] 2.3 `system` 页（7 处），验证：页面渲染与交互正常
- [x] 2.4 `warehouse` 页（7 处），验证：页面渲染与交互正常
- [x] 2.5 `customer` 页（22 处）——**拆分提交**：筛选表单、表格、弹窗/抽屉分开迁，验证：每步构建通过、页面可用
- [x] 2.6 `product` 页（13 处），验证：表格每列都有显式宽度且设了 `scroll.x`，实测行高不超过 52px

## 3. 移除 Element Plus

- [ ] 3.1 `main.ts` 移除 EP 引入与 `app.use(ElementPlus)`，改引 `ant-design-vue/dist/reset.css` 与 antd 中文包，验证：产物 CSS 中 `--el-` 计数为 0
- [ ] 3.2 移除 `element-plus`、`@element-plus/icons-vue` 依赖，验证：`pnpm --filter @yunwms/network-console why element-plus` 输出为空
- [ ] 3.3 删除 `packages/ui`（EP 版）及其 `exports`，network-console 不再引用，验证：全仓库 `grep -r "@yunwms/ui\b"` 无结果
- [ ] 3.4 删除 `packages/tokens/src/element-plus.css` 与 `exports` 的 `./element-plus`，验证：全仓库无 `tokens/element-plus` 引用

## 4. 收尾

- [ ] 4.1 `openspec/config.yaml` 技术栈行改为 `Vue 3 + Ant Design Vue 4`，去掉"进行中"，验证：文件内无"迁移至"字样
- [ ] 4.2 `docs/frontend.md` 端表改为仓网端 antd、组件包改为 `ui-web`，并移除"待迁移"表述，验证：表格与仓库结构一致
- [ ] 4.3 两应用并排对比（登录 / 商品 / 工作台），确认菜单与表格观感一致，记录差异清单
- [ ] 4.4 交付说明：列出改动文件、验证方式、遗留问题

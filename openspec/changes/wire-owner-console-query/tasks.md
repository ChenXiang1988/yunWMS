# Tasks

## 1. 数据层

- [x] 1.1 新增 `api/owner.ts` 与 `api/dict.ts`，按 docs/api 契约定义列表字段与查询参数。
- [x] 1.2 新增 `owner-model.ts`：五态文案、客户类型文案、ISO 时间格式、查询参数归一化。

## 2. 页面与组件

- [x] 2.1 扩展 `StatusTag` 支持大写五态且保留 product 页小写状态。
- [x] 2.2 新增 `OwnerFilters.vue` 并重写 `customer/index.vue`：真实筛选、服务端分页、加载/空态、无 mock 联系人列。

## 3. 测试与交付

- [x] 3.1 为纯模型函数增加 node:test，接入应用 `test` 脚本，验证：`pnpm --filter @yunwms/network-console test` 通过。
- [x] 3.2 运行 `pnpm -r typecheck`、`pnpm -r test`、`pnpm -r build`、`pnpm check:ui`，验证：全部通过。
- [ ] 3.3 运行只读审计并写 `completion.md`。

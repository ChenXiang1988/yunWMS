# Tasks

## 1. API 与模型

- [x] 1.1 扩展 `api/owner.ts`：创建、详情、编辑、状态、终止影响、删除类型与函数。
- [x] 1.2 扩展 `api/dict.ts` 与 `owner-model.ts`：国家/省州读取、合法流转、可删除、表单 payload。

## 2. 页面组件

- [x] 2.1 实现 `OwnerFormDrawer.vue` 新建/编辑与字典联动。
- [x] 2.2 实现 `OwnerDetailDrawer.vue`、`OwnerStatusModal.vue`、`OwnerDeleteModal.vue`。
- [x] 2.3 列表页接入新建按钮、操作列与刷新编排。

## 3. 验证与交付

- [x] 3.1 扩展纯模型 node:test，覆盖流转、可删除与 payload。
- [x] 3.2 运行 `pnpm -r typecheck`、`pnpm -r test`、`pnpm -r build`、`pnpm check:ui`，验证：全部通过。
- [ ] 3.3 运行只读审计并写 `completion.md`。

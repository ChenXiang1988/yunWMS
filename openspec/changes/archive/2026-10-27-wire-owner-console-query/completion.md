# 交付记录：wire-owner-console-query

## 目标与实际交付

- 目标：仓网端客户列表接真实接口，支持关键词、客户类型、五态与分页；建立纯模型测试。
- 实际：新增 owner/dict API 与纯模型；客户页改为真实列表；StatusTag 支持两套状态词表。

新增：

- `apps/network-console/src/api/owner.ts`
- `apps/network-console/src/api/dict.ts`
- `apps/network-console/src/views/customer/owner-model.ts`
- `apps/network-console/src/views/customer/owner-model.test.ts`
- `apps/network-console/src/views/customer/components/OwnerFilters.vue`

修改：

- `apps/network-console/src/views/customer/index.vue`
- `apps/network-console/src/views/customer/tsconfig` 所在应用 `tsconfig.json`
- `packages/ui-web/src/components/StatusTag.vue`
- 应用 `package.json` test 脚本

## 关键设计

- API 与纯模型分离；模型测试用 Node test，不新增依赖。
- 服务端分页，current/pageSize/total 以后端响应为准；筛选变化回第 1 页。
- 客户类型从 `OWNER_TYPE` 字典读取；失败时类型列回退原值，不阻断列表。
- StatusTag 保留小写状态给 product 页，同时支持大写五态。
- 列表只展示后端六字段，不含联系人/手机号。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| 货主模型测试 | 9 个 node:test 通过 |
| `pnpm -r typecheck` | 通过 |
| `pnpm -r test` | tokens 3 + network-console 9 通过 |
| `pnpm -r build` | 两个应用构建成功 |
| `pnpm check:ui` | 通过；扫描 65 个源文件 |
| 浏览器契约注入 | 列表、筛选、分页、空态、失败保留旧数据均实测通过 |

## 未验证项

- 未与真实后端联调；浏览器验证使用按 docs/api 契约构造的响应。
- 新建/编辑/详情/状态/删除未在本切片，按钮暂不提供。
- 未做完整移动端适配；沿用现有布局断点。

## 已知风险

- 字典请求失败时客户类型下拉为空，但列表仍可用。
- `tsconfig.json` 排除 `*.test.ts`，测试文件不进 vue-tsc；Node 运行通过。
- 页面样式沿用现有 inline 宽度，没有新增控件宽度令牌。

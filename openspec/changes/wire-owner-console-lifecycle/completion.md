# 交付记录：wire-owner-console-lifecycle

## 目标与实际交付

- 目标：接入货主新建、编辑、详情、状态流转、终止影响确认与删除草稿。
- 实际：完成四个弹层与页面编排；列表增加新建与操作列；真实后端走查通过。

新增：

- `views/customer/components/OwnerFormDrawer.vue`
- `views/customer/components/OwnerDetailDrawer.vue`
- `views/customer/components/OwnerStatusModal.vue`
- `views/customer/components/OwnerDeleteModal.vue`
- `views/customer/components/OwnerTable.vue`
- `views/customer/owner-form.ts`、`owner-status.ts`
- `views/customer/use-owner-dicts.ts`、`use-owner-lifecycle.ts`、`use-owner-transitions.ts`
- `views/customer/owner-form.test.ts`、`owner-status.test.ts`

修改：

- `api/owner.ts`、`api/dict.ts`、`index.vue`、`owner-model.ts`、`owner-model.test.ts`、`OwnerFilters.vue`

## 关键设计

- 组件只展示与 emit，请求与状态编排在 composable；符合 `docs/frontend.md` §4.3。
- 状态流转表、可删除、可写判断在 `owner-status.ts`，与后端枚举一致。
- 表单字典联动：国家变化重取州省；邮编必填读省州字典扩展。
- 从详情进入编辑/状态/删除时先关闭详情，避免浮层层级遮挡。
- 所有文件 ≤300 行。

## 验证结果

- `pnpm -r typecheck`：通过。
- `pnpm -r test`：tokens 3 + network-console 20，共 23 通过。
- `pnpm -r build`：两个应用构建成功。
- `pnpm check:ui`：通过，扫描 77 个源文件。
- 真实浏览器 + 隔离 PostgreSQL/后端走查：
  - 首次登录强制改密 → 客户管理空态；
  - 新建草稿（字典联动、校验、保存）；
  - 列表展示六字段；
  - 详情基本信息；
  - 编辑名称并保存；
  - 草稿 → 合作中；
  - 合作中终止：影响清单（0 商品/0 账号）、勾选确认、最终终止；
  - 终止后编辑/状态按钮禁用、删除入口消失；
  - 第二草稿删除成功、列表回到 1 条。

## 未验证项

- 联系人、税号、主账号页签未实现（后端无接口）。
- 前端权限点隐藏未做，403 由请求层提示。
- 未做移动端完整走查。

## 已知风险

- 编辑是后端全量替换语义，表单必须提交全部可编辑字段；当前 payload 构造已覆盖。
- 详情页国家/州省显示字典 value，未做中文标签。
- 前端 SKU 前缀规则与后端 SkuPrefix 是两份实现，需同步维护。
- 新建后刷新当前页，新记录可能不在当前页（后端排序未约定）。

# Design

## Context

列表已接真实接口。后端已有创建、详情、编辑、状态、终止影响、删除接口；联系人/税号/主账号不存在。页面必须按现有能力实现，不造占位页签。

## Goals / Non-Goals

**Goals:**

- 完成现有货主生命周期接口的前端闭环。
- 所有操作有成功反馈与错误透传。
- 状态流转与删除规则由纯模型判断，和 `OwnerStatus` 一致。
- 不新增依赖。

**Non-Goals:**

- 联系人、税号、主账号页签。
- 前端权限点渲染；后端 403 仍为最终边界。
- 数据范围与菜单驱动。

## Decisions

### D1. 组件拆分

- `OwnerFormDrawer.vue`：新建/编辑共用，模式区分只读字段。
- `OwnerDetailDrawer.vue`：详情与入口。
- `OwnerStatusModal.vue`：目标状态 + 终止确认。
- `OwnerDeleteModal.vue`：草稿删除确认。
- 页面 `index.vue` 只编排状态与刷新。

### D2. 字典加载

- 客户类型、国家、州省都从 DictController 读取；国家变化时重新取该国家的省州。
- 字典请求失败时提示，表单仍可输入，由后端最终校验。

### D3. 状态流转

- 纯模型维护 `DRAFT → POTENTIAL/ACTIVE`、`POTENTIAL → ACTIVE/TERMINATED`、`ACTIVE → SUSPENDED/TERMINATED`、`SUSPENDED → ACTIVE/TERMINATED`、`TERMINATED → []`。
- 终止选择后请求影响清单，展示商品/账号数，必须勾选确认。
- 后端仍做最终校验。

### D4. 编辑字段

- 编辑不可改编码、SKU 前缀、状态；表单只提交可编辑字段。
- 全量替换语义：空字段按 null 提交，不清掉未显示字段。

### D5. 删除

- 仅 DRAFT 显示删除入口；后端仍校验。
- 删除成功关闭详情并刷新列表。

### D6. 测试

- 纯模型测试合法流转、终止、可删除、表单 payload 构造；不新增 DOM 测试依赖。
- 浏览器测试由调用方在后端启动后走查。

# Design

## Context

后端已实现 `GET /api/console/owners` 与 `GET /api/console/dicts/{dictType}/items`；前端只有认证 API 和 mock 客户页。`StatusTag` 当前只认小写四态，product 页仍在使用。

## Goals / Non-Goals

**Goals:**

- 列表、筛选、分页由后端驱动，展示六字段真实契约。
- 状态统一大写五态，同时不破坏 product 页的小写状态。
- 纯模型函数有 node:test 覆盖，不新增依赖。

**Non-Goals:**

- 新建、编辑、详情、状态流转、删除（下一 change）。
- 联系人/税号/账号页签。
- 权限按钮隐藏。

## Decisions

### D1. API 与纯模型分离

- 选择：`api/owner.ts` 负责 HTTP 与类型；`views/customer/owner-model.ts` 只放状态文案、类型文案、日期格式和查询参数转换。
- 理由：纯函数可用现有 Node test 测，不需要 DOM 或新测试框架。
- 备选：Vitest + Vue Test Utils。需要新依赖与 DOM 环境；本 change 不引入，留待后续。

### D2. 服务端分页

- 选择：Ant Table 的 pagination 受控，current/pageSize/total 来自后端；查询变化时重置到第 1 页。
- 理由：后端有 200 上限；前端不拉全量。

### D3. 字典驱动客户类型

- 选择：进入页面时请求 `OWNER_TYPE` 启用项；筛选下拉使用 label/value。
- 理由：避免硬编码 BRAND/FORWARDER，字典停用项不再可选。

### D4. StatusTag 兼容两套状态词表

- 选择：联合类型包含小写旧状态和大写五态；TONE 映射分别定义。
- 理由：product 页仍用小写展示，一次性改所有页面超出本切片。
- 备选：只保留大写。否决，会破坏 product 页。

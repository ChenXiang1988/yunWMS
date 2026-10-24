# 接口台账

> **生成物，禁止手改。** 由 `code/tools/api-inventory/gen_api_inventory.py` 生成。
> 来源：实时拉取 `http://localhost:8080/v3/api-docs`
> 源 commit：`7b1e705`
> 计数口径：OpenAPI operation（path × method），不是 Java 方法数
> 字段记号：`a.b` 嵌套，`a[]` 数组元素，`↻` 自引用已截断，`…` 嵌套过深已截断

## 汇总

| 项 | 值 |
| --- | --- |
| 接口总数 | 1 |
| POST | 1 |

| 主体 | 数量 |
| --- | --- |
| 共用 | 1 |

## 统一响应外壳

所有接口返回 `ApiResult`，HTTP 状态码固定 200；仅未认证 401、无权限 403 例外。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | integer | 0 表示成功，非 0 见错误码表 |
| `message` | string | 提示文本 |
| `data` | T | 业务数据，下方逐接口展开 |

下文「返回字段」均指 `data` 内部，不含外壳。

## 接口清单

共 1 个接口。

### 1. `POST /api/auth/login`

- 主体：共用
- 分页：否
- 响应外壳：`ApiResult`

**请求字段**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `loginName` | string | 是 | — |
| `password` | string | 是 | — |

**返回字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `token` | string | — |
| `subjectType` | string | — |
| `ownerId` | string | — |
| `displayName` | string | — |
| `roles` | string[] | — |
| `accessiblePortals` | string[] | — |

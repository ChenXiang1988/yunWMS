# 接口台账

> **生成物，禁止手改。** 由 `code/tools/api-inventory/gen_api_inventory.py` 生成。
> 来源：实时拉取 `http://localhost:18084/v3/api-docs`
> 源 commit：`8391cf5`
> 计数口径：OpenAPI operation（path × method），不是 Java 方法数
> 字段记号：`a.b` 嵌套，`a[]` 数组元素，`↻` 自引用已截断，`…` 嵌套过深已截断

## 汇总

| 项 | 值 |
| --- | --- |
| 接口总数 | 15 |
| GET | 4 |
| POST | 3 |
| PUT | 7 |
| DELETE | 1 |

| 主体 | 数量 |
| --- | --- |
| console（仓网端） | 13 |
| 共用 | 2 |

## 统一响应外壳

所有接口返回 `ApiResult`，HTTP 状态码固定 200；仅未认证 401、无权限 403 例外。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | integer | 0 表示成功，非 0 见错误码表 |
| `message` | string | 提示文本 |
| `data` | T | 业务数据，下方逐接口展开 |

下文「返回字段」均指 `data` 内部，不含外壳。

## 接口清单

共 15 个接口。

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
| `mustChangePassword` | boolean | — |
| `roles` | string[] | — |
| `accessiblePortals` | string[] | — |

### 2. `PUT /api/auth/password`

- 主体：共用
- 分页：否
- 响应外壳：`ApiResult`

**请求字段**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `oldPassword` | string | 是 | — |
| `newPassword` | string | 是 | — |

**返回字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `—` | object | 无字段声明 |

### 3. `GET /api/console/roles`

- 主体：console（仓网端）
- 分页：是
- 响应外壳：`ApiResult`

**请求字段**

无请求体。

**返回字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pageNo` | integer(int32) | — |
| `pageSize` | integer(int32) | — |
| `total` | integer(int64) | — |
| `records` | RoleListItemResponse[] | — |
| `records[].id` | string | — |
| `records[].code` | string | — |
| `records[].name` | string | — |
| `records[].builtin` | boolean | — |
| `records[].status` | string | — |
| `records[].userCount` | integer(int64) | — |
| `records[].permissionCount` | integer(int64) | — |
| `records[].remark` | string | — |

### 4. `POST /api/console/roles`

- 主体：console（仓网端）
- 分页：否
- 响应外壳：`ApiResult`

**请求字段**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `code` | string | 是 | — |
| `name` | string | 是 | — |
| `remark` | string | 否 | — |
| `menuIds` | integer(int64)[] | 否 | — |

**返回字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | — |
| `code` | string | — |

### 5. `GET /api/console/roles/{id}`

- 主体：console（仓网端）
- 分页：否
- 响应外壳：`ApiResult`

**请求字段**

无请求体。

**返回字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | — |
| `code` | string | — |
| `name` | string | — |
| `builtin` | boolean | — |
| `status` | string | — |
| `userCount` | integer(int64) | — |
| `permissionCount` | integer(int64) | — |
| `remark` | string | — |
| `menuIds` | string[] | — |

### 6. `PUT /api/console/roles/{id}`

- 主体：console（仓网端）
- 分页：否
- 响应外壳：`ApiResult`

**请求字段**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | string | 是 | — |
| `remark` | string | 否 | — |
| `menuIds` | integer(int64)[] | 是 | — |

**返回字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `—` | object | 无字段声明 |

### 7. `DELETE /api/console/roles/{id}`

- 主体：console（仓网端）
- 分页：否
- 响应外壳：`ApiResult`

**请求字段**

无请求体。

**返回字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `—` | object | 无字段声明 |

### 8. `PUT /api/console/roles/{id}/status`

- 主体：console（仓网端）
- 分页：否
- 响应外壳：`ApiResult`

**请求字段**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `status` | string enum(ACTIVE, DISABLED) | 是 | — |

**返回字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `—` | object | 无字段声明 |

### 9. `GET /api/console/users`

- 主体：console（仓网端）
- 分页：是
- 响应外壳：`ApiResult`

**请求字段**

无请求体。

**返回字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pageNo` | integer(int32) | — |
| `pageSize` | integer(int32) | — |
| `total` | integer(int64) | — |
| `records` | UserListItemResponse[] | — |
| `records[].id` | string | — |
| `records[].loginName` | string | — |
| `records[].displayName` | string | — |
| `records[].subjectType` | string | — |
| `records[].ownerId` | string | — |
| `records[].ownerName` | string | — |
| `records[].status` | string | — |
| `records[].roles` | string[] | — |
| `records[].lastLoginAt` | string(date-time) | — |

### 10. `POST /api/console/users`

- 主体：console（仓网端）
- 分页：否
- 响应外壳：`ApiResult`

**请求字段**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `loginName` | string | 是 | — |
| `displayName` | string | 是 | — |
| `subjectType` | string enum(STAFF, OWNER) | 是 | — |
| `ownerId` | integer(int64) | 否 | — |
| `email` | string | 否 | — |
| `wecomUserId` | string | 否 | — |
| `initialPassword` | string | 是 | — |
| `roleIds` | integer(int64)[] | 否 | — |

**返回字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | — |
| `loginName` | string | — |

### 11. `GET /api/console/users/{id}`

- 主体：console（仓网端）
- 分页：否
- 响应外壳：`ApiResult`

**请求字段**

无请求体。

**返回字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | — |
| `loginName` | string | — |
| `displayName` | string | — |
| `subjectType` | string | — |
| `ownerId` | string | — |
| `ownerName` | string | — |
| `status` | string | — |
| `roles` | string[] | — |
| `lastLoginAt` | string(date-time) | — |
| `email` | string | — |
| `wecomUserId` | string | — |
| `dataScopeType` | string | — |
| `createdAt` | string(date-time) | — |
| `roleIds` | string[] | — |

### 12. `PUT /api/console/users/{id}`

- 主体：console（仓网端）
- 分页：否
- 响应外壳：`ApiResult`

**请求字段**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `displayName` | string | 是 | — |
| `email` | string | 否 | — |
| `wecomUserId` | string | 否 | — |

**返回字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `—` | object | 无字段声明 |

### 13. `PUT /api/console/users/{id}/password`

- 主体：console（仓网端）
- 分页：否
- 响应外壳：`ApiResult`

**请求字段**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `newPassword` | string | 是 | — |

**返回字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `—` | object | 无字段声明 |

### 14. `PUT /api/console/users/{id}/roles`

- 主体：console（仓网端）
- 分页：否
- 响应外壳：`ApiResult`

**请求字段**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roleIds` | integer(int64)[] | 是 | — |

**返回字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `—` | object | 无字段声明 |

### 15. `PUT /api/console/users/{id}/status`

- 主体：console（仓网端）
- 分页：否
- 响应外壳：`ApiResult`

**请求字段**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `status` | string enum(ACTIVE, DISABLED) | 是 | — |

**返回字段**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `—` | object | 无字段声明 |

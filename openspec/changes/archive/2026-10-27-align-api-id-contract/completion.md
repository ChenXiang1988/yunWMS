# 交付记录：align-api-id-contract

## 目标与实际交付

- 目标：请求体 JSON ID 改为字符串，路径参数保持长整型；OpenAPI/inventory 重新生成。
- 实际：5 个请求字段改为字符串，新增统一解析器；测试与生成物同步。

修改：

- `wms-auth` 的 5 个请求 DTO、UserController、RoleController
- 新增 `interfaces/controller/request/RequestIds.java`
- `wms-master` 的 `OwnerCreateRequest`、`OwnerDetailResponse` 示例
- 3 个集成测试与 `RequestIdsTest`
- `openspec/specs/product-center/spec.md`

生成：

- `docs/api/openapi.json`
- `docs/api/inventory.md`

## 关键设计

- 字符串只停留在 interfaces 层；Controller 经 `RequestIds` 解析为 Long。
- null、空列表、空字符串的语义保持：null 原样、空列表原样、空白/非数字返回 40000。
- 路径参数保持 long。
- OpenAPI 由运行中的后端拉取生成，不手改。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| 后端全量 `./mvnw -B verify` | BUILD SUCCESS；wms-auth 167 / wms-master 83 / wms-app 94，合计 344 tests，0 失败 |
| 新增解析单测 | `RequestIdsTest` 7 个用例通过 |
| 非法 ID 接口测试 | 用户 ownerId/roleIds、角色 menuIds 非法字符串均返回 40000 |
| OpenAPI 类型 | CreateUserRequest.ownerId string；roleIds/menuIds 均 array<string> |
| operation 数 | 25 |
| inventory 一致性 | `gen_api_inventory.py --check` 通过 |
| 源 commit | `8d2f7a2` |

## 未验证项

- 未运行前端；前端尚未消费用户/角色接口。
- Jackson 仍可把 JSON number 反序列化为 String；契约不承诺数字，旧调用方在窗口期内不会被拒绝。
- 没有对超过 2^53 的真实 ID 做端到端测试；数据库 bigint 与字符串传输路径已就绪。

## 已知风险

- 请求体 ID 从 number 变 string 对未接入的前端无影响；接入时必须按 inventory/OpenAPI 使用字符串。
- `RequestIds` 会 trim 首尾空白，`" 42 "` 被接受为 42；若要严格拒绝空白需另定口径。

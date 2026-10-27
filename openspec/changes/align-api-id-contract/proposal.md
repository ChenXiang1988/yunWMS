# Proposal

## Why

架构审计确认：响应体 ID 已按字符串返回，但请求体仍有 5 个数字字段，OpenAPI 因而生成 `integer/int64`。这与 `docs/api-convention.md:63` 的“JSON ID 用字符串”冲突，也让前端按 OpenAPI 生成类型时得到 `number`，存在超过 2^53 的精度风险。

证据见 `docs/research/architecture-boundary-snapshot-2026-10-27.md`。

## What Changes

- `CreateUserRequest.ownerId`：`Long` → `String`
- `CreateUserRequest.roleIds`：`List<Long>` → `List<String>`
- `AssignRolesRequest.roleIds`：`List<Long>` → `List<String>`
- `CreateRoleRequest.menuIds`：`List<Long>` → `List<String>`
- `UpdateRoleRequest.menuIds`：`List<Long>` → `List<String>`
- Controller 在协议层把字符串解析为 Long；非法值返回 40000。
- 路径参数保持 `long`，OpenAPI 的 int64 仅用于路径。
- 清理 `AMZ1` 示例，重新生成 OpenAPI 与 inventory。

**BREAKING**：请求体这些字段的 JSON 类型从 number 变为 string；当前前端尚未消费这些接口。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

无。业务规则、权限和状态机不变，只改协议类型。

## Impact

- **修改**：`wms-auth` 的 5 个请求 DTO、3 个 Controller、请求解析测试/集成测试
- **生成**：`docs/api/openapi.json`、`docs/api/inventory.md`
- **文档**：`OwnerCreateRequest`、`OwnerDetailResponse` 的 SKU 示例、`product-center` 规格
- **不变**：路径参数、响应 DTO、数据库、前端运行代码

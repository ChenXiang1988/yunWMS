# Tasks

## 1. 请求契约

- [x] 1.1 将 5 个请求 DTO 的 ID 字段改为 String/List<String>，更新字段描述。
- [x] 1.2 新增请求 ID 解析器并在 User/Role Controller 使用；非法值返回 40000。

## 2. 测试与示例

- [x] 2.1 更新请求构造与集成测试 JSON 为字符串，新增非法 ID 40000 用例，验证：后端全量测试通过。
- [x] 2.2 清理 `AMZ1` 示例为 `AMZ2`，验证：全仓目标路径不再有非法示例。

## 3. 生成与交付

- [x] 3.1 启动隔离数据库与应用，重新生成 `openapi.json` 和 `inventory.md`，验证：请求体 ID 为 string、operation 数正确。
- [ ] 3.2 运行只读审计并写 `completion.md`，验证：无迁移、无手改生成物、路径与行数合规。

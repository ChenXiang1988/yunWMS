# Tasks

## 1. master 能力

- [x] 1.1 新增 `OwnerNameLookup` Application 接口与实现，按 ID 批量返回 id+name，空集合短路，包含软删除。
- [x] 1.2 新增 Mapper 投影与 XML 查询，验证：显式列、无 deleted_at 条件、单次 SQL。

## 2. 装配与删除

- [x] 2.1 新增 `wms-app` OwnerNameQueryAdapter，删除 auth `OwnerNameMapper`/XML/Row/实现。
- [x] 2.2 删除表所有权清单中的 auth→owner 例外，验证：Mapper 门禁仍通过。

## 3. 验证与交付

- [x] 3.1 增加已软删除货主名称仍显示的集成测试，验证：用户列表/名称解析不回归。
- [x] 3.2 运行后端全量 `./mvnw -B verify`，验证：所有测试通过。
- [ ] 3.3 运行只读审计并写 `completion.md`，验证：无 auth 直读 owner、路径与行数合规。

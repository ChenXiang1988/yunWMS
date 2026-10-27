# 交付记录：refactor-owner-name-query

## 目标与实际交付

- 目标：删除 auth 直读 `owner` 表，由 master 提供批量、含软删除的 id+name 查询，经 app bridge 装配。
- 实际：master 新增 Application 查询能力与轻量投影；app 新增适配器；auth 旧 Mapper/XML/Row/实现已删除。

新增：

- `wms-master/.../application/query/OwnerNameLookup.java`
- `wms-master/.../application/query/OwnerNameLookupService.java`
- `wms-master/.../domain/port/OwnerNameReader.java`
- `wms-master/.../infrastructure/persistence/repository/OwnerNameReaderImpl.java`
- `wms-master/.../infrastructure/persistence/entity/OwnerNameRow.java`
- `wms-master/src/test/.../OwnerNameLookupServiceTest.java`
- `wms-app/.../bridge/OwnerNameQueryAdapter.java`

删除：

- auth `OwnerNameMapper.java`
- auth `OwnerNameMapper.xml`
- auth `OwnerNameRow.java`
- auth `OwnerNameQueryImpl.java`

修改：

- master `OwnerMapper` 与 XML 增加 `selectNamesByIds`
- auth `OwnerNameQuery` / `UserSummary` 注释
- `table-ownership.json` 删除 auth→owner 例外
- `UserManagementIntegrationTest` 新增软删除货主名称仍显示的用例
- Mapper 门禁测试的历史注释

## 关键设计

- master Application 能力 `findNamesByIds` 只返回 id+name，不排除软删除，空集合不查库。
- application 通过 domain 端口 `OwnerNameReader` 访问持久化，避免 application import infrastructure。
- `wms-app` bridge 实现 auth 的 `OwnerNameQuery`，跨模块只经端口/Application 能力。
- 删除表所有权例外后，Mapper 门禁会阻止 auth 再次直读 owner。

## 验证结果

- `./mvnw -B clean verify`：BUILD SUCCESS。
- wms-auth 167、wms-master 85、wms-app 103，合计 355 tests，0 failures/errors。
- 新增 `OwnerNameLookupServiceTest`：空/null 短路、单次批量查询。
- 新增 `UserManagementIntegrationTest#shouldKeepOwnerNameOfSoftDeletedOwner`：软删货主名称仍显示。
- `MapperTableAccessTest` 通过，auth→owner 例外已清空；`grep FROM owner` 在 auth main 无结果。

## 未验证项

- 未运行前端或 API 契约测试；本变更无 API 字段变化。
- 未做数据库迁移；查询只读。
- 未在生产规模下测量批量 IN 查询性能。

## 已知风险

- 用户列表的货主名称查询不再在 auth 本地，依赖 app bridge 被 Spring 扫描；集成测试已覆盖装配。
- 查询不过滤 `deleted_at` 是有意语义，后续修改该 SQL 时必须保留。

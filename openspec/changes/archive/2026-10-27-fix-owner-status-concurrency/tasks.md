# Tasks

## 1. 后端实现

- [x] 1.1 在 `OwnerRepository` 增加锁读端口，在 `OwnerMapper`/XML 增加 `SELECT ... FOR UPDATE`，验证：查询只返回未删除货主并锁定目标行。
- [x] 1.2 在 `OwnerStatusChangeService.change` 加事务并改用锁读，验证：判定与更新在同一事务、同一行锁内完成。

## 2. 回归测试

- [x] 2.1 新增真实 PostgreSQL 并发集成测试，覆盖 ACTIVE 终止 vs 暂停、SUSPENDED 终止 vs 恢复、重复终止，验证：终止成功后最终状态不被覆盖，重复终止幂等。
- [x] 2.2 运行后端全量 `./mvnw -B verify`，验证：既有 331 个测试与新增用例全部通过。

## 3. 交付

- [x] 3.1 运行只读交付审计，验证：无迁移、无 API 变更、路径与行数合规。
- [x] 3.2 写 `completion.md`，验证：记录实际测试结果、并发用例与未验证项。

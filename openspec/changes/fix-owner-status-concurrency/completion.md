# 交付记录：fix-owner-status-concurrency

## 目标与实际交付

- 目标：让“终止为终态”在同一货主的并发状态流转下成立。
- 实际：状态流转加事务和 `SELECT ... FOR UPDATE` 锁读；新增真实 PostgreSQL 双线程并发测试。

修改：

- `wms-master/.../domain/port/OwnerRepository.java`
- `wms-master/.../infrastructure/persistence/mapper/OwnerMapper.java`
- `wms-master/src/main/resources/mapper/master/OwnerMapper.xml`
- `wms-master/.../infrastructure/persistence/repository/OwnerRepositoryImpl.java`
- `wms-master/.../application/service/OwnerStatusChangeService.java`
- `wms-master/src/test/.../OwnerStatusChangeServiceTest.java`（只同步 mock stub，不改断言）

新增：

- `wms-app/src/test/java/com/yunwms/app/OwnerStatusConcurrencyIntegrationTest.java`

## 关键设计

- 事务边界在 `OwnerStatusChangeService.change`，确保锁读、状态判定、终止确认和更新同属一个事务。
- 锁读走 `OwnerRepository.findByIdForUpdate`，Mapper XML 使用显式列清单和 `FOR UPDATE`。
- 不新增版本列、不新增迁移、不改 API。
- 并发测试使用真实 PostgreSQL、Spring 事务代理和双线程 barrier；`CurrentOperator` 替换为固定测试值。

## 验证结果

命令：

```bash
cd code/backend && JAVA_HOME=/opt/homebrew/opt/openjdk@21 \
  PATH=/opt/homebrew/opt/openjdk@21/bin:$PATH ./mvnw -B verify
```

结果：

- `BUILD SUCCESS`
- wms-auth 160 tests
- wms-master 83 tests
- wms-app 91 tests（新增 3 个并发用例）
- 合计 334 tests，0 failures，0 errors

反向敏感性验证：临时移除 `@Transactional` 并改回 `findById` 后，新并发用例 3 个中 2 个失败，分别复现终止被暂停、终止被恢复覆盖；恢复实现后 3/3 通过。该临时改动已还原。

## 未验证项

- 未做高并发压力测试；只验证两个并发写者。
- 未在真实生产数据库规模或长事务负载下测量锁等待。
- 未修改前端；状态切换页面仍待后续 change。

## 已知风险

- 同一货主的状态流转由并发变为串行，管理端低频场景可接受。
- 未确认终止且需读取影响清单时，锁持有时间包含一次只读查询；当前数据量下可接受。
- 新增测试使用 `@MockitoBean` 会创建第二个 Spring 测试上下文，wms-app 测试启动时间略增。

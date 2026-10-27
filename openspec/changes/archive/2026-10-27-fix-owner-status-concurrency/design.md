# Design

## Context

见 `proposal.md`。现有实现：

- `OwnerStatusChangeService.java:80-105`：`findById` → 状态机判定 → `updateStatus`；
- `OwnerMapper.xml:136-143`：UPDATE 只按 `id + deleted_at IS NULL`，不比较原状态；
- 无 `version` 列、无行锁、无事务。

## Goals / Non-Goals

**Goals:**

- 终止成功后，最终状态不得被并发暂停或恢复覆盖。
- 重复终止保持幂等。
- 不新增迁移，不改变 HTTP 契约。

**Non-Goals:**

- 不引入乐观锁版本列。
- 不改状态机允许的流转集合。
- 不改其他货主写路径。

## Decisions

### D1. 事务 + `SELECT FOR UPDATE`，不加版本列

- 选择：`OwnerStatusChangeService.change` 加 `@Transactional`，锁读当前行后再判定和更新。
- 理由：状态流转是低频管理操作；行锁能直接保证同一货主的读写串行。无迁移、无前端冲突处理。
- 备选：版本列。需要迁移与冲突提示，收益在当前并发量下不明显。
- 备选：`UPDATE ... WHERE status = 旧值`。文档禁止依据影响行数判成功/冲突；且无法在同一事务内可靠区分“状态已变”与“其他失败”。

### D2. 锁读作为仓储端口方法

- 选择：`OwnerRepository.findByIdForUpdate(long)`，实现走专用 Mapper 查询，返回 `Optional<Owner>`。
- 理由：锁语义是持久化能力，不能泄漏到 Controller；端口明确表达“这个读会阻塞其他写”。
- 备选：在服务层直接调 Mapper。否决，破坏 application → domain port 边界。

### D3. 锁范围只覆盖状态流转

- 选择：只改状态流转用例；创建、编辑、删除不改。
- 理由：创建是单条 INSERT，编辑/删除有自己的并发语义；混入会扩大风险。
- 备选：给所有写用例加锁。否决，超出本缺陷范围。

### D4. 并发测试走真实事务代理和 PostgreSQL

- 选择：Spring Boot 集成测试中启动两个线程，分别调用 `OwnerStatusChangeService`，使用真实 PostgreSQL 和 `@Transactional` 代理；`CurrentOperator` 用测试 bean 固定。
- 理由：Mockito 单测无法验证锁；直接调 Mapper 也测不到服务事务。
- 备选：MockMvc 并发。可行但受测试上下文共享和线程安全影响，不如直接调代理稳定。

### D5. 不修改 API 和数据库

- 锁读查询为只读 SQL；无 DDL、无 DTO 变化。
- 若未来并发量增加，再评估版本列。

## Risks / Trade-offs

- **锁持有期间做过长查询** → 锁内只做状态判定和单行更新；影响清单查询仅在需要确认时执行，且为只读。
- **死锁** → 单行锁，无多表锁定顺序；风险低。
- **测试偶发不稳定** → 使用 barrier 让两请求同时起跑，重复多轮；断言终态和响应，不依赖执行顺序。
- **锁等待增加延迟** → 管理端低频，可接受。

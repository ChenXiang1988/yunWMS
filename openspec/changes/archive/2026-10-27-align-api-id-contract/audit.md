# 审计记录

- change: `align-api-id-contract`
- anchor: `6bc78ac62e44154b4121c11043051073229788db`（`git merge-base main HEAD`）
- 审计时间：2026-10-27
- 审计方式：`delivery-auditor` 只读 + `audit_delivery.py` 机器证据

## 逐项结论

| 检查项 | 结论 | 证据 |
| --- | --- | --- |
| 路径白名单 | 通过 | 22 个文件均在请求 DTO、2 个 Controller、测试、Owner 示例、API 生成物、product spec 与 change 工件 |
| 单次提交 ≤400 行 | 通过 | 179、157、113、63、50 行 |
| 迁移与代码分离 | 通过 | 无 SQL、无迁移 |
| 违规依赖 | 通过 | 无 `pom.xml`、`package.json`、lock |
| 前端/色值 | 通过 | 无前端代码改动 |
| 测试断言 | 通过 | 既有断言无删除；新增 7 个解析单测与 3 个非法 ID 接口用例 |
| 请求 ID 类型 | 通过 | OpenAPI 中 ownerId 为 string，roleIds/menuIds 为 array<string>；路径参数未改 |
| 生成物 | 通过 | inventory 与 openapi 由运行实例生成，`gen_api_inventory --check` 通过 |
| 机器证据 | 通过 | 脚本 `blocking=[]`、`unknowns=[]`、operation 25 |

## 存量违约

| 提交 | 违规 | 处置 |
| --- | --- | --- |
| 本分支 | 无 | 不适用 |

## 结论

**通过。** `mvnw verify` 的 344 tests 由实现者执行并报告；审计未复跑构建。OpenAPI 已在隔离数据库与本地实例上重新生成，生成物一致性已用只读 `--check` 验证。

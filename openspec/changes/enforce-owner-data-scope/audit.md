# 审计记录

- change：`enforce-owner-data-scope`
- anchor：`release/architecture-hardening-2026-10-27@6535bc5`
- 审计时间：2026-10-27
- 方式：`audit_delivery.py` 机器证据 + 主 Agent 只读复核
- 说明：`delivery-auditor` 子 Agent 因 API 连接超时未完成，本记录不冒充独立审计结论；已在结论中标注。

## 机器审计

| 检查项 | 结论 | 证据 |
| --- | --- | --- |
| 路径白名单 | 通过 | 99 个文件均在 `code/backend/**`、`docs/api/**`、`docs/research/mybatis-data-permission.md`、change 目录、`implement-owner/tasks.md` |
| 单笔提交 ≤400 行 | 通过 | 27 笔均 ≤378；原 406 行提交已重拆 |
| 禁止文件 | 通过 | 无 `.sql`、`.vue`、`.ts`、`.css` |
| 生成物 | 预期 | `docs/api/openapi.json`、`inventory.md` 由运行中后端重新生成，源 commit `fe45410`，27 operations；非手改 |
| 数据库迁移 | 通过 | 本 change 无迁移；使用现有表 |
| 依赖 | 通过 | 仅新增 `mybatis-plus-jsqlparser:3.5.9`，Apache-2.0；无其他依赖/lock 改动 |
| OpenSpec | 通过 | `openspec validate enforce-owner-data-scope` 通过；`skip_specs` 合理 |

## 内容复核

| 检查项 | 结论 | 证据 |
| --- | --- | --- |
| OWNER 强制隔离 | 通过 | `DataScopeResolver` 对 OWNER 返回所属货主；集成测试即使角色 ALL 也只读自己 |
| 用户覆盖优先 | 通过 | 覆盖非空只按覆盖；null 清除后回落角色 |
| 角色并集 | 通过 | 启用角色的 CUSTOM 并集、ASSIGNED 实时、ALL 吞并；禁用立即排除 |
| 空范围 fail-closed | 通过 | `NONE → 1=0`；CUSTOM 空集合法且不贡献；无登录上下文 NONE |
| 查询范围 | 通过 | 登记 `OwnerMapper.countByFilter/selectPageByFilter/selectVisibleById`；详情与终止影响 404；count/records 一致 |
| 未登记语句 | 通过 | `DataScopePermissionHandlerTest` 验证不取范围、不追加条件 |
| 测试适配 | 通过 | SQL 上限 5→8 有明确构成；Lifecycle 补 ALL；Persistence mock 范围，均为必要适配 |
| 测试结果 | 通过 | `clean verify` 431 tests，0 失败 |
| completion 披露 | 通过 | 已列写路径未过滤、SQL 解析开销、字面量 ID、异步上下文、CUSTOM 规模 |

## 残余风险

| 项 | 分类 | 说明 |
| --- | --- | --- |
| 货主写路径未过滤 | 已知风险 | 编辑/状态/删除仍按 id；知道 id 的内部员工仍可能写他方货主。需后续 change 明确授权模型 |
| 全量 SELECT 解析 | 已知性能风险 | 未登记语句不追加条件但仍解析；当前规模可接受 |
| ownerId 字面量 | 已知实现约束 | 来自数据库、无字符串拼接，但无法使用 JDBC 参数映射 |
| CUSTOM IN 列表增长 | 已知规模风险 | 当前管理端规模可接受 |
| 独立审计未完成 | 流程风险 | delivery-auditor API 超时；本记录为机器证据 + 主 Agent 复核 |

## 结论

**通过（机器审计 + 主 Agent 复核）。** 没有路径越界、超行数提交、迁移混入或测试断言削弱；生成物为预期契约更新。独立审计子 Agent 未完成，归档记录保留该缺口。

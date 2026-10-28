# Proposal

## Why

`docs/coding.md` 新增「代码组织」「校验与异常」两节，定下三条口径：入口只校验一次、单次逻辑不乱抽函数、低概率防御先确认。后端存量与新约定脱节：`src/main` 下共 119 处 `null` 判断、19 处 `try/catch`（auth 69/11，master 34/8，app 6/0，web 0）。这些未必都违规，但读写双方都按存量写法照抄，新约定会立刻失效。

## What Changes

- 先出违规清单，按「重复判空 / 合法防御 / 无处理 catch / 跨边界转换 catch」四类归档，只对前两类中确属重复的部分动手。
- 清理 auth、master、app 三个模块入口之后的重复判空与重复格式校验。
- 清理无处理动作的 `try/catch`：捕获后仅记录日志、捕获后原样重抛。
- 保留合法防御：跨模块 `api` 包前置条件断言、外部系统返回值与报文、金额与库存数量写入前的不变式、并发与幂等路径、`GlobalExceptionHandler` 与过滤器兜底转换、数据库约束。

**BREAKING**：无。对外 API、错误码、错误文案、数据库结构均不变。

## Capabilities

### New Capabilities

无。本 change 不引入业务能力。

### Modified Capabilities

无。纯实现质量整改，不改变任何规格描述的行为。

## Impact

- **代码**：`wms-auth`、`wms-master`、`wms-app`（`wms-web` 无存量，不涉及）
- **依赖**：无新增
- **数据库**：无迁移
- **API**：无变化；整改中若发现判空所在分支实际承担错误码语义，**不得**顺手改动，须单独提出
- **前端**：不改

## 调研门禁说明

本 change 不引入新功能域、新模块、新第三方依赖，不触发 `AGENTS.md` 的 R1-R6 调研门禁。规范依据为 `docs/coding.md` 的「代码组织」「校验与异常」两节。

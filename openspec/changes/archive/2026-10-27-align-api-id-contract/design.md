# Design

## Context

见 `proposal.md`。当前请求 DTO 使用 `Long` / `List<Long>`，OpenAPI 生成 integer；响应侧已经用 `String` 并由 assembler 转换。

## Goals / Non-Goals

**Goals:**

- 请求体 ID 在 JSON 中为字符串，OpenAPI 与规范一致。
- 非法字符串返回 40000，不落到 50000。
- 生成物由工具重跑，不手改。

**Non-Goals:**

- 不改路径参数。
- 不改响应 DTO。
- 不改业务服务签名与数据库类型。

## Decisions

### D1. 在 interfaces 层解析，不把字符串带进 application

- 选择：请求 DTO 保持 String；Controller 调 `RequestIds` 解析成 Long 后再构造 Command。
- 理由：application/domain 只认识 Long；字符串是 HTTP 协议事实，不能污染用例。
- 备选：把 Command 改成 String。否决，会让所有下游重复解析。

### D2. 统一解析器

- 选择：新增包内 `RequestIds`，提供可选单个 ID 与可选 ID 列表解析；空值/null 规则保持：
  - 单值 null → null；
  - 列表 null → null；
  - 空列表 → 空列表；
  - 空白或非数字 → `BizException.badRequest`。
- 理由：同一规则只写一处，错误信息带字段名。
- 备选：每个 Controller 写 `Long.parseLong`。否决，重复且异常类型不统一。

### D3. 路径参数维持 long

- 路径不是 JSON，不涉及 JS 大整数精度；改动会让所有路由和前端调用变化，收益为零。

### D4. 示例清理与生成

- `AMZ1` 违反 V035 的字符排除规则，改为 `AMZ2`。
- OpenAPI/inventory 必须由 `/v3/api-docs` 重新生成；生成前启动隔离数据库与应用。
- 不手改生成物。

## Risks / Trade-offs

- **旧调用方发送数字**：Jackson 通常能把数字反序列化为 String，但契约不再承诺；前端尚未接入，窗口期合适。
- **解析异常**：必须映射 40000；用统一 helper 和测试覆盖。
- **生成物漂移**：提交前对比 operation 数量与 ID 字段类型。

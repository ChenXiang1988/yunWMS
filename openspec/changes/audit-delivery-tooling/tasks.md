# Tasks

## 1. 工具实现

- [x] 1.1 创建 `code/tools/audit-delivery/audit_delivery.py`，实现 anchor、changed files、逐笔行数、路径规则、禁止扩展名与生成物检查，验证：脚本在 `--help` 与空仓库场景均能正常结束并输出 JSON。
- [x] 1.2 增加 Mapper、迁移、表、OpenAPI operation 与 OpenSpec 状态统计，验证：在当前仓库输出数字与 9/36/24/25 一致，CLI 不可用时标记 unknown。

## 2. 测试

- [x] 2.1 为纯函数与 diff 解析增加 unittest，覆盖路径通配、单笔行数、禁止扩展名、生成物和未知 CLI，验证：`python3 -m unittest discover -s tests` 通过。
- [x] 2.2 在当前仓库运行脚本并与 `git diff --stat`、`openspec validate` 实际结果交叉核对，验证：脚本不写文件，输出可复现。

## 3. 流程文档

- [x] 3.1 更新 `docs/agents.md`，写入审计输入包、两段式执行、阻塞/建议/无法判定分级和禁止临时文件，验证：模板与脚本参数一一对应。
- [x] 3.2 更新 `.codebuddy/agents/delivery-auditor.md`，要求先读脚本证据、禁止手工猜数，验证：prompt 中包含脚本命令与输出分级。

## 4. 交付

- [x] 4.1 写 `completion.md` 记录实际输出、调用方验证命令与未验证项，验证：文件覆盖交付记录模板。

# 交付审计证据

只读脚本：从 Git、源码和 OpenSpec 收集审计事实，向 stdout 输出 JSON，不写文件。

## 用法

```bash
# 提交前审计 staged diff
python3 code/tools/audit-delivery/audit_delivery.py --anchor main --staged \
  --change <change-id> --allow 'openspec/changes/<change-id>/**'

# 提交后审计整条分支
python3 code/tools/audit-delivery/audit_delivery.py --anchor main \
  --change <change-id> --allow 'openspec/changes/<change-id>/**'
```

常用参数：

- `--allow`：允许路径，可重复；`dir/**` 表示目录下全部文件。
- `--forbid-ext`：禁止扩展名；不传时默认 Java/SQL/Vue/TS/CSS。
- `--generated`：生成物路径，命中时报告并从提交行数中扣除。
- `--no-openspec`：跳过 OpenSpec 查询。

退出码：`0` 无阻塞，`1` 有阻塞，`2` 证据生成失败。

## 边界

- 只读取 staged diff 或 `anchor..HEAD`，不读取 unstaged/untracked 文件。
- 不运行构建、测试、数据库或应用。
- 只给事实，不给架构结论。

## 测试

```bash
cd code/tools/audit-delivery && python3 -m unittest discover -s tests
```

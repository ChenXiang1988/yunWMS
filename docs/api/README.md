# 接口产物

本目录回答两个问题：**一共有多少个接口**、**每个接口给前端返回了什么数据**。

前后端分离的项目里，这两个问题的答案不能靠人记，也不能靠手抄的表格——手抄的表格必然腐坏，而腐坏时没有机制能发现。所以本目录只有生成物，没有手写内容。

## 两份产物

| 文件 | 性质 | 谁读 | 能否手改 |
| --- | --- | --- | --- |
| `openapi.json` | 机器源，springdoc 输出的原文 | 脚本、CI | 由脚本覆盖 |
| `inventory.md` | 人读台账 | 人、前端子 Agent | **禁止** |

本文件（`README.md`）是说明，不属于产物。

真源是代码里的 `@RequestMapping` 与 DTO，这两个文件都是派生物：

```
代码 → /v3/api-docs → openapi.json → inventory.md
```

## 生成

```bash
# 后端已启动：实时拉取，同时刷新 openapi.json
python3 code/tools/api-inventory/gen_api_inventory.py --source-commit <含这些接口的 commit>

# 后端起不来：从已有 json 重新生成台账，头部会标注「文件快照」
python3 code/tools/api-inventory/gen_api_inventory.py --from-file docs/api/openapi.json \
        --source-commit <commit> --check
```

**`--source-commit` 必须显式给出。** 台账描述的是提供 spec 的那版代码，不一定等于写台账所在分支的 HEAD。不写会让读者定位到错的版本。

工具细节见 `code/tools/api-inventory/README.md`。

## 何时必须重跑

- 任何新增、删除、修改接口的提交
- 任何改动 DTO 字段的提交
- change 归档前

代码改了但没人重跑，台账会停留在旧版本，而且**看起来完全正常**——这是本机制唯一无法自证的地方，只能靠纪律。

## 当前状态

当前台账的源 commit 为 `fe45410`，共 **27 个 operation**（GET 10 / POST 4 / PUT 11 / DELETE 2）。

2026-10-27 已在隔离数据库和本地实例上重新生成。请求体中的 `ownerId`、`roleIds`、`menuIds`、`ownerIds` 按字符串传输；路径参数仍按 OpenAPI `integer(int64)` 描述。角色与用户各新增一条数据范围配置端点，详情回显 `dataScopeType/ownerIds`。下次任何接口或 DTO 变更后仍必须按上文重新生成并更新源 commit。

## 前端子 Agent 的取数边界

前端实现只允许读本目录，**不得阅读 `code/backend/**`**。读了就会耦合到 DTO 的内部字段与 Service 方法，前后端分离只剩目录形态。

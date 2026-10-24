# 接口台账生成工具

从 OpenAPI 文档生成 `docs/api/inventory.md`，回答两个问题：

- 一共有多少个接口
- 每个接口给前端返回了什么数据

**这不是产品代码**，是文档工具。产物供前端与验收使用，不参与运行时。

## 真源链条

```
代码里的 @RequestMapping + DTO      ← 唯一真源
        ↓ springdoc 反射
docs/api/openapi.json               ← 机器源，可 diff
        ↓ gen_api_inventory.py
docs/api/inventory.md               ← 人读台账，生成物，禁止手改
```

手写的接口表格必然腐坏，而腐坏时没有机制能发现——前端照着过期的表格写页面，直到联调才暴露。因此台账只能生成。

## 用法

```bash
# 常规：后端已启动，实时拉取并同时写出 openapi.json
python3 code/tools/api-inventory/gen_api_inventory.py

# 降级：后端起不来时，从已保存的 json 生成，台账会标注为「文件快照」
python3 code/tools/api-inventory/gen_api_inventory.py --from-file docs/api/openapi.json

# 校验：CI 或提交前确认台账与源一致，不一致返回 1
python3 code/tools/api-inventory/gen_api_inventory.py --from-file docs/api/openapi.json --check
```

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `--from-url` | `http://localhost:8080/v3/api-docs` | 从运行中的服务拉取 |
| `--from-file` | 无 | 读已保存的 json，与 `--from-url` 互斥 |
| `--out` | `docs/api/inventory.md` | 台账输出路径 |
| `--json-out` | `docs/api/openapi.json` | 仅在实时拉取时写出 |
| `--prefix` | `/api/` | 只收录该前缀，过滤 `/error`、`/actuator/*` 等框架路径 |
| `--timeout` | `10` | 拉取超时秒数 |
| `--source-commit` | 当前 HEAD | 覆盖台账头部的源 commit；跨分支生成时必填 |
| `--check` | 关 | 只校验不写文件 |

`--source-commit` 存在的原因：台账描述的是**提供 spec 的那版代码**，不一定是写台账所在分支的 HEAD。两者不一致时，头部 commit 会把人定位到错的版本。

退出码：`0` 成功 / `1` 台账与源不一致（含文件不存在）/ `2` 输入不可用（文件缺失、JSON 非法、顶层缺 `paths`）。

## 计数口径

按 **OpenAPI operation（path × method）** 计，不按 Java 方法数：

- 一个 `@PostMapping({"a","b"})` 产出两个 operation
- `GET /api/owners` 与 `POST /api/owners/search` 是两条不同的接口

所以「接口总数」由脚本数出来，不能靠人看注解数。

## 字段记号

| 记号 | 含义 |
| --- | --- |
| `a.b` | 嵌套字段 |
| `a[]` | 数组元素字段 |
| `↻` | 自引用，已截断不再下钻 |
| `…` | 嵌套超过 4 层，已截断 |

台账只展开 `data` 内部，`ApiResult` 的外壳在「统一响应外壳」一节说明一次。只写「返回 ApiResult」等于没说，前端真正要的是 `T`。

## 为什么输出里没有生成时间

生成时间每次运行都在变，会让每次重跑都产生无意义 diff，「台账变了」这个信号就失效了。定位版本用头部记录的源 commit。

## 已知局限

- `--check` 只能确认 `inventory.md` 与 `openapi.json` 一致，**不能确认 `openapi.json` 是否与当前代码一致**——后者需要启动服务拉取，CI 里做不到。
- `openapi.json` 由实时拉取产生，若代码已改但没人重跑，台账会停留在旧版本且看起来正常。收口时必须重跑一次。

## 测试

```bash
cd code/tools/api-inventory && python3 -m unittest discover -s tests
```

测试只依赖 `fixtures/`，不启动后端。

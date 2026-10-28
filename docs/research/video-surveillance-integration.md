# 视频监控服务商（货查查）接口调研

- 调研时间：2026-10-27
- 时效：本文为 2026-10-27 的快照，记录当时状态；后续变化以仓库现状为准，本文不回改。
- 来源资料：`https://s.apifox.cn/fe518f03-5f58-4bfe-9321-031ed2024e95`（Apifox 在线文档，文档最后修改时间 2026-06-15 21:56:58）
- 抓取方式：该分享站点为 SSR 渲染，每页均可通过「页面 ID + `.md` 后缀」取得原始正文与 OpenAPI 3.0.1 定义，例如 `.../fe518f03-5f58-4bfe-9321-031ed2024e95/294280743e0.md`。本文所有字段取自这些 `.md` 原文，未做推断补全。
- 结论状态：**接口已收集完毕，方案未定**。尚未绑定 OpenSpec change，未进入实现，未与现有模块做任何代码改动。

## 1. 服务商与接入形态

| 项目 | 内容 |
| --- | --- |
| 文档标题 | 货查查 OpenAPI 对接接口文档 |
| 产品名 | 货查查开放API |
| 网关域名 | `https://zhanhuatech.net`（文档中 Base URL 写作 `https://zhanhuatech.net/api`） |
| 文档侧 Apifox 项目 ID | 6367388（来自各接口 `x-run-in-apifox` 链接） |
| 协议 | 全部 `POST`，请求体 `application/json`，响应 JSON |
| 接口数量 | 在用 11 个；另 2 个 v1 接口标记 `@Deprecated` 仍可用 |
| 版本策略 | v1 已弃用；v2 / v3 并行，差异集中在时间处理（见 §4.1） |

**产品定位**：仓内扫码工位 + 摄像头录像的绑定服务。第三方系统在扫码时把「运单号 / 子单号 / 扫码时间 / 工位」推给货查查，货查查按工位关联的摄像头回溯录像、裁剪合成 MP4，并可在视频上叠加动态水印。数据模型只有三层：**company（企业）→ station（工位）→ goods（扫码订单，含 subGoods 子单）**。

## 2. 鉴权与通用约定

### 2.1 鉴权

- 方式：请求头 `APIKEY: <企业密钥>`，一个企业一个固定密钥，由服务商后台分配、可在其公司管理页面查看/重置。
- 校验链路（服务商自述）：Redis 缓存 5 小时 → 未命中查 `company.apikey` → 密钥不存在返回 `403 授权异常` → 企业禁用（`open=false`）返回 `403 apikey过期或无权限`。
- 缓存副作用：**企业新增或密钥重置后最长 5 小时才生效**，必要时需联系服务商运维刷新。
- 企业被禁用后密钥立即失效。

### 2.2 全局响应结构

```json
{ "code": 200, "msg": "ok", "data": { } }
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | Integer | 200 成功 / 400 参数错误 / 401 未认证 / 403 无权限 |
| msg | String | 提示信息，错误原因只在这里，无独立错误码表 |
| data | T | 业务数据，失败时为 null |

**关键约束**：文档明确「所有响应 HTTP 状态码均为 200」，必须解析 body 内的 `code` 判成功。常规 HTTP 客户端按状态码重试/熔断的机制在这里全部失效。

### 2.3 时间与分页

| 项 | 约定 |
| --- | --- |
| 时间格式 | `yyyy-MM-dd HH:mm:ss.SSS`，毫秒固定 3 位，不足补零；格式错误返回 `400 请求时间参数格式错误` |
| 例外 | 已弃用的 `/api/v1/video/select` 使用秒级 `yyyy-MM-dd HH:mm:ss` |
| 响应时间格式 | `orderSelect` 系列返回的是 ISO 风格 `2024-11-23T13:08:48.044`，与入参格式**不一致** |
| 时区 | 文档全文未提时区，时间无偏移量标识 |
| 分页上限 | `orderSelect`(v2)、`stationStatistics` 的 `size` 最大 100，超出强制截断为 100 |
| 结果条数上限 | `subGoodsQuery` 最多返回 10 条关联订单 |

### 2.4 数据隔离

所有接口按 `APIKEY` 所属企业隔离，跨企业不可见；`uid` 必须是本企业已存在的用户，`station_id` 必须是本企业工位，否则 400。

## 3. 接口清单

| # | 方法 | 路径 | 名称 | 用途 |
| --- | --- | --- | --- | --- |
| 1 | POST | `/api/v2/third/dataReceive` | 订单添加（实时） | 推送扫码订单，服务端做时间偏差校准 |
| 2 | POST | `/api/v3/third/dataReceive` | 订单添加（自定义时间） | 同上，但不做时间校准，按传入原始时间存储 |
| 3 | POST | `/api/v2/third/renderByGid` | 导出视频 | 按订单内部 ID 触发录像合成，返回 videoId，异步 |
| 4 | POST | `/api/v2/third/findVideo` | 查询视频 | 轮询渲染状态，state=6 时返回签名下载 URL |
| 5 | POST | `/api/v2/third/orderSelect` | 订单查询 | 分页查询本企业扫码订单，支持排序 |
| 6 | POST | `/api/v3/third/orderSelect` | 订单查询（指定单号） | 按单号数组批量查询，不分页 |
| 7 | POST | `/api/v2/third/subGoodsQuery` | 子订单查询 | 按子单号/条码反查父订单 |
| 8 | POST | `/api/v2/third/goodsExtInfo` | 抓图/重量查询 | 返回抓图 COS 直链与秤重值 |
| 9 | POST | `/api/v2/third/stationStatistics` | 统计数据-工位 | 按时间段统计各工位扫码量 |
| 10 | POST | `/api/v2/third/stationSelect` | 查询工位列表 | 查询本企业工位（无入参） |
| 11 | POST | `/api/v2/third/stationCreate` | 创建工位 | 本企业下新建工位，校验 IP/MAC 与套餐额度 |

**已弃用（仍可用）**

| 路径 | 弃用说明 | 替代 |
| --- | --- | --- |
| `/api/v1/video/renderByErpGood` | v1 导入+渲染一步完成，缺 `error` 与 `enable` 字段 | `/api/v2/third/dataReceive` + `/api/v2/third/renderByGid` |
| `/api/v1/video/select` | 查询参数方式，功能有限，秒级时间格式 | 无直接替代，仍在使用 |

> 文档自身不一致：总览页「接口列表」只有 9 个接口，漏了 `stationSelect`、`stationCreate`（这两个只出现在侧边栏与 `LLMs.txt`）。本文按 11 个收录。

## 4. 接口明细

### 4.1 订单添加 v2 / v3 — `POST /api/v2|v3/third/dataReceive`

请求体（v2 与 v3 完全一致）：

```json
{
  "uniqueCode": "SF1234567890",
  "uid": "user_001",
  "station_id": 10,
  "startTime": "2026-06-16 08:30:00.000",
  "endTime": "2026-06-16 08:30:10.000",
  "goods": [
    { "id": "barCode_001", "scanTime": "2026-06-16 08:30:02.000", "error": false }
  ],
  "headers": [
    { "title": "操作员", "value": "SH001", "enable": true }
  ]
}
```

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| uniqueCode | String | 是 | 订单号 / 包裹唯一码 / 运单号 |
| uid | String | 是 | 操作员用户号，必须是本企业已存在用户 |
| station_id | Integer | 是 | 货查查系统内的工位 ID |
| startTime | String | 是 | 扫码开始时间，`yyyy-MM-dd HH:mm:ss.SSS` |
| endTime | String | 是 | 扫码结束时间，格式同上 |
| goods[].id | String | 是 | 商品 id（子单号/条码） |
| goods[].scanTime | String | 是 | 子单扫码时间 |
| goods[].error | Boolean | 是（OpenAPI 定义） | 是否异常件；总览页参数表写作「否」 |
| headers[].title | String | 是（OpenAPI 定义） | 动态水印标题；总览页写作「否」 |
| headers[].value | String | 是（OpenAPI 定义） | 动态水印值 |
| headers[].enable | Boolean | 是（OpenAPI 定义） | 是否显示在视频画面中 |

响应：

```json
{ "code": 200, "msg": "导入订单成功", "data": { "goodsId": 1028661, "videoId": null } }
```

`data.goodsId` = 订单内部数字 ID（数据库自增主键），后续 `renderByGid` / `goodsExtInfo` 都用它。`data.videoId` 在 v2/v3 恒为 null。

错误码：`400 用户不存在`、`400 该用户不为本企业的,请使用本企业的用户uid`、`400 工位ID错误`。

v2 / v3 差异：

| 方面 | v2（实时） | v3（自定义时间） |
| --- | --- | --- |
| 存储 scanTime | `startTime + offset`，offset = 传入 `endTime` 与服务器当前时间的差 | 传入的 `scanTime` |
| 存储 endTime | 服务器当前时间 | 传入的 `endTime` |
| 适用 | 设备时间不准，由服务端拉齐 | 设备已 NTP 同步 |

**v2 的本质是「以服务器时间为锚点平移整段扫码时间」**，不是逐条校准。同一批数据从 v2 换到 v3，落库时间会整体偏移，历史数据不可混用两种版本。

### 4.2 导出视频 — `POST /api/v2/third/renderByGid`

请求：`{ "goodsId": 1028657 }`，`goodsId` 为订单内部数字 ID。

响应：`{ "code": 200, "msg": "导出成功", "data": { "goodsId": 1028657, "videoId": 58012 } }`

| 错误 msg | 原因 |
| --- | --- |
| 没有该商品 | goodsId 不存在或不属于本企业 |
| 已经导出请勿重复操作 | 该订单已有导出任务（一个订单只能触发一次） |
| 该工位暂未绑定通道 | 工位未关联摄像头 |
| 请为录像机绑定设备 | 摄像头未配置导出设备 |

约束：异步；视频最长 1 小时，超长只取前 1 小时；起止时间 = 订单时间 ± 工位配置的回溯秒数（`backtrack1` / `backtrack2`，回溯值在服务商侧配置，接口不可读不可改；文档字段名 `backtrack1`/`backtrack2` 与语义「开始 -backtrack1 / 结束 +backtrack2」的对应关系未说明）。

**不可重试**：重复触发直接报错，这使「导出失败后重试」没有接口可用。

### 4.3 查询视频 — `POST /api/v2/third/findVideo`

请求：`{ "videoId": 58012 }`

成功响应：`data = { videoId, url, state: 6 }`，`url` 为带签名的 COS 直链，**有效期 1 小时**，过期需重新查询。

渲染中响应：`code: 400, msg: "视频暂未导出成功", data: { videoId, url: null, state: 3 }`（注意：未完成时仍返回 400，`data` 不为 null，与「失败 data 为 null」的全局约定冲突）。

**state 语义在文档内有两处互相矛盾的定义：**

| 来源 | 0 | 1 | 2-5 | 3 | 6 |
| --- | --- | --- | --- | --- | --- |
| 总览页 | 等待中 | 处理中 | 1-5 渲染中 | 渲染中 | 完成 |
| findVideo 页 description | 渲染队列中 | 渲染中 | — | **渲染错误** | 导出成功 |

必须向服务商确认 3 到底是「渲染中」还是「终态错误」——若是终态错误，轮询逻辑必须能识别并终止，否则会无限等 6。2/4/5 是否存在同样待确认。

其他：文档注明「每次成功调用会记录视频文件大小的流量消耗」，即查询行为本身计入流量计费，轮询频率需克制（文档建议 5-10 秒/次）。

### 4.4 订单查询（分页）— `POST /api/v2/third/orderSelect`

请求：`{ "page": 1, "size": 20, "sortBy": "scanTime", "desc": true }`

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| page | Integer | 否 | 默认 1 |
| size | Integer | 否 | 默认 10，最大 100 |
| sortBy | String | 否 | 驼峰字段名，默认 `id`，服务端自动转下划线 |
| desc | Boolean | 否 | 默认 true |

响应 `data`：`records[]`、`total`、`current`、`size`、`hasNext`、`hasPrevious`。

| records[] 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 订单内部 ID |
| goodsId | String | **运单号/包裹唯一码**（与 id 不是一回事） |
| startTime / endTime | LocalDateTime | 订单起止时间，示例中常为 null |
| scanTime | LocalDateTime | 扫码时间 |
| userId | String | 操作员 |
| stationName | String | 工位名称（只有名称，无 ID） |
| videos | Array | `[{videoId, url, state}]`，其中 `url` 恒为 null，须再调 findVideo |

注意：响应里没有工位 ID、没有货主/仓库维度、没有抓图与重量；示例中同一 `goodsId` 会出现多条记录（同一包裹多次扫码 → 多条订单）。

### 4.5 订单查询（指定单号）— `POST /api/v3/third/orderSelect`

请求体是**裸 JSON 数组**，不是对象：`["6903244675147", "SF1529553156094"]`

文档给的检索规则：

- 单号个数 > 1 → 精准匹配
- 单号个数 = 1 且长度 > 10 → 前缀模糊匹配 `(xxx%)`

响应 `data` 为数组，字段同上（含 videos）。不存在的单号静默忽略，不报错。

**该接口入参语义是关键分歧点**：总览页在接口列表里称「按 ID 批量查询」，同页注意事项又称「v3 的 IDs 是 `goodsId` 字段（业务编码），不是数据库主键」；而 4.5 页面示例里返回 `id: 7245370` / `goodsId: '6903244675147'`，配合「单号」描述，可判定入参是**包裹编码**而非内部主键。同一节文字自相矛盾，需服务商书面确认。

### 4.6 子订单查询 — `POST /api/v2/third/subGoodsQuery`

请求：`{ "subGoodsId": "2332#194721521200" }`，示例值形如 `订单号#序号`。

响应 `data` 为数组，元素含父订单字段 + `subGoods[]`（`id`、`subGoodsId`、`scanTime`、`goodsId`、`error`）+ `videos[]`（此处的类型在 OpenAPI 里定义为 **string 数组**，与其他接口的 `{videoId,url,state}` 对象数组不一致）。

错误：`400 子订单号不能为空`、`400 未找到匹配的子订单数据`。最多 10 条，同一父订单多子单命中自动去重，搜索范围为全企业数据。

### 4.7 抓图/重量查询 — `POST /api/v2/third/goodsExtInfo`

请求：`{ "id": 4903053 }`（订单内部数字 ID，字段名叫 `id` 不叫 `goodsId`）

响应：`data = { id, photos: string[], weight: string|null }`

- `photos` 为腾讯云 COS 完整 URL，文档称「公有读，可直接在浏览器打开」，**无签名、无有效期**。
- `weight` 为字符串，来源是 `header` 表中 `title` 为 **「秤重」** 的记录；无数据时为 null。
- 错误：`400 参数错误`、`400 没有该商品`。

### 4.8 工位扫码统计 — `POST /api/v2/third/stationStatistics`

请求：`{ "page": 1, "size": 100, "startTime": "2024-11-04 00:00:00.000", "endTime": "2025-11-05 00:00:00.000" }`，四个字段全部必填。

响应 `data.records[] = { id: String, name: String, count: Integer }` + 分页字段。`id` 为字符串型工位 ID，`count` 为扫码订单数。

### 4.9 查询工位列表 — `POST /api/v2/third/stationSelect`

无请求体（文档明确「请求无需 Body」）。

响应 `data` 为数组：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Integer | 工位 ID（`dataReceive` 的 `station_id` 取自这里） |
| stationName | String | 工位名称，示例值形如 `IP：192.168.137.1`（自动生成） |
| stationType | String | 工位类型，示例值 `快递单号`（枚举未列出） |
| stationIp | String | 工位 IP |
| stationMac | String | 工位 MAC |
| enable | Boolean | 是否启用 |
| scan | Boolean | 是否启用扫码端 |

`dataReceive` 必需的 `station_id` 只能从这里取，**没有按名称查询的接口**，也不能改、不能删。

### 4.10 创建工位 — `POST /api/v2/third/stationCreate`

请求：`stationIp`（IPv4）、`stationMac`（冒号分隔）、`stationName`，三者必填。

响应：`data = { id, scanStationCount, stationLimit }`，`stationLimit` 为套餐授权工位数，`-1` 表示不限量。

错误：`400`（IP 为空 / IP 格式错 / MAC 为空 / MAC 格式错 / 名称不能为空 / IP 或 MAC 已存在 / 授权扫码工位已达上限 / 当前公司未配置有效工位套餐）、`401`（缺少或无效 APIKEY）、`403`（APIKEY 已禁用或无权限）。

创建后工位类型等属性不可通过接口设置。

### 4.11 文档未覆盖的接口

以下能力**没有对应接口**，只能到服务商后台操作：

- 用户（`uid`）的创建、查询、停用 —— 而 `uid` 是 `dataReceive` 的必填项
- 工位的修改、删除
- 工位与摄像头的绑定、回溯秒数（`backtrack1`/`backtrack2`）配置 —— 却是视频能否导出的前提
- 订单/视频的删除或作废
- 视频渲染完成的回调 —— 只有轮询

## 5. 对接时最容易踩的坑（按危险程度排序）

1. **`goodsId` 一名四义**。同一个词在四处指不同东西：

   | 出现位置 | 实际语义 | 类型 |
   | --- | --- | --- |
   | `dataReceive` 响应 `data.goodsId` | 订单内部主键 | Long |
   | `orderSelect` 记录里的 `goodsId` | 运单号/包裹码 | String |
   | `v3 orderSelect` 入参数组元素 | 包裹码（待确认） | String |
   | `goodsExtInfo` 入参 `id` | 订单内部主键 | Long |

   本地必须立刻把「内部 ID / 包裹码 / 子单号」拆成三个字段落库，否则后期必然写错参数。

2. **`state` 的 3 无定论**（见 4.3）。轮询终止条件不能只判 `== 6`。

3. **无回调、无幂等键对外暴露**。`dataReceive` 重复推送同一 `uniqueCode` 的行为文档未定义；`orderSelect` 示例暴露出同一包裹存在多条记录。本地要自己定「同一包裹用哪条记录取视频」。

4. **`renderByGid` 不可重复调用**。失败即无重试路径，只能靠服务商后台。本地需要有失败态与人工兜底。

5. **时间无时区**。服务端时间锚定（v2）、`yyyy-MM-dd HH:mm:ss.SSS` 无偏移量，跨时区仓（海外仓）会直接错位。

6. **抓图是公开可读直链**。COS 公有读意味着任何拿到 URL 的人都能长期访问，与视频 URL 的 1 小时签名不同。

7. **HTTP 恒 200**。网关/负载层的健康检查、重试策略不能依赖状态码。

8. **基路径重复**。Base URL 写 `/api`，路径也以 `/api/v2` 开头，实际拼装以 `https://zhanhuatech.net` + 接口路径为准，避免出现 `/api/api/v2`。

9. **`subGoodsQuery` 的 `videos` 类型是字符串数组**，与其他接口的对象数组不一致，反序列化要分开建模。

## 6. 与 yunWMS 的落点分析（仅列候选，未定方案）

现状：`code/backend` 下现有 `wms-app`、`wms-auth`、`wms-master`、`wms-web` 四个模块，全仓库检索 `video` / `监控` / `录像` / `camera` 无任何命中，属全新功能域。

服务商模型与 WMS 模型的错位：

| 维度 | 货查查 | yunWMS |
| --- | --- | --- |
| 隔离单位 | company（一个 APIKEY 一家企业） | 单租户部署，但业务上分货主与仓库 |
| 核心对象 | 扫码事件（一次扫码一条 goods） | 出库单 / 包裹 / 任务 / 复核记录 |
| 工位 | 唯一实体，绑定摄像头 | 无「工位」主数据，最接近的是作业台/打包台概念 |
| 时间 | 服务端锚定，无时区 | 需明确时区口径 |
| 标识 | `uniqueCode` + 子单号 | 运单号、包裹码、出库单号、任务号 |

可确定的约束（不涉及业务口径）：

- 本项目单租户私有部署，一个企业维度即可容纳全部仓，不需要按仓拆多个 APIKEY。
- 服务商侧没有货主字段，**唯一能把货主/仓库信息带到视频画面上的通道是 `headers` 动态水印**；若要求「按货主回查录像」，则必须在 `headers` 里写入货主标识，同时本地建立「包裹码 → 货主/仓库/单据」映射。
- `uid` 与 WMS 操作员账号之间需要一张映射表（服务商不提供用户接口，只能先在后台建人）。
- `station_id` 与 WMS 的作业台/打包位之间需要映射，且只能在服务商后台建工位、配摄像头。
- 「导出视频」的触发时机（复核完成即导 / 事后按需导）决定是否需要本地任务表与轮询调度；服务商无回调，轮询是唯一手段。

结论：**按接口能力看可以直接对接，不需要改造上游**；但落点（挂在复核打包、还是做成独立的视频查询页）、触发方式（自动/按需）、以及是否强制水印货主，属于业务口径，未确认前不进入实现。

## 7. 待确认清单

### 7.1 问服务商（接口口径，必须先闭环）

1. `findVideo` 的 `state=3` 是「渲染中」还是「渲染失败终态」？2/4/5 是否会出现？
2. `v3 orderSelect` 入参数组元素到底是包裹编码还是订单内部主键？文档两处表述相反。
3. `dataReceive` 重复推送同一 `uniqueCode` 是新增记录、覆盖还是报错？同一包裹多次扫码产生的多条记录，取视频时应选哪条？
4. `goods[].id` 与 `subGoodsQuery` 的 `subGoodsId` 是否为同一值？`subGoodsId` 的 `订单号#序号` 格式是强制还是仅示例？
5. `headers[].title` 有哪些保留值？文档一处写「重量」、另一处写 `weight` 取自 title 为「秤重」的记录。
6. `headers`、`goods[].error` 在 OpenAPI 定义里是必填，总览参数表写选填，以哪个为准？
7. `renderByGid` 失败后有无重试或重新导出途径？视频文件保留多久？
8. 视频流量计费口径：计费是按 `findVideo` 调用还是按实际下载？重复查询是否重复计费？
9. 时间字段的时区与夏令时处理；v2 的 offset 在跨天、跨时区场景如何计算。
10. `backtrack1` / `backtrack2` 分别对应「开始前回溯」还是「结束后延伸」，默认值多少。
11. 抓图 COS 公有读是否为长期策略，能否改成签名 URL 或私有读。
12. 是否有渲染完成回调、批量导出、按时间范围检索的规定，还是只能按单号/分页拉。
13. 密钥重置后 5 小时缓存，能否提供即时刷新方式。
14. 服务商侧数据保留期与清理策略（订单、视频、抓图、重量）。

### 7.2 问业务（口径，决定要不要做）

1. 视频监控的落点是「复核打包」环节，还是独立页面按运单/出库单查询？
2. 导出是作业时自动触发，还是事后人工按需触发？
3. 是否要求视频画面带水印，水印需要出现哪些字段（货主、仓库、操作员、单号）？
4. 操作员账号是否需要在货查查后台单独维护，谁是维护责任人？
5. 抓图（照片）与秤重是否进 WMS 业务数据（例如作为复核留证或计费依据）？

## 8. 非目标与下一步

**本文不做**：

- 不做候选方案比较（R3）与选型结论（R4）——本次只到「能力盘点」，因为只有一家指定服务商，不存在多方案比选。
- 不设计 WMS 侧表结构、不建 change、不改代码。
- 不推测服务商未公开的能力（如摄像头管理、录像回放、AI 识别）。

**下一步**：§7.1 的 14 项拿到书面答复后，再按 R1（需求范围）与 §7.2 的业务口径决定是否开 change。

# yunWMS 开源方案调研与技术路线建议

- 调研时间：2026-10-22
- 数据来源：GitHub REST API（star、最近推送、许可证以接口返回为准）、项目 README 与公开介绍
- 结论状态：前提已确认（2026-10-22），结论可执行
- 已确认前提：Java / Spring Boot；多货主 3PL；不接受商业授权预算

## 1. 需求要点

工作区名为 yunWMS，尚无需求文档与代码。以下为基于“云端仓储管理系统”定位的推断草案，**逐条待确认**。

### 1.1 功能范围

| 域 | 内容 |
| --- | --- |
| 仓库主数据 | 仓库、库区、货位、货主、承运商、供应商、客户 |
| 入库 | ASN/预约、收货、质检、上架、异常处理 |
| 库内 | 移库、调拨、盘点（明盘/盲盘/循环盘点）、补货 |
| 出库 | 订单/发货单、库存分配、波次、拣货、复核打包、发货 |
| 库存 | 多口径数量（实存/良品/不良品/可用/在途/预占/占用/圈货/备货/冻结/安全库存）、批次与效期、序列号、库存流水 |
| 任务中心 | 任务生成、派工、执行、进度监控、异常上报 |
| 终端 | Web 后台、PDA 扫码作业、打印（面单/标签/单据） |

### 1.2 关键特征

- 库存准确性依赖永续库存 + 不可变流水，不能只做余额表更新
- 并发扣减与预占/占用分离，需要明确锁策略与幂等
- 作业策略可配置：上架策略、分配策略、拣货策略、波次规则
- 多仓与多货主的数据隔离是模型层问题，不是过滤条件问题
- PDA 弱网、离线重试、扫码防重

### 1.3 边界（待确认）

本期是否包含：自动化设备对接（AGV/输送线/电子标签）、计费结算、跨境报关、生产工单联动、ERP 双向同步。

### 1.4 现存约束

- 技术栈已定：Java / Spring Boot，非 Java 候选（Odoo、ERPNext、GreaterWMS、InvenTree）不再具备基线资格
- 业务形态已定：多货主 3PL，货主维度必须进入数据模型，不能作为后期过滤条件补丁
- 预算已定：不接受商业授权，许可证成为**一票否决项**，GPL/AGPL/SSPL 依赖需逐项排除
- 代码尚不存在，无历史包袱

## 2. 检索范围与方法

检索词覆盖三类：

- 完整同类系统：`open source WMS`、`开源 WMS 仓储管理系统`
- 中文 Java 系：`若依 WMS`、`Spring Boot 仓储管理系统`
- 可复用模块：`warehouse task execution`、`wave picking module`、`WES`

按 star 初筛后逐个核对最近推送时间与许可证原文，未采信二手文章中的许可证描述（实测出现冲突）。

## 3. 候选清单

数据采集于 2026-10-22。

| 项目 | Star | 最近推送 | 许可证 | 技术栈 | 定位 |
| --- | --- | --- | --- | --- | --- |
| GreaterWMS/GreaterWMS | 4413 | 2026-10-13 | Apache-2.0 | Python Django + Quasar(Vue2) | 售后物流 WMS，含波次、合并拣货、Milk-Run、PDA |
| fjykTec/ModernWMS | 1788 | 2025-06-22 | Apache-2.0 | .NET 7 + Vue3 | 中小企业轻量 WMS |
| zccbbg/wms-ruoyi | 1224 | 2026-03-20 | 改版 Apache-2.0（附加限制） | Java Spring Boot + Vue2/Element | 通用 WMS，多仓多货主 |
| openwms/org.openwms | 753 | 2026-07-13 | Apache-2.0 | Java Spring Boot 微服务 | 集成商内核，模块化拆分 |
| inventree/InvenTree | 7742 | 2026-10-22 | MIT | Python Django | 物料/零部件库存 |
| openboxes/openboxes | 917 | 2026-10-21 | EPL-1.0 | Groovy/Grails | 医疗供应链物资 |
| frappe/erpnext | 40442 | 2026-10-22 | GPL-3.0 | Python Frappe | ERP 含 Stock 模块 |
| odoo/odoo | 55239 | 2026-10-22 | LGPL-3.0（社区版） | Python | ERP 含 Inventory |
| frePPLe/frepple | 767 | 2026-10-21 | MIT（双许可） | C++/Python | APS 生产与供应链计划 |
| jingsewu/open-wes | 340 | 2026-10-13 | MIT | Java | WES 设备执行层 |
| grocy/grocy | 9605 | 2026-09-16 | MIT | PHP | 家庭库存 |
| Part-DB/Part-DB-server | 1807 | 2026-10-21 | AGPL-3.0 | PHP | 元器件库存 |

## 4. 逐项比较

### 4.1 GreaterWMS

- 功能覆盖度：入库、出库、库存、多仓、波次、合并拣货、Milk-Run、PDA、报表，是本批候选中作业链最完整的一个
- 活跃度：最近推送 2026-10-13，社区活跃
- 许可证：Apache-2.0，允许商用与闭源分发
- 依赖与可维护性：Django + Quasar，前端 Vue2 已于 2023-12-31 结束官方维护，长期需自行升级到 Vue3
- 技术栈兼容性：Python 生态，若团队以 Java 为主则为全栈异构
- 二次开发成本：中。流程与数据模型可直接借鉴；多租户模型未见成熟实现，需自行改造

### 4.2 wms-ruoyi

- 功能覆盖度：仓库/库区/货架、出入库、移库、盘库、客户/供应商/承运商、库存看板
- 活跃度：最近推送 2026-03-20，节奏放缓
- 许可证：**关键风险**。许可证原文为修改版 Apache-2.0，附加两条：未获书面授权不得用于多租户环境运营；不得移除前端 LOGO 与版权信息，且交互设计受外观专利保护
- 兼容性：Java Spring Boot + Vue2/Element，国内生态熟悉
- 二次开发成本：低（技术栈）但法律成本高（多租户 SaaS 需商业授权）

### 4.3 openwms/org.openwms

- 功能覆盖度：拆分为 Common（库位/物料）、TMS（运输任务/路由）、Receiving、Movements、Preferences、UAA 等独立服务，边界清晰
- 活跃度：主仓 2026-07 有推送，但子模块 star 普遍 0-5，Receiving、Transportation 自 2025-03 后停更
- 许可证：Apache-2.0
- 可维护性：依赖 Spring Cloud 体系，模块多、组装成本高，开箱不可用
- 二次开发成本：高。适合作为**架构与领域边界参考**，不适合作为产品基线

### 4.4 InvenTree / OpenBoxes / Grocy / Part-DB

定位均非作业型 WMS：InvenTree 与 Part-DB 面向物料/元器件库存，Grocy 面向家庭，OpenBoxes 面向医疗物资且技术栈 Groovy/Grails 冷门。均不能满足波次、任务中心、PDA 作业链需求。

### 4.5 ERPNext / Odoo

- 功能：库存核算与批次管理成熟，仓储作业深度（波次、任务派工、PDA 作业流）偏弱，且与 ERP 其余模块强耦合
- 许可证：ERPNext 为 GPL-3.0，闭源分发与 SaaS 化需谨慎评估；Odoo 社区版 LGPL-3.0，改动核心需回馈
- 适用：如果目标是“ERP + 库存”而非“作业型 WMS”，二者优于其他候选；作为纯 WMS 基座则负担过重

### 4.6 frePPLe / open-wes

- frePPLe：MIT，强项是需求预测、生产排程、供应计划，不覆盖仓内作业
- open-wes：MIT，定位于 WMS 与设备之间的执行层（任务下发、设备调度），可作为后续设备对接的参考实现

## 5. 关键判断

1. **没有可直接复用的完整方案。** 作业全链路（ASN→上架→波次→拣货→复核→发货）最完整的是 GreaterWMS，但它是 Python/Django，与已定技术栈冲突；Java 侧唯一的 Apache-2.0 完整 WMS 是 openwms，且已部分停更。
2. **许可证是硬门槛，不是细节。** wms-ruoyi 的多租户禁令写在 LICENSE 原文；JeeWMS 与 XXL-Job 为 GPL-3.0；ERPNext 为 GPL-3.0、Part-DB 为 AGPL-3.0。零预算前提下这些均为一票否决。
3. **star 与实际可用度弱相关。** star 最高的 Odoo、ERPNext 解决的是 ERP 问题；最贴合需求的 openwms 子模块 star 仅个位数且部分停更。
4. **可复用的层级应下沉。** 值得复用的是领域模型与流程编排（库存口径设计、任务状态机、波次规则），而不是整套代码。

## 6. 推荐方案（已按 Java + 多货主 3PL + 零授权收敛）

### 6.1 结论

**自行实现。** 不采用任何现有项目作为代码基线，openwms 仅作模块边界参考，greaterWMS 仅作流程参考。

理由：

- Java 生态中唯一的 Apache-2.0 完整 WMS 是 openwms，但其 Receiving、Transportation 等子模块自 2025-03 停更，star 个位数，组装成本高于自研
- 多货主 3PL 的货主维度必须进入库存主键与查询拦截层，这是结构性设计，无法靠改造现成单租户项目实现
- 零授权预算下，wms-ruoyi 的多租户禁令、JeeWMS 的 GPL-3.0 直接出局，可选池实际被清空

### 6.2 被排除的候选与原因

| 项目 | 排除原因 |
| --- | --- |
| wms-ruoyi | 许可证原文禁止未经书面授权的多租户运营，且不得移除前端 LOGO，与 3PL SaaS 定位正面冲突 |
| JeeWMS | GPL-3.0，闭源分发需开源全部衍生代码 |
| Odoo / ERPNext | Python + LGPL/GPL，技术栈不符且许可证有传染性；Odoo 企业版需付费 |
| GreaterWMS | 流程最接近（波次、合并拣货、Milk-Run、PDA），但 Python/Django + Vue2 |
| ModernWMS | .NET 7，且已 16 个月无提交 |
| openwms | 技术栈匹配，但子模块停更、开箱不可用，仅保留架构参考价值 |
| OCA/wms | AGPL-3.0，且是 Odoo 模块 |
| InvenTree / Grocy / Part-DB | 定位为物料或家庭库存，非作业型 WMS |

### 6.3 技术栈与许可证核对

零预算且可能闭源分发时，许可证需逐项确认，不能只看主项目。

| 层 | 选型 | 许可证 | 说明 |
| --- | --- | --- | --- |
| 运行时 | JDK 21 | GPL-2.0 + Classpath Exception | 可闭源商用 |
| 框架 | Spring Boot 3.x | Apache-2.0 | 无风险 |
| 持久层 | MyBatis-Plus | Apache-2.0 | 无风险 |
| 数据库 | PostgreSQL 16 | PostgreSQL License | 建议不用 MySQL（GPL-2.0，闭源分发需商业授权） |
| 缓存 | Valkey | BSD-3 | Redis 自 7.4 起为 RSALv2/SSPLv1，8.x 追加 AGPLv3，非 OSI 许可，Valkey 更稳 |
| 调度 | PowerJob | Apache-2.0 | **不要用 XXL-Job，其许可证为 GPL-3.0**，是国内 Java 项目最常见的许可陷阱 |
| 工作流 | Flowable | Apache-2.0 | 可选；首版用显式状态机即可，不急于引入 |
| 认证 | Sa-Token 或 Spring Security | Apache-2.0 | 二选一 |
| 消息 | RocketMQ 或 Kafka | Apache-2.0 | MVP 可暂不引入 |
| 前端 | Vue 3 + Element Plus | MIT | 不沿用 GreaterWMS 的 Vue2 前端 |
| 脚手架 | RuoYi-Vue | MIT | 可选，仅取权限与代码生成；引入前须核查其传递依赖是否带 GPL |

结论：技术选型不存在必须付费的组件，GPL 类依赖（XXL-Job、JeeWMS）全部可替换。

### 6.4 多货主 3PL 的模型建议

- 双维度：`tenant_id` 表示系统租户，`owner_id` 表示货主。单实例部署时 tenant 固定；SaaS 化时二者均需隔离
- 货位属仓库，库存属货主，两者不能混为一谈。同一货位可存放多个货主的货
- 库存唯一键应包含 仓库 + 库位 + 货主 + SKU + 批次 + 库存状态
- 货主隔离放在数据访问层统一注入，不依赖每个查询手写条件
- 库存流水不可变，余额表由流水驱动并可重算；定期快照用于对账与性能兜底
- 计费、结算、对账报表不在 MVP 范围

### 6.5 模块划分

| 模块 | 职责 |
| --- | --- |
| wms-common | 库位、物料、计量单位、通用异常与幂等 |
| wms-master | 仓库、货主、承运商、供应商、客户 |
| wms-inbound | ASN、收货、质检、上架 |
| wms-inventory | 库存余额、流水、预占/占用/冻结、批次效期、盘点 |
| wms-outbound | 订单、分配、波次、拣货、复核打包、发货 |
| wms-task | 任务中心、派工、执行、异常上报 |
| wms-integration | ERP/平台对接、设备协议适配 |
| wms-auth | 认证授权、租户与货主上下文 |

模块边界借鉴 openwms 的 Common / TMS / Receiving / Movements 拆分，设备侧预留 open-wes 式执行抽象。

### 6.6 MVP 顺序

仓库主数据 → 库存流水与多口径余额 → 入库 → 出库（不含波次）→ 任务中心 → 波次与策略 → PDA → 设备对接。

## 7. 主要风险

| 风险 | 说明 | 缓解 |
| --- | --- | --- |
| GPL 依赖混入 | XXL-Job（GPL-3.0）是国内 Java 项目默认调度器，一旦引入即产生传染风险 | 依赖清单准入审查，调度统一用 PowerJob |
| 许可证误判 | wms-ruoyi 的多租户禁令写在 LICENSE 而非 README | 任何代码引入前读 LICENSE 原文 |
| 货主模型返工 | 若首版把 owner 当过滤条件而非主键维度，后期需重建库存表 | 首版即纳入唯一键与拦截层 |
| 库存口径返工 | 多口径数量后期追加需重算历史 | 首版建流水 + 余额 + 快照三层 |
| 并发扣减 | 3PL 多货主并发抢同一货位库存，易超卖 | 明确锁粒度与幂等键，预占与占用分离 |
| 自研范围蔓延 | 作业链域多，容易一次做全 | 严格按 MVP 顺序，波次与设备后置 |
| 上游停更 | openwms 部分模块已停更，不能作为依赖基线 | 只借鉴设计，不拉代码 |
| PDA 弱网 | 扫码作业在弱网下易重复提交 | 幂等令牌 + 本地队列重试 |

## 8. 剩余待确认事项

已确认：技术栈（Java/Spring Boot）、业务形态（多货主 3PL）、预算（零商业授权）。

仍待确认：

1. 仓数量、SKU 量级、日均单据量与峰值并发（决定是否分库分表）
2. 是否需要批次/效期/序列号管理，是否涉及温区与混放规则
3. 是否对接 PDA、打印机、AGV/输送线/电子标签
4. 是否对接上游 ERP/电商平台，单向推送还是双向同步
5. 上线时间与首期范围

以上不影响技术路线结论，但会影响模块优先级与表结构细节。未确认前不进入实现。

## 9. wms-ruoyi 授权后的复核（2026-10-22）

前提变化：已取得无限制使用授权，许可证不再构成约束。本节基于 `advance` 分支 `script/sql/wms.sql` 实测，非二手描述。

### 9.1 实测结论

业务表：`wms_warehouse`、`wms_area`、`wms_item`、`wms_item_sku`、`wms_item_brand`、`wms_item_category`、`wms_merchant`、`wms_inventory`、`wms_inventory_detail`、`wms_inventory_history`、`wms_receipt_order`、`wms_shipment_order`、`wms_movement_order`、`wms_check_order`（含各自 detail）。

关键发现：

- `wms_merchant` 表注释为“往来单位”，`wms_shipment_order.merchant_id` 注释为“客户”，即 merchant 是客户/供应商，**不是 3PL 货主**
- `wms_inventory` 仅含 `id`、`sku_id`、`warehouse_id`、`area_id`、`quantity`、`remark` 与审计字段；**无货主维度、无货位、无批次效期、无库存状态、无预占/占用**
- **无货位表**，层级只有仓库与库区
- `wms_shipment_order` 状态仅暂存/作废/完成，无分配、拣货、复核中间态
- 技术栈实测：`master` / `lite` / `advance` 为 JDK17 + Spring Boot 3.1 + Vue3，仅 `v1` 为 JDK8 + Vue2。此前记录的技术栈判断需以此为准

### 9.2 结论修正

定位由“是否可用”改为“用哪一部分”：

- 可复用：若依基座（权限、菜单、字典、代码生成）、主数据 CRUD、单据骨架、Lodop 打印、库存看板
- 必须新建：货主（owner）表、货位（bin）表、库存四维模型（位置/质量/权属/可用性）、出库状态机、任务中心、PDA
- 不复用 `wms_merchant` 承载货主，避免语义污染
- 净收益约 4-6 周，需 2-3 周适配与清理

技术路线总结论不变：核心自研。变化仅在于起步方式，由“从零搭建”改为“以 wms-ruoyi 为脚手架起步，核心模块自研”。

## 10. 打印方案（2026-10-22 确认）

选型：hiprint。

- `hiprint` 0.4.0 与 `vue-plugin-hiprint` 0.0.62 均为 MIT，仓库 CcSimple/vue-plugin-hiprint，1739 star，2026-10-08 仍有推送
- 替代 Lodop：后者依赖 ActiveX 与本地插件，Vue3 与现代浏览器下不可用

分场景与成本：

| 场景 | 方案 | 成本 |
| --- | --- | --- |
| 模板可视化设计 | hiprint 设计器 | MIT，免费 |
| 单据预览打印 | 浏览器 print | 免费 |
| 标签静默打印 | hiprint 官方客户端 | 付费，已确认采购 |
| 服务端 PDF 归档 | Apache PDFBox | Apache-2.0，免费 |

hiprint 客户端是本次唯一的付费组件，其余保持零授权预算。自建 Electron 打印代理的方案不再采用。

实施注意：

- 前端若走 HTTPS，页面连接本地客户端 WebSocket 会触发混合内容拦截，需客户端支持 wss，或业务侧改用内网 HTTP
- 客户端默认端口 17521，多仓多终端部署需统一客户端版本
- 客户端须支持离线，仓库断网时不能影响出纸
- hiprint 内部依赖 jQuery，与 Vue3 混用时需做样式隔离
- 版本号偏低，锁定版本，升级前须做打印精度回归
- 热敏标签尺寸依赖浏览器 CSS 换算，必须用实际打印机校准
- 禁止使用 iText（AGPL 或商业授权）

采购时需确认：按设备数、并发数还是按年计费；是否覆盖多仓多终端；版本升级是否包含；是否绑定域名或项目；是否支持离线运行。

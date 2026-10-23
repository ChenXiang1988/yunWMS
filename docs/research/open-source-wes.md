# 开源 WES 与任务中心参考实现调研

- 调研时间：2026-10-23
- 数据来源：GitHub 仓库原始文件（README、CLAUDE.md、docker-compose.yml、README.adoc 等）；star 与提交频次来自 repositorystats 快照与搜索页快照，非 GitHub API 实时值（采集时 API 已触发匿名限流）
- 结论状态：候选已确认；open-wes 代码结构已于 2026-10-23 浅克隆实测，其余候选未读源码
- 上游文档：`docs/research/execution-unit-modeling.md`（执行单元与任务池建模）、`docs/research/wms-open-source-landscape.md`（WMS 全景，已收录 open-wes 一行）
- 绑定变更：无。`init-wms-foundation` 明确不做任务中心与 PDA，本文件作为后续任务中心变更的前置依据，届时在该变更 `proposal.md` 中引用

## 1. 需求要点

### 1.1 功能范围

| 域 | 内容 |
| --- | --- |
| 任务中心 | 任务生成、入池、派工、领取、执行、异常上报、进度监控 |
| 执行单元 | 人 + 终端的工作会话、纯设备会话、能力匹配、跨班交接 |
| 设备接入 | 料箱机器人（海柔）、AGV/AMR、输送线；协议适配层与厂商隔离 |
| 节拍 | 波次作为节拍视图（截单、发波拍点、容量水位），非任务 |

### 1.2 关键特征

- 任务状态属于任务本身，`assigned_executor_id` 可空，空即在池中
- 交接班 = 结束会话，任务自动回池，不做转派
- 派工是「任务所需能力 × 执行单元能力」的交集匹配，再叠就近与负载
- 设备状态由上游 RCS 告知，是镜像而非事实，需区分网络抖动与真离线
- 任务下发必须幂等，重复下发与增量更新可区分
- 人机混跑，人与设备在同一资源池内竞争任务

### 1.3 边界与非目标

- 不做 WMS 的库存主数据与单据（入库单、出库单、库存台账由本项目自身承担，不属于本调研范围）
- 不引入 ROS 2 生态，不自行研发机器人导航与交通管制
- 不采购商业 WES/WCS/RCS
- 本调研只判断「能否作为代码基线」与「哪些结构值得参考」，不做产品实测

### 1.4 现存约束

- 技术栈：Java 21 + Spring Boot 3.x、PostgreSQL、Valkey
- 业务形态：多货主 3PL，货主维度进数据模型
- 许可：零商业授权预算，GPL/AGPL/SSPL 一票否决
- 现状：首期无任务、无 PDA、无设备，任务表与会话表尚未建

## 2. 检索范围与方法

检索时间 2026-10-23。检索词：

| 层次 | 检索词 |
| --- | --- |
| WES 层 | `open source warehouse execution system WES github task orchestration` |
| WCS / 车队层 | `open source warehouse control system WCS AGV fleet manager github openTCS Open-RMF`、`开源 WCS 仓库控制系统 github 分拣 输送线 调度 任务下发` |
| 人机协同 | `open source task allocation labor management warehouse human robot orchestration github` |
| 设备协议 | `VDA5050 open source implementation github master control AGV order task protocol` |

方法：先按定位初筛，再逐个读仓库原始文件（`README`、`CLAUDE.md`、`docker-compose.yml`、`README.adoc`）核对，不采信二手评测文章。检索中发现大量以「JeeWMS 多租户 / 3PL 落地」为题的深度文章实为项目作者本人在博客园、掘金、CSDN 批量发布的内容，不作为证据。

## 3. 候选清单

| 项目 | Star | 活跃度 | 许可 | 技术栈 | 定位 |
| --- | --- | --- | --- | --- | --- |
| jingsewu/open-wes | 341 | 382 提交，最后提交约 2026-06 | MIT | Java 17 + Spring Boot 3.2.2 + Gradle + DDD；React 17 + AMIS | 开源 WES，任务调度 + 工位执行 + 设备对接 |
| openTCS/opentcs | 588 | 2 分支 73 标签，Fraunhofer IML 维护 15 年以上 | MIT（代码）+ CC-BY-4.0（文档） | Java 21 + Gradle + Guice | AGV/AMR 车队调度框架 |
| open-rmf/rmf | 未采集 | ROS 2 生态，持续更新 | 未从 LICENSE 核实（通常 Apache-2.0） | ROS 2 + C++ | 多车队 + 楼宇设施编排 |
| VDA5050 | 标准 | 3.0.0 版 2025-03 | 开放标准 | — | AGV 与主控的通信协议 |
| 243480503/WMS-BS | 61 | 48 提交 | MIT | 未采集 | 国内智能仓储 WMS，含输送线/堆垛机/AGV |
| mingandrew/WCS_INTEGRATE | 未采集 | 未采集 | 未采集 | C# | WCS，含搬运/摆渡/交通管制任务类 |
| zhart0520/agv-wcs | 未采集 | 12 提交 | 未采集 | C#/.NET | 玩具级 AGV 调度 |

关联实现（VDA5050 生态）：`ros-industrial/vda5050_core`（C++ 库，框架无关，含 MQTT 传输抽象与 master control API）、`libvda5050pp`（C++，实现 VDA5050 1.1.0）。

## 4. 逐项比较

### 4.1 jingsewu/open-wes

实测方式：2026-10-23 经 codeload 下载 master 快照（`git clone` 直连 github.com 在本网络不可达），只读统计，未改动工作区。

**规模（实测）**

| 指标 | 值 |
| --- | --- |
| Java 文件 / 行数 | 1,357 个 / 64,524 行 |
| 后端模块 | 9 个（`modules-wes` 813、`modules-api-platform` 128、`modules-station` 115、`modules-utils` 103、`modules-user` 73、`modules-plugin` 59、`modules-ai` 26、`modules-search` 23、`modules-gateway` 6） |
| 数据表 | 67 张（`init_create_table.sql`） |
| 迁移脚本 | 22 个 Liquibase changelog，跨度 2024-12-14 至 2026-05-23 |
| 测试文件 | 33 个（相对 1,357 个源码文件，覆盖率极低） |
| 前端页面 | 173 个 tsx |

**任务模型（实测）**

`OperationTask` 聚合根字段：`taskType`、`workStationId`、`priority`、`skuId`、`skuBatchStockId`、`skuBatchAttributeId`、`containerStockId`、`sourceContainerCode/Face/Slot`、`boxNo`、`requiredQty`、`operatedQty`、`abnormalQty`、`targetLocationCode`、`targetContainerCode`、`targetContainerSlotCode`、`transferContainerRecordId`、`orderId/orderNo/detailId`、`assignedStationSlot`、`taskStatus`、`abnormal`、`version`

任务类型 7 种：上架 `PUT_AWAY`、拣选 `PICKING`、两步式理货 `TWO_STEP_RELOCATION`、一步式理货 `ONE_STEP_RELOCATION`、盘点 `STOCK_TAKE`、复核 `RECHECK`、调整 `ADJUST`。

完成判定按数量累计：`operatedQty + abnormalQty == requiredQty`。

**关键缺口（实测确认）**

1. **任务只挂工位，不挂人**。`w_operation_task` 表没有 `operator_id`、`user_id`、`executor_id` 任一字段，只有 `work_station_id`；`w_work_station` 表只有 `station_code`、`work_station_mode`、`work_locations`、`work_station_status`，没有操作员绑定。系统不记录是谁在工位上作业。
2. **没有执行单元会话、能力匹配、工时计量、跨班交接**。`execution-unit-modeling.md` 的整套模型在源码中无对应物。
3. **库存不是货主维度**。`owner_code` 出现在 13 张表（`m_owner_main_data`、`m_sku_main_data`、入库/出库/收货/拣货/上架/盘点/调整明细、预分配记录、条码与打印规则），但库存表 `w_sku_batch_stock` **没有 owner_code**，唯一索引为 `sku_batch_attribute_id + warehouse_area_id`。货主只到单据层，未进入库存主键。

**定位（实测修正）**

它是**货到人（goods-to-person）自动化仓的 WES**：容器送到工作站，人在工位作业。不是人工仓的人员派工系统，也不是多货主 3PL 的 WMS。

**其余维度**

- 活跃度：382 提交，最后提交约 2026-06，近 4 个月无更新；changelog 最晚 2026-05-23
- 许可证：MIT，可闭源分发
- 依赖与可维护性：Nacos 2.0、Dubbo、Kafka、MySQL 8.0、Redis 7.2、Liquibase；`docker-compose.yml` 中 `kafka-exporter` 指向未定义的 kafka 服务，MySQL 与 Nacos 使用 root/root 弱口令，Grafana 匿名可看，监控组件全部 `latest` 标签
- 技术栈兼容性：后端 Java 17 + Spring Boot 3.2.2，与本地栈接近；**前端 React 17 + TypeScript + MobX + AMIS，与 Vue 3 + Ant Design Vue 不符**
- 二次开发成本：不能嵌入。定位为独立 WES（接上游 WMS），而本项目任务中心与库存、单据同源
- 工程约束可借鉴：聚合根不允许 setter，只走 `initialize()/complete()/cancel()/close()` 生命周期方法；仓储只允许 `find*/save*`，禁止 `update*`；`@Transaction` 只加在聚合与仓储上，查询移出事务；领域事件异步发布；每领域一个 `IPlugin` 提供 before/after 钩子
- 开发方式：仓库根有 `CLAUDE.md`（AI Coding Guidelines）与 `docs/superpowers/specs/`，属 AI 辅助开发项目

### 4.2 openTCS/opentcs

- 功能覆盖度：AGV/AMR 车队层，提供 routing、dispatching、scheduling 与交通管制；内置 VDA5050 支持，非 VDA5050 车辆需自写 communication adapter
- 活跃度：Fraunhofer IML 维护，15 年以上，588 星、199 fork、73 标签；7.3.0 版本与 2026-05 发布时间来自二手文章，未验证
- 许可证：源代码 MIT，文档 CC-BY-4.0，配置与数据文件 CC0-1.0，部分第三方资源 Apache-2.0 或 OFL-1.1，采用 REUSE 规范
- 依赖与可维护性：Java 21 + Gradle + Guice，与本地 Java 版本一致；官方明确其为框架而非开箱产品
- 技术栈兼容性：高（Java 21）
- 二次开发成本：中。只管车，不管人、库存、单据；定位在任务中心之下
- 与我们的关系：可作为「车队调度外包层」，不进入任务池语义

### 4.3 open-rmf/rmf

- 功能覆盖度：多车队管理 + 设施集成（电梯、门、workcell），含任务编排组件
- 活跃度：ROS 2 生态（Humble、Jazzy、Kilted、Rolling），持续更新
- 许可证：README 未声明，需以各子包 `package.xml` 与 `LICENSE` 为准，未验证
- 依赖与可维护性：ROS 2 + C++ + colcon/vcs/rosdep，首次构建需从 Gazebo Fuel 下载模型，可达 1 小时
- 技术栈兼容性：低（ROS 2 生态，与 Java 栈无交集）
- 二次开发成本：高。仅当需要跨楼层电梯联动时才值得引入

### 4.4 VDA5050

- 定位：AGV 与中央主控之间的开放接口标准，VDA、VDMA 与 KIT IFL 共同制定
- 版本：3.0.0（2025-03），另有 2.x 与 1.1.0 实现生态
- 核心模型：`order` 携带 `orderId` 与 `orderUpdateId`（幂等与增量更新）、任务由 `node` + `edge` 组成且每段挂 `actions`、车辆回传 `state`、`agvPosition`、`errors`、`actionStates`
- 对我们：不引入代码，只借鉴契约。任何接设备的下发接口都应该能表达「幂等 + 增量 + 动作序列 + 状态回传」
- 开源实现：`ros-industrial/vda5050_core`（C++，框架无关）、`libvda5050pp`（C++，1.1.0）

### 4.5 其余候选

- `243480503/WMS-BS`：MIT，61 星，48 提交，定位为含输送线、堆垛机、AGV 的智能仓储 WMS，规模小，仅作单据与设备关系的命名参考
- `mingandrew/WCS_INTEGRATE`：C#，任务类含 CarrierTask、FerryTask、TrafficControlMaster，可参考任务类型枚举的切分方式，语言不符不做深入
- `zhart0520/agv-wcs`：C#/.NET，12 提交，玩具级，排除

## 5. 关键判断

1. **真正叫 WES 且能对上「任务中心」的开源项目只有一个**（open-wes），其余候选都在它下层（车队调度）或是一个协议。
2. **open-wes 是独立 WES，不是可嵌入的组件。** 它的假设是上游已有 WMS 并向下发任务；本项目是 WMS 自己长任务中心，库存与单据都在同一进程内，边界不同。
3. **技术栈只对上后端。** 前端 React + AMIS 与 Vue 3 + Ant Design Vue 不符，界面与工程结构零参考。
4. **openTCS 解决的是另一个问题。** 它管「车怎么走、谁先过路口、这趟活派给哪台车」，不管人、不管库存；成熟度在候选中最高的，但定位在任务池之下。
5. **VDA5050 是这里最耐用的东西。** 它不是代码，是一份任务下发契约，接任何设备时都要面对同样的幂等与增量问题。
6. **star 与实际可用度弱相关。** open-wes 341 星但单人维护、AI 辅助开发、近 4 个月停更；openTCS 588 星但是框架而非产品。

## 6. 结论

**自行实现。** 不采用任何候选作为代码基线；open-wes 仅作任务中心的结构参考，openTCS 仅作车队层的外包选项，VDA5050 仅作任务下发的契约参考。

理由：

- 边界不同：open-wes 是独立 WES，任务中心要接上游 WMS；本项目任务中心与库存、单据同源，强行引入会制造一次拆分与一次合并
- 前端不符：open-wes 前端为 React + AMIS，本项目为 Vue 3 + Ant Design Vue，前端无法复用
- 维护风险：open-wes 单人维护、近 4 个月无提交、AI 辅助开发，测试文件仅 33 个
- 模型缺口（已实测）：任务只挂 `work_station_id`，全表无操作员字段，不存在「执行单元 = 人登录终端的会话」「任务入池不挂人」「交接班自动回池」这一层；而这一层正是本项目 `execution-unit-modeling.md` 的核心
- 库存缺口（已实测）：库存表 `w_sku_batch_stock` 无 `owner_code`，货主只到单据层，与本项目「库存唯一键含货主」的硬要求不同
- 许可不是障碍：本批候选许可均为 MIT 或更宽松，与 WMS 全景调研中 JeeWMS 的 GPL-3.0 情形不同

### 6.1 被排除的候选与原因

| 项目 | 排除原因 |
| --- | --- |
| open-wes | 独立 WES 定位、前端 React/AMIS、单人维护且停更、未见执行单元会话模型 |
| openTCS | 车队层框架，不管人与业务语义；可作为外包层，不做代码基线 |
| open-rmf | ROS 2 + C++，仅跨楼层设施联动才需要 |
| WMS-BS | 规模小（48 提交），无任务层证据 |
| WCS_INTEGRATE / agv-wcs | C#/.NET，语言不符，后者为玩具项目 |

### 6.2 可吸收的结构

| open-wes 的做法 | 对我们的用法 |
| --- | --- |
| task / station / ems-proxy 三段拆分 | 对应任务池、作业端、设备代理三条边界，模块划分照此切 |
| 聚合根无 setter，走生命周期方法 | 与任务状态机一致，状态变更必须是显式方法 |
| 仓储只 find/save，不 update | 与「任务状态属于任务本身」一致，避免局部更新绕过状态机 |
| 领域事件驱动监控，零侵入业务代码 | 执行看板与流程监控走事件，不侵入派工逻辑 |
| `Container` 作为一等领域对象 | 对应海柔料箱，容器与库位分离 |
| 每领域一个插件提供 before/after 钩子 | 后期接入不同厂商设备时的扩展点 |

## 7. 风险与待确认

### 7.1 风险

- **测试覆盖极低**：1,357 个源码文件对应 33 个测试文件，open-wes 的回归能力靠人工验证，不能作为质量背书
- **ems-proxy 未读实现**：设备代理模块是否已对接真实设备厂商未验证，本次只确认了模块、`e_container_task` 与 `w_transfer_container` 等表存在
- **维护中断**：open-wes 单人维护，近 4 个月无提交，短期不建议作为依赖，长期不建议作为参考的唯一来源
- **数据时效**：star 与提交数据来自第三方快照，GitHub API 采集时已匿名限流，未能实时核对
- **协议版本分裂**：VDA5050 存在 1.1.0、2.x、3.0.0 三个版本，设备厂商支持情况不一
- **锁定风险**：若引入 openTCS 作为车队层，其路网模型（固定路网）与自由导航 AMR 的适配是已知限制

### 7.2 必须确认的事项

- 海柔料箱机器人是否支持 VDA5050，还是只能走其自有 RCS 接口（决定 `ems-proxy` 的协议适配策略）
- AGV 调度是自建还是引入 openTCS，需在首次接 AGV 前定
- 任务中心的工时计量口径（按任务计、按动作计、按分钟计）未定
- 「一个人同时接几个任务」的上限，各来源说法不一，需按现场实测
- open-wes 的 `ems-proxy` 是否已对接真实设备厂商（本次只确认模块与 `e_container_task`、`w_transfer_container`、`w_transfer_container_record` 等表存在）

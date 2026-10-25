# 项目结构与归属

适用于 `code/**`。定义「东西放哪」，防止同一种代码在两处出现。

## 一、顶层布局

```text
code/
├── backend/     Java 后端，独立 Maven 工程
├── frontend/    前端，pnpm workspace 根
└── tools/       开发期工具（CAD 解析等），不进生产镜像
```

仓库根只放工程性文件。业务代码 **MUST NOT** 出现在根目录。

## 二、后端模块

```text
wms-backend/
├── wms-web        入站协议类型（白名单见下）
├── wms-auth       认证、会话、用户、角色、权限点、数据范围
├── wms-master     货主、联系人、税号、品类、品牌、商品、条码、包装
└── wms-app        启动装配、配置、数据库迁移
```

**模块边界 MUST 由业务语义、职责与变化原因决定，MUST NOT 由表、接口、技术组件或外部系统决定。**
判断方法是问「改这个东西时，哪些代码会跟着改」，而不是「数据存在哪张表」。
按表拆模块的典型症状是：加一张关联表就要同时改三个模块。

依赖方向 **MUST** 单向且无环：

```text
wms-app ──► wms-auth ──┐
   │                   ├──► wms-web
   └────► wms-master ──┘
```

- `wms-web` **MUST NOT** 依赖任何业务模块。
- `wms-auth` 与 `wms-master` **MUST NOT** 互相依赖。确实需要对方数据时，由 `wms-app` 装配，或在能力提供方暴露 Application 接口，需求方依赖该接口。
- **业务代码 MUST NOT 写进 `wms-app`**，它只负责装配。

### wms-web 的边界

它替代原 `wms-common`。改名不是换字眼，是收窄职责：`common` 天然会变成垃圾场，`web` 天然只放 Web 层共用的东西。

**允许存在的类型（白名单，新增必须走 design 决策）：**

| 类型 | 理由 |
| --- | --- |
| `ApiResult` | 统一响应结构，所有 Controller 返回它 |
| `ErrorCode` | 错误码归属唯一的定义处 |
| `BizException` | 业务失败的载体 |
| `GlobalExceptionHandler` | 全局异常到响应结构的映射 |
| `PageResult` | 分页响应结构 |

**禁止出现在此层的：** 业务工具类、领域常量、跨模块 DTO、Repository、任何带业务语义的代码。出现即为违规。

### 分层不得跨越模块

`wms-web` 属于**入站协议层**。因此：

- `domain` **MUST NOT** 依赖任何承载协议、持久化、配置或框架能力的技术共享模块，`com.yunwms.web.*` 只是其中之一。模块名称 **MUST NOT** 被当作允许反向依赖的理由。
- `domain` 包 **MUST NOT** import `com.yunwms.web.*`。领域模型不认识 HTTP。
- `infrastructure` **MUST NOT** import `com.yunwms.web.*`。持久化实现不认识响应结构。
- `application` 引用 `wms-web` 属于**已知技术债**：当前 `PasswordLoginService` 等用例直接抛 `BizException(ErrorCode)`，使 Application 耦合了协议错误码。目标形态是由 `wms-web` 的全局异常处理把 Application 自有异常映射为 `ErrorCode`，Application 只表达失败原因。新代码 **MUST NOT** 扩大这处耦合。
- 只有 `interfaces` 与 `application` 可以依赖它。

这三条 **MUST** 由 ArchUnit 测试拦截，不靠自觉。

## 三、业务模块内的包结构

以 `wms-master` 为例，其余模块同构：

```text
com.yunwms.master/
├── interfaces/                 入站协议
│   ├── controller/             按能力分子包，一个能力一个 Controller
│   │   ├── OwnerController.java
│   │   ├── request/            OwnerCreateRequest
│   │   ├── response/           OwnerDetailResponse
│   │   └── assembler/          Response 与 DTO 的转换
├── application/                用例编排
│   ├── service/                用例实现，事务边界在此
│   ├── command/                入参对象
│   ├── query/                  查询服务
│   └── dto/                    模块内传递的模型
├── domain/                     业务规则
│   ├── model/                  聚合、值对象
│   ├── service/                跨聚合的领域服务
│   └── port/                   需要外部能力时定义的接口
└── infrastructure/             技术实现
    ├── persistence/
    │   ├── entity/             表映射，与表结构一一对应
    │   ├── mapper/             MyBatis-Plus Mapper
    │   └── repository/         port 的实现
    └── config/                 本模块的技术装配
```

层间依赖 **MUST** 限于：

```text
interfaces → application → domain
infrastructure → domain / application 的 port
```

`domain` **MUST NOT** 依赖 Spring、MyBatis、Jackson 或任何框架注解。

## 四、模型归属

- **实体**（`*Entity`）**MUST** 与表一一对应，只出现在 `infrastructure/persistence/entity`。
- **领域模型**（`domain/model`）**MUST NOT** 带持久化注解。
- **聚合的创建与重建 MUST 分离**：创建入口由领域模型自己保证初始状态与不变量，接口层与应用层 **MUST NOT** 注入或覆盖；从库中重建 **MUST** 走独立入口并校验读到的事实，**MUST NOT** 借重建路径绕过创建期校验。
  当前 `UserAccount` 做得对：紧凑构造器拒绝「货主账号没有 ownerId」，仓储的 `toDomain` 也走同一个构造器，因此库里读到脏数据会当场失败而不是被放行。这条规则是为了防止以后为了「重建方便」新增一个不校验的构造重载——那会让唯一入口变成两个，而绕过校验的那个总是更好用。
- **请求/响应**（`interfaces/**/request|response`）**MUST NOT** 被 `application` 或 `domain` import。
- 三者之间的转换 **MUST** 有显式的转换类，**MUST NOT** 用同一个类兼任两种角色。

判断方法：**同一个类如果同时出现在两个层的包里，就是归属错了。**

## 五、数据库迁移

`wms-app/src/main/resources/db/migration/` 是该目录**唯一**存放 SQL 迁移的地方。

- 该目录 **MUST** 只放 `.sql` 文件，**MUST NOT** 放脚本、文档或临时查询。
- 迁移的归属模块是 `wms-app`，业务模块 **MUST NOT** 自带迁移目录。

## 六、前端

```text
frontend/
├── apps/
│   ├── network-console/    仓网管理端
│   └── owner-portal/       货主端
└── packages/               令牌、请求层、共享类型
```

前端细则见 `docs/frontend.md`，本文件不重复。

## 七、工具

`code/tools/` 下是开发期工具（如 CAD 解析）。它们：

- **MUST NOT** 被打进后端镜像或生产构建
- **MUST** 各自独立可运行，有自己的依赖清单
- **MUST NOT** 被 `code/backend` 依赖

## 八、新增模块的门槛

新增 Maven 模块 **MUST** 满足其一：

1. 有独立的依赖闭包（不与现有模块共享大部分依赖）
2. 有独立部署需求
3. 有明确的领域边界，且与现有模块无循环依赖风险

**不满足时 MUST NOT 新建模块**——多一个空模块的成本不是十分钟，是此后每次构建都多一次跳转。

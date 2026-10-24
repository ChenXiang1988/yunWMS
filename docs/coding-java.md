# Java 与持久化编码规范

适用于 `code/backend/**`。技术栈固定为 Java 21 + Spring Boot 3.4 + MyBatis-Plus + PostgreSQL。

## 注释与文档

- 公开类型与行为契约 **MUST** 有中文 Javadoc，参数、返回值、抛出异常分别用 `@param`、`@return`、`@throws` 说明。
- 注释 **MUST** 说明「为什么」或约束背景，**MUST NOT** 复述代码字面含义。
- 生产代码 **MUST NOT** 为兼容测试而新增构造器、分支或可空依赖。

## 命名与字段

- 数据库列 **MUST** 是直接映射字段的命名基准，Java 字段与 HTTP JSON **MUST** 统一 camelCase，只允许 `snake_case → camelCase` 一种转换。
- 请求与响应 **MUST** 基于真实字段与明确契约，**MUST NOT** 自行创造、重命名或改变结构。
- 发现既有字段命名不一致时 **MUST** 主动修正并同步全部引用，**MUST NOT** 为兼容保留别名。
- 非数据库映射字段 **MUST** 有明确业务定义与数据来源。

## 分层职责

- Controller **MUST** 只做协议转换：接收、校验、调用 Application、返回。**MUST NOT** 写业务判断、**MUST NOT** 直接调用 Mapper。
- Controller **MUST NOT** 注册局部异常处理器。异常统一交给 `GlobalExceptionHandler`，**MUST NOT** 依异常名推断错误码。
- Service **MUST** 承担用例编排与事务，**MUST NOT** 承载领域规则以外的协议语义。
- Mapper **MUST** 只做数据库映射，**MUST NOT** 内含业务分支。

## Spring

- Bean **MUST** 使用构造器注入，依赖字段 **MUST** 为 `private final`。
- Spring 组件类型名 **MUST** 表达真实职责；同一概念存在多个入口时 **MUST** 在类型名上体现差异。
- `@Transactional` **MUST** 位于能经 Spring 代理调用的方法上；代理、可见性或异常处理影响语义时 **MUST** 实测提交与回滚，**MUST NOT** 只断言注解存在。

## 校验

- 能由标准 Bean Validation 表达的单字段约束 **MUST** 使用标准注解；多字段或分支约束 **MUST** 使用 class-level constraint，并产生稳定可定位的 violation path。
- `ConstraintValidator` **MUST** 只依赖当前输入，**MUST NOT** 访问数据库、缓存或远程服务。
- 校验 `message` **MUST** 直接写中文文案，**MUST NOT** 使用国际化 key。

## 枚举

- **NEVER** 使用 `ordinal()` 作为数据库、HTTP 或事件的编码。
- 枚举入库 **MUST** 显式存 `name()` 或独立 code 字段；读取到未知编码 **MUST** 抛异常，**MUST NOT** 静默回落默认值。

## MyBatis-Plus

- 表映射类 **MUST** 放在模块的 `infrastructure/persistence/entity`，查询投影 **MUST NOT** 声明 `@TableName` 或参与写入。
- 单表等值、单条插入、批量插入、按业务键更新 **MUST** 优先用 `BaseMapper` 或 Lambda Wrapper；仅多表原子行锁、数据库特有语法时 **MUST** 用 XML。
- 自定义 SQL **MUST** 显式列名，**MUST NOT** `SELECT *`，**MUST NOT** 拼接外部输入。
- 空 `IN` 集合 **MUST** 短路为空结果或拒绝，**MUST NOT** 因省略条件而扩大范围。

## PostgreSQL 语义（与 MySQL 不同，出错不报错）

- **NULL 会让 CHECK 通过**：`CHECK (length > 0)` 在 `length` 为 NULL 时结果为 NULL，不视为违反。需要非空时 **MUST** 显式写 `IS NOT NULL AND ...`。
- **部分唯一索引可用**：`CREATE UNIQUE INDEX ... WHERE deleted_at IS NULL`。这是 PG 相对 MySQL 的能力，该用就用。
- **外键目标不能是部分索引**：外键引用的列 **MUST** 建全量唯一索引。
- **`ON CONFLICT DO NOTHING` 返回 0 行是正常结果**，**MUST NOT** 当作失败。
- **时间统一 `timestamptz`**，以 UTC 存储，展示层按时区转换。
- **别用 `serial`**，用 `GENERATED ALWAYS AS IDENTITY`。

## 数据库迁移

- 已生效的迁移脚本 **MUST NOT** 修改，变化 **MUST** 新增一份 `V{序号}__{描述}.sql`。
- 一次迁移一个文件，序号连续。
- 迁移 **MUST NOT** 与代码改动混在同一次提交。

## 配置

- 必需配置缺失或非法 **MUST** 在启动时失败，**MUST NOT** 静默兜底。
- 敏感配置（数据库密码、SMTP 密码）**MUST** 走环境变量，**MUST NOT** 入库、入仓、入日志。

## 日志

- 日志 **MUST** 用参数化占位符，**MUST NOT** 字符串拼接。
- 同一异常 **MUST NOT** 重复记录；`ERROR` **MUST** 保留堆栈。

## Maven

- 模块 **MUST** 声明实际使用的直接依赖，**MUST NOT** 依赖偶然的传递依赖。
- 版本 **MUST** 由父 POM 的 `dependencyManagement` 统一管理，业务模块 **MUST NOT** 自行覆盖。
- 仅测试源码使用的依赖 **MUST** 为 `test` scope。

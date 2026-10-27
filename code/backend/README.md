# 后端服务

Java 21 + Spring Boot 3.4 + MyBatis-Plus + PostgreSQL。

## 模块划分

```
wms-backend
├── wms-web       入站协议层共用物：统一响应、错误码、业务异常、全局异常处理
├── wms-auth      认证、会话、用户、角色、权限点、数据范围
├── wms-master    货主、联系人、税号、品类、品牌、商品、条码、包装
└── wms-app       启动装配、配置、数据库迁移
```

依赖方向是单向的，不允许回指：

```
app ──► auth ──┐
 │             ├──► web
 └────► master ─┘
```

`web` 不依赖任何业务模块；`auth` 与 `master` 互不依赖，需要跨模块协作时由 `app` 层装配。
**业务代码不放 `wms-app`**，它只负责启动。

## 环境准备

```bash
docker compose -f docker-compose.dev.yml up -d
```

### 端口被占用时

三个服务的宿主端口都可以用环境变量覆盖，compose 默认仍是标准端口：

```bash
# 本机已有 PostgreSQL 占着 5432
PG_PORT=15432 docker compose -f docker-compose.dev.yml up -d

# 应用连过去
DB_URL="jdbc:postgresql://127.0.0.1:15432/yunwms" ./mvnw spring-boot:run
```

可用变量：`PG_PORT`、`REDIS_PORT`。

### 拉不到镜像时

官方 registry 在部分网络环境下会被 DNS 劫持，表现为时好时坏：同一个 `docker pull`
这次成功、下次报 `connection refused` 或 `context deadline exceeded`。

判断方法：连续几次 `nslookup registry-1.docker.io`，如果解析出的 IP 每次不同、
且都不在 Docker 的常见网段，就是被劫持了。

遇到时用镜像源拉取后打回标准名：

```bash
docker pull docker.m.daocloud.io/library/postgres:16-alpine
docker tag  docker.m.daocloud.io/library/postgres:16-alpine postgres:16-alpine

docker pull docker.m.daocloud.io/valkey/valkey:8-alpine
docker tag  docker.m.daocloud.io/valkey/valkey:8-alpine valkey/valkey:8-alpine
```

`docker-compose.dev.yml` 里写的是标准镜像名，不打进镜像源地址，保持文件可移植。

### 关于对象存储

已按 `design.md` **D40** 决策：做一层 `ObjectStorage` 抽象（`put` / `get` / `url` / `delete`），本地用文件系统实现，生产接 S3。**compose 里不再有 MinIO**，开发环境依赖只剩 PostgreSQL 与 Valkey。

背景：`minio/minio` 已从 Docker Hub 下架，`quay.io/minio/minio` 需要登录，`bitnami/minio` 也已迁移，三个来源都取不到——这不是网络问题，是分发策略变了。

被否方案：换 S3 兼容容器（`localstack`、`seaweedfs`）。否决原因是开发机仍要多背一个服务，而首期根本用不到对象存储。

## 启动

```bash
./mvnw spring-boot:run
```

> **首次构建先装依赖模块**。干净仓库下直接 `./mvnw -pl wms-app ...` 会报
> `Could not resolve dependencies ... wms-auth / wms-master`，因为模块与根 POM 尚未进本地仓库。
> 先跑一次全量安装：
>
> ```bash
> ./mvnw install -DskipTests
> ```

首次启动会自动执行 `wms-app/src/main/resources/db/migration` 下的 36 份迁移脚本，建出 24 张表。

可配置的环境变量：

| 变量 | 默认值 |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/yunwms` |
| `DB_USER` | `yunwms` |
| `DB_PASSWORD` | `yunwms` |

## 验证

```bash
./mvnw verify

curl -s localhost:8080/actuator/health   # 期望 status=UP
curl -s -o /dev/null -w '%{http_code}' localhost:8080/v3/api-docs   # 期望 200
curl -s -o /dev/null -w '%{http_code}' localhost:8080/api/anything  # 期望 401

# 登录：两端共用同一入口，主体类型由后端判定
curl -s -X POST localhost:8080/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"loginName":"<登录名>","password":"<密码>"}'          # 期望 code=0 且 data.token 非空

# 带令牌访问未映射路径：期望 404（说明令牌已通过鉴权，请求进了 DispatcherServlet）
curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer <token>" localhost:8080/api/anything
```

接口文档：<http://localhost:8080/swagger-ui/index.html>

## 数据库迁移

Flyway 管理，**不要手改已发布的脚本**。改表结构新增一份 `V{序号}__{描述}.sql`，序号连续，描述用小写下划线。

约束验证脚本在 `tools/verify_constraints.py`，对着空库跑一遍可以确认「数据库能不能挡住错误数据」：

```bash
initdb -D /tmp/wmsddl -U postgres --auth=trust
pg_ctl -D /tmp/wmsddl -o "-p 55432 -k /tmp" start
for f in wms-app/src/main/resources/db/migration/V*.sql; do
    psql -h /tmp -p 55432 -U postgres -d postgres -q -v ON_ERROR_STOP=1 -f "$f"
done
python3 tools/verify_constraints.py
```

## 当前状态

已完成：工程骨架、统一响应、全局异常、数据库迁移、双主体登录与令牌签发、密码策略与失败锁定、首次登录强制改密、本人改密、会话失效、用户与角色管理、接口级权限拦截、字典查询、货主建档/编辑/列表/详情/状态流转/删除草稿。

未完成：邮箱找回密码、数据范围过滤、管理端踢人入口、审计日志、货主联系人/税号/主账号、商品与 SKU、仓库、入库、库存、出库、任务。

因此除登录、健康检查与接口文档外，未带有效令牌的请求一律返回 401——这是预期行为，不是故障。

## 两个约定

**响应状态码**：业务结果放在响应体 `code` 字段，HTTP 状态码只表达传输层语义。唯一例外是未认证（401）与无权限（403），这两个必须用 HTTP 状态码，否则前端无法据此跳转登录页。

**时间**：全库存 `timestamptz`，以 UTC 存储，展示时按仓库时区转换。

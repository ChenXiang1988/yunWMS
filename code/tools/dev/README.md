# 容器内开发

本机不安装 JDK。后端的编译、测试、运行全部在 `maven:3.9-eclipse-temurin-21`
容器里完成，`wmsdev.py` 是这组 docker 命令的入口。

## 前置

- Docker Desktop 已启动
- 前端需要本机 `pnpm`（Node 侧不涉及 JDK，正常装即可）

## 命令

```bash
python code/tools/dev/wmsdev.py up                  # 起 PostgreSQL 与 Valkey
python code/tools/dev/wmsdev.py build               # 全量编译安装，跳过测试
python code/tools/dev/wmsdev.py build --module auth # 只编 wms-auth
python code/tools/dev/wmsdev.py build --clean       # 改过包结构时必加
python code/tools/dev/wmsdev.py test                # verify，含集成测试
python code/tools/dev/wmsdev.py test --test BootstrapIntegrationTest
python code/tools/dev/wmsdev.py run                 # 容器跑后端，8080
python code/tools/dev/wmsdev.py logs -f             # 跟随日志
python code/tools/dev/wmsdev.py status              # 看容器状态
python code/tools/dev/wmsdev.py down                # 全停，保留数据卷
python code/tools/dev/wmsdev.py fe owner            # 货主端 5173
python code/tools/dev/wmsdev.py fe network          # 仓网端 5174
```

## 为什么测试要带四个参数

集成测试基类 `AbstractIntegrationTest` 用 Testcontainers 现起一个
`postgres:16-alpine`。测试 JVM 本身跑在容器里，要连到「兄弟容器」必须在启动时
补四项，缺任一项都失败：

| 参数 | 缺了会怎样 |
|---|---|
| `-v //var/run/docker.sock:/var/run/docker.sock` | `Could not connect to Ryuk` |
| `-e TESTCONTAINERS_RYUK_DISABLED=true` | 同上，Ryuk 回连不到宿主端口 |
| `-e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` | 数据源拿到 `localhost:<随机端口>`，连不上 |
| `--add-host host.docker.internal:host-gateway` | `UnknownHostException: host.docker.internal` |

外加 `--dns 8.8.8.8`：容器内解析 `repo.maven.apache.org` 时常失败，表现为
`Temporary failure in name resolution`。但它会覆盖 Docker 内嵌 DNS，所以必须和
`--add-host` 同时给，否则 `host.docker.internal` 跟着一起失效。

## 两个已知坑

**改过包结构要 `--clean`。** 源码目录挂载进容器，宿主机 `target/` 里会被 Maven
留下已删除类的 `.class` 与 XML 映射，增量构建不会清理它们，错误会在运行期以
`ClassNotFoundException` 的形式出现，而不是编译期报错。

**禁用 Ryuk 后失败会残留容器。** 测试中途失败时 Testcontainers 的回收容器没有
启动，`postgres:16-alpine` 会留在 `docker ps -a` 里，需要手动清理：

```powershell
docker ps -a --filter ancestor=postgres:16-alpine
```

# yunWMS

多货主 3PL 仓储系统。单租户，装在客户自己的服务器上，不做 SaaS。

代码是 AI 写的，我负责验收和拍板，所以别指望它有正经开源项目那种文档完整度。

后端 Java 21 + Spring Boot，数据库 PostgreSQL，缓存 Valkey；前端 Vue 3 + Ant Design Vue。

## 跑一下

```bash
# 后端
cd code/backend
docker compose -f docker-compose.dev.yml up -d
./mvnw install -DskipTests    # 首次得先把依赖模块装进本地仓库，不然 wms-app 起不来
./mvnw spring-boot:run

# 前端，另开一个终端
cd code/frontend
pnpm install
pnpm dev:owner                # 5173；仓网端是 pnpm dev:network，5174
```

端口被占、拉不到镜像这些坑写在 [code/backend/README.md](code/backend/README.md) 里，前端结构见 [code/frontend/README.md](code/frontend/README.md)。

## 进度

认证和货主基础能力能用了：登录与改密、用户、角色、字典查询、货主建档与状态流转。联系人、商品、条码、包装目前只有表结构，尚无对应接口；入库、库存、出库、任务还没开工。表结构和接口随时会改，别当稳定版用。

规格在 `openspec/`，写代码的规矩在 [AGENTS.md](AGENTS.md)，这两样都不轻，动手前值得扫一眼。

## 许可

MIT，见 [LICENSE](LICENSE)。想 fork 就 fork，想改就改，改完怎么用是你自己的事，出了岔子也别找我。只有一条，别用 yunWMS 这个名字发你的版本，商标的事。

PR 和 issue 都不看，这仓库只往外出。

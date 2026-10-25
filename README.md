# yunWMS

多货主 3PL 仓储管理系统。单租户、私有部署，不做 SaaS。

全部代码由 AI 编写，人类只做验收，交付协议见 [AGENTS.md](AGENTS.md)。

## 关于本仓库

- 欢迎 fork、clone、自己改。改完之后怎么用、用出什么问题，是你和你的副本之间的事，与作者无关。
- **不接受外部 PR，不接受用 issue 派活。** 本仓库只做单向发布：代码推上来，别人取走，不回流。
- 不提供任何形式的技术支持、答疑与适配。
- 安全问题请不要开公开 issue，直接私下联系作者。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.4、MyBatis-Plus、PostgreSQL、Valkey |
| 前端 | Vue 3、Ant Design Vue 4、Vite、pnpm workspace |
| 规格 | OpenSpec（spec 驱动，PRD 为派生物） |

## 目录

```
code/backend/      后端多模块 Maven 工程（wms-web / wms-auth / wms-master / wms-app）
code/frontend/     货主端与仓网端两个应用，共用 tokens / ui-web / request
code/tools/        辅助工具（接口台账生成、仓库平面图解析）
docs/              交付协议、编码规范、调研快照、PRD
openspec/          当前有效规格（specs）与拟议变更（changes）
```

## 本地运行

- 后端：见 [code/backend/README.md](code/backend/README.md)
- 前端：见 [code/frontend/README.md](code/frontend/README.md)

## 项目状态

早期阶段。已完成认证与主数据（货主、联系人、税号、品类、品牌、商品、条码、包装），前端为两个应用的骨架。入库、库存、出库、任务等模块尚未落地，表结构与接口均可能变动。

## 文档

- [AGENTS.md](AGENTS.md) — 交付协议，动手前必读
- [openspec/specs](openspec/specs) — 当前有效行为，最新
- [openspec/changes](openspec/changes) — 拟议变更，随归档消失
- [docs/coding.md](docs/coding.md)、[docs/coding-java.md](docs/coding-java.md)、[docs/frontend.md](docs/frontend.md) — 编码与前端规范
- [docs/research](docs/research) — 开源调研快照，不回改

## 许可

[MIT](LICENSE)。可以自由使用、修改、分发、商用，包括 fork 之后改完作为你自己的项目发布，无需告知作者，也无需注明来源以外的东西。

MIT 不涉及商标，所以项目名 `yunWMS` 不在授权范围内：代码随你改，但请给你的版本另起一个名字，不要以 `yunWMS` 或容易混淆的名称对外发布。

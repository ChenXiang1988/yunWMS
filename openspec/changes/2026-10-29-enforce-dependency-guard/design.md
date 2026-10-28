# Design

## Context

依赖准入目前是唯一没有机器执行点的技术选型约束：AGENTS.md 的依赖准入三问与
`openspec/config.yaml` 的 GPL/AGPL/SSPL 黑名单都只在文档里存在，加一个新依赖不会让构建变红。
证据是 `wms-auth/pom.xml` 的 `spring-boot-starter-data-redis`——同一文件里
`mybatis-plus-jsqlparser` 把许可证与选型理由写满了一段注释，而它一行都没有，
且与 `config.yaml`「Redis 7.4+ 禁止」表观冲突。

既有护栏的形态决定了本次的形态：后端用 ArchUnit 测试、前端用零依赖 Node 脚本，
两者都在 CI 里跑。调研见 `docs/research/dependency-guard.md`。

## Goals / Non-Goals

**Goals:**

- 让「引入一个没被讨论过的依赖」变成构建期失败，而不是靠 review 发现。
- 让 AGENTS.md 点名的黑名单在 Maven 侧（含传递依赖）真正被拦下。
- 给「已准入依赖」一个唯一真源，注销其许可证与登记理由。
- 存量零误报：本次落地后当前代码必须全部通过。

**Non-Goals:**

- 不做 CVE / 漏洞扫描。
- 不做传递依赖的许可证判定（需外部许可证数据库，本期不引入）。
- 不生成 SBOM，不自动修复或升级依赖。
- 不扩展 `check-ui-boundaries.mjs` 的检查范围（菜单层级、间距令牌属后续 change）。
- 不改业务代码、数据库、API。

## Decisions

### D1. enforcer 只管坐标黑名单与环境版本，不管许可证字段

- 选择：`maven-enforcer-plugin` 3.6.3，父 pom `<build><plugins>` 继承到全部子模块，
  一个 execution 绑 `validate` 阶段、`enforce` 目标，规则三条：
  - `requireJavaVersion`：`[21,)`；
  - `requireMavenVersion`：`[3.9.9,)`；
  - `bannedDependencies`：`searchTransitive=true`，黑名单见下。
- 黑名单（每一项都要在 pom 注释里写明依据）：
  - `com.xuxueli:*` —— XXL-Job，AGPL 家族，AGENTS.md 点名；
  - `*:jee-wms*`、`*:jeewms*`、`com.jeewms:*`、`org.jeewms:*` —— JeeWMS，点名；
  - `com.mysql:*`、`mysql:*` —— MySQL 驱动，GPL 双许可下的商用风险，点名；
  - `*:embedded-redis*` —— 内嵌 Redis，会在测试期拉起真实 Redis 服务端二进制，
    等于把「Redis 7.4+ 不得引入」这条部署约束绕进构建。
- 理由：`bannedDependencies` 按 `groupId:artifactId[:version]` 匹配，能覆盖传递依赖，
  这是自研脚本做不到的（脚本只看 pom 里写出来的直接依赖）。版本与环境的两条规则
  顺带消除「同一份代码在不同机器上跑出不同结论」。
- **诚实边界**：bannedDependencies **不能**按许可证字段拦截，挡不住一个没被点名过的 AGPL 库。
  这一层由 D2 的准入清单兜底——任何新依赖都要登记，登记就触发三问。
- 备选：OWASP dependency-check（需外网下载 NVD、分钟级、主查 CVE、误报需长期维护基线）、
  CycloneDX / ORT（只产出或需重工具链，都要再接一层判定器）、
  mycila license-maven-plugin（检索结果证实它查的是源码许可证头，与依赖准入无关）。

### D2. 准入清单是「未登记即失败」，不是许可证白名单

- 选择：新增 `code/tools/dependency-guard/`，Python 3 标准库实现，零第三方依赖：
  - `check_dependencies.py` —— 主脚本，退出码非 0 即失败；
  - `allowlist.json` —— 唯一真源，登记后端 Maven 直接依赖与前端 pnpm 直接依赖；
  - `tests/test_check.py` —— 自检用例；
  - `README.md` —— 用法与「如何登记一个新依赖」。
- 扫描范围：
  - `code/backend/pom.xml` 与 `code/backend/*/pom.xml` 的 `<dependencies>` 下坐标，
    跳过 `dependencyManagement`（那是版本管理不是引入）、跳过 `com.yunwms:*`（本工程内部模块）；
  - `code/frontend/apps/*/package.json` 与 `code/frontend/packages/*/package.json` 的
    `dependencies` 与 `devDependencies`，跳过 `workspace:*` 的内部包。
- 判定：代码中出现、清单中没有 → **失败**，报错里直接给出「在 allowlist.json 的哪个段登记、
  需要填写哪几个字段」。清单里有、代码中未用 → 打印提示但**不失败**，
  因为父 pom 的 `dependencyManagement` 允许「声明供后续模块使用」，把这条做成失败会让构建变脆。
- 清单字段：`coordinate`（后端）或 `name`（前端）、`version`、`license`、`note`（为什么是它、
  替代方案评估结论）。`note` 就是 AGENTS.md 依赖准入三问的落点。
- 理由：生态里所有现成方案都表达不了这个语义（见调研 R4）。把清单变成构建输入，
  就把「加依赖前先讨论」从提醒变成了流程上的必经一步。
- 为什么清单用 JSON：Python 标准库没有 YAML 解析器，为读一个清单引入 PyYAML，
  等于让「管依赖准入的护栏」自己先带一个第三方依赖——那正是它要管的东西。
- 为什么不再配一份 Markdown 版清单：脚本要读结构化数据；再写一份 md 说明会形成第二真源，
  违反 `docs/coding.md`「每个事实必须有唯一权威所有者」。人类可读的说明写在 JSON 的 `note`
  字段与 README。

### D3. 黑名单与准入清单不重叠，避免第二真源

- 选择：黑名单只存在于 enforcer（构建期、覆盖传递依赖）；`allowlist.yaml` 只做准入登记，
  **不再重复一份禁止清单**。
- 理由：两份禁止清单会漂移，而漂移的方向总是「一处更新了另一处没更新」。
  两者职责不同：enforcer 答「这个库是不是被点名禁止」，清单答「这个库有没有被评审过」。

### D4. 前端 `check:ui` 挂进 `build` 而不是靠 CI 独立步骤

- 选择：根 `package.json` 的 `build` 改为 `pnpm check:ui && pnpm -r build`。
- 理由：CI 已有独立一步，但本地 `pnpm build` 不会跑，等于把「改完能构建」和「改完合规」
  分成两件事。用显式 `&&` 而非 `prebuild`：pnpm 7+ 默认不执行 `pre`/`post` 脚本，
  靠钩子会因版本或配置不同而静默失效——我们吃的正是「规则写了但没执行」的亏。

### D5. `spring-boot-starter-data-redis` 补注释而非改名或替换

- 选择：保留依赖，在 `wms-auth/pom.xml` 补注释，写明三件事：
  服务端是 Valkey 8（`docker-compose.dev.yml` 的镜像），本依赖只是 Spring 的 Redis 协议客户端
  （Lettuce，Apache-2.0），不构成 Redis 服务端引入，与 `config.yaml`「Redis 7.4+ 禁止」不冲突；
  以及会话数据只存 String / 基本类型包装类 / 时间，不存领域对象。
- 理由：替换客户端要改序列化与会话实现，属另一个 change；删掉它会让 `Sa-Token` 的
  Redis 会话与 `DataScopeContext` 的既有设计失去落点。当前缺的只是「为什么这里是 redis」。

### D6. CI 增量

- tools job 增加两步：跑 `check_dependencies.py`（失败即红），跑该目录的 unittest。
- 不改动后端 job：`mvn verify` 已包含 enforcer（绑 `validate`，在 `verify` 之前执行）。

## Risks / Trade-offs

- **enforcer 插件拉不下来或版本与 Maven 3.9.9 不兼容** → 落地时先跑一次 `./mvnw -B validate`
  实测；插件版本钉在父 pom，不跟随 Spring Boot parent。
- **存量依赖清单漏登记导致首跑失败** → 按实际报错逐条补齐，本就应如此；
  清单的准确性由构建保证，不靠人记得更新。
- **pom XML 解析误判** → 用标准库 `xml.etree.ElementTree` 解析并正确处理 Maven 命名空间，
  不用正则匹配标签。
- **CI 变慢** → 准入核对是秒级纯文本解析；enforcer 只是解析依赖树，不下载。
- **清单会变长** → 这是设计意图：每多一个依赖就多一行需要说明理由的记录。

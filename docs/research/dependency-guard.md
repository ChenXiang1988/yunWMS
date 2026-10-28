# 依赖准入护栏调研

> 时效说明：本文档是 2026-10-29 的调研快照，按 `docs/docs-convention.md` 事后不回改。
> 需求变化时应增量新增文档，不在正文逐条加注。

## R1 需求梳理

**功能范围**

落在「交付护栏」而非业务域：让 AGENTS.md 与 `openspec/config.yaml` 里已写明的依赖准入规则
（许可证、最近维护时间、可被现有依赖替代）具备机器执行点。涉及后端 Maven 构建、前端 pnpm
workspace、CI 三个位置。

**关键特征**

- 必须**存量零误报**：当前仓库的依赖集合是要被放行的基线，新增护栏不能一上来就红。
- 必须**秒级、离线可跑**：CI 每笔推送都跑，不能引入需要下载漏洞库或许可证数据库的步骤。
- 拦截语义要覆盖「AI 随手加了一个没讨论过的依赖」——这是本项目的真实失效模式，不是 CVE。
- 失败信息要能指导下一步（去哪里登记），而不是只报「不允许」。

**边界与非目标**

- 不做 CVE / 漏洞扫描。
- 不做传递依赖的许可证判定（需要外部许可证数据库，本期不引入）。
- 不生成 SBOM，不自动修复，不自动升级。
- 不改动任何业务代码与数据库。

**现存约束**

- 后端：Maven 3.9.9（wrapper only-script）、Java 21、模块 wms-web/auth/master/app。
- 前端：pnpm workspace（packageManager 要求 11.25）、Node >= 22.13、Vue 3 + ant-design-vue 4。
- CI：`.github/workflows/ci.yml`，ubuntu-latest；已有前端 job、Python tools job、后端 `./mvnw -B verify`。
- 既有护栏形态：ArchUnit 测试（后端）、`scripts/check-ui-boundaries.mjs`（前端，Node ESM 无第三方依赖）。

## R2 检索

检索时间：2026-10-29。检索词与命中：

| 检索词 | 命中 | 链接 |
| --- | --- | --- |
| maven-enforcer-plugin latest version 2026 | 3.6.3（2026-08 仍有 Apache 项目升级到该版本） | <https://github.com/apache/maven-enforcer> |
| license-maven-plugin vs OWASP dependency-check | mycila 的 `license-maven-plugin` 报错形态是「Some files do not have the expected license header」 | <https://github.com/mathieucarbou/license-maven-plugin> |

以下候选基于既有知识与项目内既有实践补充，未在本项目实测的项已逐条标注：

| 候选 | 链接 | 许可证（未实测的标注） |
| --- | --- | --- |
| maven-enforcer-plugin（bannedDependencies / requireJavaVersion / requireMavenVersion） | <https://github.com/apache/maven-enforcer> | Apache-2.0，Apache 官方插件 |
| OWASP dependency-check-maven | <https://github.com/jeremylong/DependencyCheck> | Apache-2.0（未实测） |
| CycloneDX Maven plugin | <https://github.com/CycloneDX/cyclonedx-maven-plugin> | Apache-2.0（未实测） |
| OSS Review Toolkit（ORT） | <https://github.com/oss-review-toolkit/ort> | Apache-2.0（未实测） |
| mojohaus license-maven-plugin | <https://github.com/mojohaus/license-maven-plugin> | Apache-2.0（未实测） |
| pnpm licenses list（CLI 内置） | <https://pnpm.io/cli/licenses> | 随 pnpm 发布（未实测，本机 pnpm 9.11 与 CI 的 11.25 版本差异未验证） |

## R3 比较

| 候选 | 功能覆盖度 | 活跃度 | 许可证 | 依赖与可维护性 | 与现有栈兼容 | 二次开发成本 |
| --- | --- | --- | --- | --- | --- | --- |
| maven-enforcer-plugin | 按**坐标**拦截直接依赖与传递依赖（`searchTransitive`）；可钉 Java / Maven 版本。**不能**按许可证字段拦截 | 活跃，2026-08 有 3.6.3 升级 | Apache-2.0 | 单插件，零运行期影响，配置即规则 | Maven 3.9.9 + Java 21 原生 | 低：纯 pom 配置 |
| OWASP dependency-check-maven | CVE 为主，许可证是副产物；需下载 NVD 数据库 | 活跃（未实测） | Apache-2.0（未实测） | 分钟级耗时、需外网、误报需基线文件维护 | 与 CI 可以跑，但拖慢每笔推送 | 高：需长期维护抑制基线 |
| CycloneDX Maven plugin | 生成 SBOM，本身**不判定**、不失败 | 活跃（未实测） | Apache-2.0（未实测） | 产出物还需外部工具判读 | 兼容 | 高：要再接一层判定器 |
| ORT | 能做许可证合规策略并 fail | 活跃（未实测） | Apache-2.0（未实测） | 重量级工具链，需独立配置与下载 | 引入 JVM 之外的 Kotlin 工具链 | 高 |
| mojohaus license-maven-plugin | 汇总第三方许可证报告，需人工判读（未实测其 fail 能力） | 一般（未实测） | Apache-2.0（未实测） | 报告型，不直接给出「未登记即失败」 | 兼容 | 中 |
| mycila license-maven-plugin | **源码许可证头**检查，非依赖许可证（已由检索结果证实其报错形态） | — | Apache-2.0 | 与依赖准入无关 | 不适用 | — |
| pnpm licenses list | 列出前端依赖许可证 | 随 pnpm（未实测） | — | 只能列，fail 条件要自写 | 本机 9.11 / CI 11.25 差异未验证 | 中 |
| 自研准入清单核对（Python，零第三方依赖） | 「未登记即失败」，覆盖后端 pom 与前端 package.json 的直接依赖 | 本项目自持 | 无新依赖 | 复用 CI 已有 Python job，秒级离线 | 与既有 `check-ui-boundaries.mjs` 同构 | 低 |

## R4 结论

**自行实现 + 直接复用插件的组合**，不是三选一里的单一项：

1. **直接复用**：`maven-enforcer-plugin` 3.6.3，承担两件事——
   - `bannedDependencies` 拦截 AGENTS.md 点名黑名单，且 `searchTransitive=true` 覆盖传递依赖；
   - `requireJavaVersion` / `requireMavenVersion` 钉住构建环境，防止换 JDK / Maven 版本造成「同一份代码两种结论」。
2. **自行实现**：Python 准入清单核对脚本（零第三方依赖），承担「任何新依赖必须先登记」。
   理由：上表所有生态方案都不能表达这个语义——它们要么查 CVE、要么出报告、要么按坐标拦截，
   而本项目的失效模式是「引入了一个从没被讨论过的库」。把清单变成构建输入，
   就把 AGENTS.md 的依赖准入三问从「靠人提醒」变成了「不登记就红」。

**被否候选的落选原因**

- OWASP dependency-check：主查 CVE 不是许可证，需外网下载 NVD，分钟级，误报需长期维护基线；
  与「秒级离线」冲突，且它拦不住「新加了一个干净的 MIT 库但没人评审过」。
- CycloneDX / ORT：只产出或需重工具链，都要再接一层判定器，成本高于收益。
- mycila license-maven-plugin：检索结果证实它查的是源码许可证头，与依赖准入无关。
- mojohaus license-maven-plugin：报告型，不能给出「未登记即失败」。
- pnpm licenses list：本机与 CI 的 pnpm 版本差异未验证，本期不引入，前端只核对准入清单。

## R5 风险与待确认

**风险**

| 风险 | 说明 | 处置 |
| --- | --- | --- |
| enforcer 只能按坐标拦截 | 挡不住一个「没被点名过的 AGPL 库」 | 由准入清单兜底：任何新依赖都要登记，登记即触发三问 |
| 插件升级改变规则语义 | 3.x 内部规则参数可能调整 | 版本钉在父 pom，升级视为一次显式提交 |
| pom 解析误判 | 正则解析 XML 不可靠 | 用标准库 XML 解析，只取 `dependencies` 下的坐标 |
| 清单与依赖漂移 | 漏登记会让构建红 | 这是设计意图：红了才说明有人加依赖没走准入 |

**待确认**

1. 前端传递依赖的许可证是否纳入——需 `pnpm licenses`，版本差异未验证，**本期不做**。
2. 是否把 `check-ui-boundaries.mjs` 的菜单层级 / `router-link` / 间距令牌检查一并纳入
   —— 属同一护栏主题但不在本次确认的范围内，**本期不做**，留作后续 change。

## R6 产物

- 调研文档：本文档。
- 绑定变更：`openspec/changes/2026-10-29-enforce-dependency-guard/proposal.md` 已引用本路径。

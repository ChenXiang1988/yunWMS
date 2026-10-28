# 依赖准入核对

核对「代码里用到的直接依赖」是否都在 `allowlist.json` 登记过。未登记即失败，
退出码非 0 让构建变红。

## 用法

```bash
python3 check_dependencies.py            # 人类可读输出
python3 check_dependencies.py --json     # 机器可读，供后续接入
python -m unittest discover -s tests     # 护栏自身的自检
```

零第三方依赖，只用标准库，秒级、离线可跑。CI 的 tools job 会执行它。

## 它管什么、不管什么

管：后端各模块 `pom.xml` 的 `<dependencies>`、前端根 / `apps/*` / `packages/*` 的
`package.json` 三类依赖段。**任何一项出现在代码里却不在清单里，构建失败。**

不管（有意留白，避免制造假精确）：

- 不做许可证判定。清单里的 `license` 是给人看的登记项，不是自动校验项——
  Maven 侧有一批依赖（sa-token、springdoc、swagger 注解、Flyway）压根不在 pom 里声明
  licenses 元数据，靠元数据判许可证只会制造假阴性。
- 不做传递依赖。坐标黑名单那层由后端 `maven-enforcer-plugin` 承担，它能
  `searchTransitive=true` 扫整棵依赖树，这是纯文本脚本做不到的。
- 不做 CVE 扫描。那需要外网下载漏洞库，与「秒级离线」冲突。

三层各管一段，不重叠：enforcer 答「是不是被点名禁止」，本工具答「有没有被评审过」，
`check-ui-boundaries.mjs` 答「前端 UI 与令牌是否越界」。

## 如何登记一个新依赖

在 `allowlist.json` 的 `backend` 或 `frontend` 段追加一条，四个字段都要填：

```json
{"coordinate": "group:artifact", "version": "x.y.z", "license": "Apache-2.0", "note": "为什么是它"}
```

`note` 就是 AGENTS.md 依赖准入三问的落点，必须回答：

1. **许可证**——允许商用与闭源分发吗？GPL / AGPL / SSPL 一律不得引入。
2. **最近维护时间**——还在发版吗？issue 有人回吗？星标数不作数。
3. **可否被现有依赖替代**——清单里已经有同类能力时，先说明为什么不复用。

`license` 的取值必须**实测**，不得凭印象：

- Maven：优先看 pom 的 `<licenses>` 段；没有就查项目 LICENSE 文件，
  或拆 jar 读 `META-INF/LICENSE.txt`（Flyway 就是这么定的：pom 无 licenses 段，
  jar 内的 LICENSE.txt 写明 Apache-2.0）。
- npm：`registry.npmjs.org/<包名>/latest` 的 `license` 字段。

**核实不到就填 `UNVERIFIED`**，并在 `note` 写明为什么核不到。它会作为警告出现但不阻断构建——
宁可暴露不确定性，也不要填一个看起来精确的假值。

## 为什么清单不再配一份 Markdown

两份清单必然漂移，而漂移的方向总是「一处更新了另一处没更新」。
可读性由 JSON 里的 `note` 字段承担，说明写在文件头的 `_comment` 里。

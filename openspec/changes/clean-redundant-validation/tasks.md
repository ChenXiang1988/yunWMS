# Tasks

## 1. 基线与清单

- [ ] 1.1 扫描 auth、master、app 三个模块的 `null` 判断与 `try/catch`，逐条按「重复判空 / 合法防御 / 无处理 catch / 跨边界转换 catch」归档到 `audit.md`，每条写明文件:行、归类、理由。验证：抽查 10 条，归类均可追溯到 `docs/coding.md` 的具体条款。
- [ ] 1.2 按清单拆分批次，确认每批 diff 不超过 400 行。验证：每批预估行数写进 `audit.md`，超限即再拆。

## 2. auth 整改

- [ ] 2.1 删除入口之后的重复判空与重复格式校验；标注「疑似语义承载」的不动。验证：`./mvnw -pl wms-auth -am test` 通过。
- [ ] 2.2 删除无处理动作的 catch（仅记录日志、原样重抛），保留 `GlobalExceptionHandler`、`SaTokenAnnotationConfig`、`RestAuthResponses` 的跨边界转换。验证：异常仍能冒泡到全局处理器，鉴权失败响应结构不变。
- [ ] 2.3 比对整改前后的错误码与错误文案，确认零变化。验证：`git diff` 逐条确认，无文案改动。

## 3. master 整改

- [ ] 3.1 删除重复判空，保留外部报文与库存、金额写入前的不变式。验证：`./mvnw -pl wms-master -am test` 通过。
- [ ] 3.2 清理无处理动作的 catch。验证：异常冒泡路径与整改前一致。
- [ ] 3.3 比对错误码与文案。验证：`git diff` 无文案改动。

## 4. app 整改

- [ ] 4.1 清理 bridge 装配层的重复判空，保留装配期必需的启动失败校验（配置缺失必须在启动时失败）。验证：`./mvnw -pl wms-app -am test` 通过。
- [ ] 4.2 删除无处理动作的 catch。验证：启动与装配路径行为不变。

## 5. 收口

- [ ] 5.1 运行后端全量 `./mvnw -B verify`。验证：编译、单测、架构门禁全部通过。
- [ ] 5.2 只读审计：确认无越出三模块的改动、无测试断言改动、无文案改动、单批行数合规。验证：产出 `completion.md`。
- [ ] 5.3 归档 change，删除 `change/clean-redundant-validation` 分支。

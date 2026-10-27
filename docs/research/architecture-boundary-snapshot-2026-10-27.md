# yunWMS 架构边界快照（模块—表—外键—Mapper）

- 审计时间：2026-10-27
- 时效：本文为 `main@865d2ae4be3fa5e4ec47d07994ab0e252eb3c264` 的只读快照。后续变化以仓库现状为准，本文不回改。
- 适用对象：后端四个 Maven 模块、Flyway 迁移、MyBatis Mapper XML 与当前数据库约束。
- 方法：只读源码、迁移与文档；未运行构建、测试或数据库。
- 决策记录：`openspec/changes/audit-architecture-boundaries/design.md`。

## 1. 模块与依赖

```text
wms-app → wms-auth   → wms-web
wms-app → wms-master → wms-web
```

- `wms-web` 不依赖业务模块。
- `wms-auth` 与 `wms-master` 无 Maven 依赖、无生产 Java import。
- `wms-app` 的 `bridge/` 有三处跨模块装配：`DictLookupAdapter`、`OwnerAccountCounterAdapter`、`OwnerCodeGeneratorAdapter`。
- 仓库未发现 Spring 事件或领域事件实现。

## 2. Mapper XML 全量清单

共 9 个 XML、25 条语句、5 个 `<sql>` 片段、10 个 Mapper 接口；`SysRoleMenuMapper` 无 XML。

| # | 模块 | XML / namespace | 语句与访问表 |
|---|---|---|---|
| 1 | auth | `mapper/auth/OwnerNameMapper.xml` / `OwnerNameMapper` | `selectNamesByIds`：`owner`（跨模块只读） |
| 2 | auth | `mapper/auth/SysCodeRuleMapper.xml` / `SysCodeRuleMapper` | `advance`：`sys_code_rule`（UPDATE…RETURNING） |
| 3 | auth | `mapper/auth/SysDictMapper.xml` / `SysDictMapper` | `selectTypeSummaries`：`sys_dict` LEFT JOIN `sys_dict_item`；`existsType`：`sys_dict`；`selectItems`、`selectItemsByTypes`：`sys_dict_item` |
| 4 | auth | `mapper/auth/SysMenuMapper.xml` / `SysMenuMapper` | `selectAllPermissionCodes`：`sys_menu`；`selectPermissionCodesByRoleIds`：`sys_role_menu` ⋈ `sys_menu` |
| 5 | auth | `mapper/auth/SysRoleMapper.xml` / `SysRoleMapper` | `countByFilter`：`sys_role`；`selectSummaryPage`、`selectSummaryById`：`sys_role` + `sys_user_role` + `sys_role_menu` |
| 6 | auth | `mapper/auth/SysUserMapper.xml` / `SysUserMapper` | `recordFailedAttempt`：`sys_user`（CTE + FOR UPDATE + UPDATE…RETURNING）；`isOnlyAvailableSuperAdmin`：`sys_user` ⋈ `sys_user_role` ⋈ `sys_role`；`countByFilter`、`selectSummaryPage`：`sys_user` |
| 7 | auth | `mapper/auth/SysUserRoleMapper.xml` / `SysUserRoleMapper` | `selectGrantsByUserIds`：`sys_user_role` ⋈ `sys_role` |
| 8 | master | `mapper/master/OwnerMapper.xml` / `OwnerMapper` | `countByFilter`、`selectPageByFilter`、`existsBySkuPrefix`、`existsByExternalCode`、`selectCodeBySkuPrefix`：`owner`；`updateProfile`、`updateStatus`、`softDelete`：`owner` 写 |
| 9 | master | `mapper/master/SkuCountMapper.xml` / `SkuCountMapper` | `countByOwnerId`：`sku`（模块内临时读，注释登记随商品域迁走） |

无 XML 的 Mapper：`SysRoleMenuMapper`，使用 MyBatis-Plus `BaseMapper`。全仓没有注解内联 SQL。

## 3. 24 张表与归属

归属依据 `docs/coding-project.md:20-23`。无法可靠判断的明确标注。

| # | 表 | DDL | 当前访问方 | 归属 / 备注 |
|---|---|---|---|---|
| 1 | `owner` | `V001__owner.sql:4` | master `OwnerMapper`；auth `OwnerNameMapper` 只读 id/name | wms-master；唯一跨模块表示例 |
| 2 | `sys_user` | `V002__sys_user.sql:5` | auth `SysUserMapper`；master 仅经端口查询账号数 | wms-auth；软删除货主账号仍可被 FK 允许 |
| 3 | `sys_password_reset_token` | `V003:4` | 无代码访问 | wms-auth |
| 4 | `sys_menu` | `V004:5` | auth `SysMenuMapper` | wms-auth |
| 5 | `sys_role` | `V005:3` | auth `SysRoleMapper` | wms-auth |
| 6 | `sys_role_menu` | `V005:26` | auth `SysRoleMenuMapper`、`SysMenuMapper`、`SysRoleMapper` | wms-auth |
| 7 | `sys_user_role` | `V005:33` | auth `SysUserRoleMapper`、`SysUserMapper`、`SysRoleMapper` | wms-auth |
| 8 | `sys_data_scope_owner` | `V006:10` | 无代码访问 | wms-auth；`owner_id` 引用 master 表 |
| 9 | `owner_contact` | `V007:4` | 无代码访问 | wms-master |
| 10 | `owner_assignee` | `V008:6` | 无代码访问 | 无法确认（master 候选）；同时引用 `owner` 与 `sys_user` |
| 11 | `owner_tax_registration` | `V009:4` | 无代码访问 | wms-master |
| 12 | `category` | `V010:4` | 无代码访问 | wms-master |
| 13 | `brand` | `V011:4` | 无代码访问 | wms-master |
| 14 | `sku` | `V012:8` | master `SkuCountMapper` 只做 count | wms-master |
| 15 | `sku_pending` | `V013:9` | 无代码访问 | wms-master |
| 16 | `sku_barcode` | `V014:6` | 无代码访问 | wms-master |
| 17 | `sku_pack` | `V015:8` | 无代码访问 | wms-master |
| 18 | `sku_import_task` | `V016:6` | 无代码访问 | wms-master |
| 19 | `label_print_record` | `V017:5` | 无代码访问 | 无法确认（商品/条码/打印域候选） |
| 20 | `sys_dict` | `V018:4` | auth `SysDictMapper` | wms-auth；master 经端口使用 |
| 21 | `sys_dict_item` | `V018:25` | auth `SysDictMapper` | wms-auth；master 经端口使用 |
| 22 | `sys_code_rule` | `V019:6` | auth `SysCodeRuleMapper` | 无法确认；取号当前暂放 auth，master 经端口使用 |
| 23 | `sys_config` | `V020:6` | 无代码访问 | 无法确认（全局配置候选） |
| 24 | `sys_audit_log` | `V021:6` | 无生产代码；仅开发工具与测试写入 | 无法确认（全局审计） |

无生产代码访问的表共 14 张：`sys_password_reset_token`、`sys_data_scope_owner`、`owner_contact`、`owner_assignee`、`owner_tax_registration`、`category`、`brand`、`sku_pending`、`sku_barcode`、`sku_pack`、`sku_import_task`、`label_print_record`、`sys_config`、`sys_audit_log`。

## 4. 外键全量清单

迁移共 29 条外键。除 `fk_sys_user_owner` 外均为 PostgreSQL 默认约束名。

| # | 引用列 | 目标 | 删除动作 | 定义 |
|---|---|---|---|---|
| 1 | `label_print_record.sku_id` | `sku(id)` | NO ACTION | `V017:7` |
| 2 | `label_print_record.owner_id` | `owner(id)` | NO ACTION | `V017:8` |
| 3 | `owner_tax_registration.owner_id` | `owner(id)` | NO ACTION | `V009:6` |
| 4 | `sku_barcode.sku_id` | `sku(id)` | CASCADE | `V014:8` |
| 5 | `sku_barcode.owner_id` | `owner(id)` | NO ACTION | `V014:9` |
| 6 | `brand.owner_id` | `owner(id)` | NO ACTION | `V011:6` |
| 7 | `sku.owner_id` | `owner(id)` | NO ACTION | `V012:13` |
| 8 | `sku.category_id` | `category(id)` | NO ACTION | `V012:18` |
| 9 | `sku.brand_id` | `brand(id)` | NO ACTION | `V012:19` |
| 10 | `sys_role_menu.role_id` | `sys_role(id)` | CASCADE | `V005:27` |
| 11 | `sys_role_menu.menu_id` | `sys_menu(id)` | NO ACTION | `V005:28` |
| 12 | `sys_user_role.user_id` | `sys_user(id)` | CASCADE | `V005:34` |
| 13 | `sys_user_role.role_id` | `sys_role(id)` | CASCADE | `V005:35` |
| 14 | `sys_password_reset_token.user_id` | `sys_user(id)` | NO ACTION | `V003:6` |
| 15 | `owner_contact.owner_id` | `owner(id)` | NO ACTION | `V007:6` |
| 16 | `sys_data_scope_owner.role_id` | `sys_role(id)` | CASCADE | `V006:12` |
| 17 | `sys_data_scope_owner.user_id` | `sys_user(id)` | CASCADE | `V006:13` |
| 18 | `sys_data_scope_owner.owner_id` | `owner(id)` | NO ACTION | `V006:14` |
| 19 | `sys_menu.parent_id` | `sys_menu(id)` | NO ACTION | `V004:7` |
| 20 | `category.parent_id` | `category(id)` | NO ACTION | `V010:6` |
| 21 | `owner_assignee.owner_id` | `owner(id)` | NO ACTION | `V008:8` |
| 22 | `owner_assignee.user_id` | `sys_user(id)` | NO ACTION | `V008:9` |
| 23 | `sku_pack.sku_id` | `sku(id)` | CASCADE | `V015:10` |
| 24 | `sys_dict_item.dict_type` | `sys_dict(dict_type)` | NO ACTION | `V018:27` |
| 25 | `sku_pending.sku_id` | `sku(id)` | CASCADE | `V013:10` |
| 26 | `sku_pending.category_id` | `category(id)` | NO ACTION | `V013:14` |
| 27 | `sku_pending.brand_id` | `brand(id)` | NO ACTION | `V013:15` |
| 28 | `sku_import_task.owner_id` | `owner(id)` | NO ACTION | `V016:8` |
| 29 | `sys_user.owner_id` | `owner(id)` | NO ACTION | `V024:23-24`，约束 `fk_sys_user_owner` |

### 跨模块外键

- `sys_user.owner_id → owner.id`：auth 表引用 master 表；用于兜住不存在的货主 ID，不覆盖软删除。
- `sys_data_scope_owner.owner_id → owner.id`：auth 表引用 master 表；当前无代码访问。
- `owner_assignee.user_id → sys_user.id`：master 表引用 auth 表；完整清单新增发现。

三个外键的处置见 `design.md` D3：全部保留。

## 5. OwnerNameMapper 事实

- 唯一跨模块 Mapper 读取：`wms-auth/src/main/resources/mapper/auth/OwnerNameMapper.xml:14-16`。
- 已收口到 `OwnerNameQuery`，实现仍在 auth：`OwnerNameQueryImpl.java`。
- 替换条件未满足：master 当前没有“按 ID 批量取 id+name 且包含已软删除记录”的能力；现有 `findById` 是单条聚合查询，`findPage` 排除软删除。
- 处置见 `design.md` D2：保留为登记债务，补齐 master 能力后经 `wms-app` bridge 替换。

## 6. 未确认项

- `owner_assignee`、`label_print_record`、`sys_config`、`sys_audit_log`、`sys_code_rule` 的最终模块归属。
- 14 张无生产代码访问表与 DDL 的完整一致性。
- 是否曾修改历史迁移；本次未检查完整 Git 历史。
- 运行时外键行为与迁移回滚；本次未运行数据库。

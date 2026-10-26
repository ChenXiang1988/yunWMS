-- 客户管理的菜单与权限点
--
-- 结构：M 客户管理（顶层，parent_id IS NULL）→ C 货主档案 → 三个 F 操作权限点。
--
-- 为什么挂在顶层而不是 M 系统管理下：货主是业务主数据，不是平台治理能力。挂在系统管理下会让
-- 「谁有系统管理权限」与「谁能建档客户」变成同一个开关，而这两件事的责任人通常不是同一个角色。
-- 顶层目录因此与 M 系统管理同级（V025 把系统管理放在 sort_no 900，这里取 100，让业务侧排在前面）。
--
-- 为什么现在就把三个权限点建齐：3.1a 只做领域模型与持久化，接口在 3.1b，三个点在本批都没有消费者。
-- 这与 V026/V027/V029「只插已实现接口要消费的权限点」的口径不同，取舍是：菜单与权限点一次建齐，
-- 3.1b 落地时不必再补一份迁移去改父节点与 sort_no（已生效的迁移禁止修改）。代价是库里多三条
-- 暂时无人消费的权限点，与 V031 处理 system:dict:update 的方式一致。
--
-- 三个点各管一件事，不合并：
--   system:owner:list   列表与详情共用。能看到列表的人本来就能点进去看详情，拆开只多一次配置。
--   system:owner:create 新建货主。它触发的副作用（取号、占用 SKU 前缀、后续开通主账号）与其他两个不同。
--   system:owner:update 编辑货主。草稿提交、字段锁定与状态流转（3.2）都落在这个点上。
--
-- 父节点用子查询取，不写死 id：id 是 GENERATED ALWAYS AS IDENTITY，写死要求本文件必须按某个
-- 隐式顺序执行，而迁移顺序只由版本号保证。
--
-- 幂等：M/C 两层没有唯一约束，用 (menu_type, path) 判存在；F 层由 ux_sys_menu_perms 兜底，
-- 但同样先判重，避免重复执行时靠异常发现「本来就该拒绝」这件事。

-- ① 客户管理目录（顶层）
INSERT INTO sys_menu (parent_id, menu_type, name, path, sort_no)
SELECT NULL, 'M', '客户管理', '/master', 100
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE menu_type = 'M' AND path = '/master'
);

-- ② 货主档案菜单
INSERT INTO sys_menu (parent_id, menu_type, name, path, sort_no)
SELECT p.id, 'C', '货主档案', '/master/owners', 10
FROM sys_menu p
WHERE p.menu_type = 'M'
  AND p.path = '/master'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE menu_type = 'C' AND path = '/master/owners'
  );

-- ③ 货主列表（列表与详情共用）
INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id, 'F', '货主列表', 'system:owner:list', 10
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/master/owners'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:owner:list'
  );

-- ④ 新建货主
INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id, 'F', '新建货主', 'system:owner:create', 20
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/master/owners'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:owner:create'
  );

-- ⑤ 编辑货主
INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id, 'F', '编辑货主', 'system:owner:update', 30
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/master/owners'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:owner:update'
  );

-- ⑥ 刻意不写 sys_role_menu
--
-- 内置超级管理员的权限在鉴权时按「持有 builtin 角色」动态展开为 sys_menu 里的全部权限点，
-- 不落授权数据；一旦这里补了授权行，新增权限点时就会漏补且不报错——超管会突然少一个权限。
-- 新建角色由管理员在界面上勾选，同样不在这里预置。

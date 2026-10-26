-- 数据字典的菜单与权限点
--
-- 菜单：C 数据字典（path=/system/dicts）挂在 M 系统管理下，sort_no 接用户管理 10、角色管理 20 之后取 30。
-- 权限点两个：
--   system:dict:list   被本批的三个读接口消费：类型列表、某类字典项、批量读取。三者共用一点——
--                      能看到字典类型列表的人本来就能逐类点进去看项，拆成三个点只会多两次配置而没有边界收益。
--   system:dict:update 留给 2.7b 的新增/编辑/启停。它在本批还没有消费者，这与 V026/V027/V029
--                      「只插已实现接口要消费的权限点」的口径不同：本批把菜单与两个权限点一次建齐，
--                      是为了让 2.7b 不必回来改一次菜单结构（改已生效的迁移是被禁止的，
--                      只能再补一份迁移去调 sort_no 与父节点）。代价是库里多一条暂时无人消费的权限点。
--
-- 父节点用子查询取，不写死 id：id 是 GENERATED ALWAYS AS IDENTITY，
-- 写死要求本文件必须按某个隐含顺序执行，而迁移顺序只由版本号保证。
--
-- 幂等：M/C 两层没有唯一约束，用 (menu_type, path) 判存在；F 层由 ux_sys_menu_perms 兜底，
-- 但同样先判重，避免重复执行时靠异常发现「本来就该拒绝」这件事。

-- ① 数据字典菜单，挂在 M 系统管理下
INSERT INTO sys_menu (parent_id, menu_type, name, path, sort_no)
SELECT p.id, 'C', '数据字典', '/system/dicts', 30
FROM sys_menu p
WHERE p.menu_type = 'M'
  AND p.path = '/system'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE menu_type = 'C' AND path = '/system/dicts'
  );

-- ② 字典查询权限点（三个读接口共用）
INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id, 'F', '字典查询', 'system:dict:list', 10
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/system/dicts'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:dict:list'
  );

-- ③ 字典维护权限点（2.7b 的新增/编辑/启停消费）
INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id, 'F', '字典维护', 'system:dict:update', 20
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/system/dicts'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:dict:update'
  );

-- ④ 刻意不写 sys_role_menu
--
-- 内置超级管理员的权限在鉴权时按「持有 builtin 角色」动态展开为 sys_menu 里的全部权限点，
-- 不落授权数据；一旦这里补了授权行，新增权限点时就会漏补且不报错——超管会突然少一个权限。
-- 新建角色由管理员在界面上勾选，同样不在这里预置。

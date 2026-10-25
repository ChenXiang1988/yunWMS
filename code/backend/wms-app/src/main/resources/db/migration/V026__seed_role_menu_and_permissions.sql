-- 角色管理菜单 + 两个操作权限点
--
-- 只插本批已实现接口要消费的两个权限点（列表、新建）：权限点的唯一用途是被接口消费。
-- 编辑、启停、删除属于 2.2b2，那时再补 system:role:update / system:role:delete，
-- 现在插进去会得到一批「授权了也做不了事」的记录。
--
-- 父节点一律用子查询取 id，不写死：id 是 GENERATED ALWAYS AS IDENTITY，
-- 写死要求本文件必须按某个隐含顺序执行，而迁移顺序只由版本号保证。
--
-- 幂等：M/C 两层没有唯一约束，用 (menu_type, path) 判存在；F 层由 ux_sys_menu_perms 兜底，
-- 但同样先判重，避免重复执行时靠异常发现「本来就该拒绝」这件事。

-- ① 角色管理菜单，挂在 M 系统管理下
INSERT INTO sys_menu (parent_id, menu_type, name, path, sort_no)
SELECT p.id, 'C', '角色管理', '/system/roles', 20
FROM sys_menu p
WHERE p.menu_type = 'M'
  AND p.path = '/system'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE menu_type = 'C' AND path = '/system/roles'
  );

-- ② 角色列表权限点
INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id, 'F', '角色列表', 'system:role:list', 10
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/system/roles'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:role:list'
  );

-- ③ 新增角色权限点
INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id, 'F', '新增角色', 'system:role:create', 20
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/system/roles'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:role:create'
  );

-- ④ 刻意不写 sys_role_menu
--
-- 内置超级管理员的权限在鉴权时按「持有 builtin 角色」动态展开为 sys_menu 里的全部权限点，
-- 不落授权数据；一旦这里补了授权行，新增权限点时就会漏补且不报错——超管会突然少一个权限。
-- 新建角色由管理员在界面上勾选，同样不在这里预置。

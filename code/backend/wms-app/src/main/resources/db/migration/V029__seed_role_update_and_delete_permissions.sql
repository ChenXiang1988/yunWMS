-- 角色管理补两个操作权限点：编辑、删除
--
-- 只插本批新接口要消费的两个（与 V025 / V027 / V028 同一口径：权限点的唯一用途是被接口消费）。
-- 启用停用复用 system:role:update，不另设权限点：编辑与启停改的都是「这个角色本身」，
-- 拆成两个点只会让管理员在配置页里多勾一次，而任何一次漏勾都表现为「按钮点了没反应」。
--
-- 删除独立成点：它的后果比编辑重——角色删除后从管理界面消失（软删除，列表查不到），
-- 而编辑改名随时可以改回。两者的风险等级不同，不能共用授权。
--
-- 父节点用子查询取，不写死 id：id 是 GENERATED ALWAYS AS IDENTITY，
-- 写死要求本文件必须按某个隐含顺序执行，而迁移顺序只由版本号保证。
--
-- 幂等：F 层由 ux_sys_menu_perms 兜底，但仍先判重，避免重复执行时靠异常发现「本来就该拒绝」这件事。

-- ① 编辑角色权限点（同时被启用停用消费）
INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id, 'F', '编辑角色', 'system:role:update', 30
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/system/roles'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:role:update'
  );

-- ② 删除角色权限点
INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id, 'F', '删除角色', 'system:role:delete', 40
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/system/roles'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:role:delete'
  );

-- ③ 刻意不写 sys_role_menu
--
-- 内置超级管理员的权限在鉴权时按「持有 builtin 角色」动态展开为 sys_menu 里的全部权限点，
-- 不落授权数据；一旦这里补了授权行，新增权限点时就会漏补且不报错——超管会突然少一个权限。
-- 新建角色由管理员在界面上勾选，同样不在这里预置。

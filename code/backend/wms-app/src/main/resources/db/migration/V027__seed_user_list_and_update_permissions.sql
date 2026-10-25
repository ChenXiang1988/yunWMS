-- 用户管理补两个操作权限点：列表、编辑
--
-- 只插本批已实现接口要消费的两个：权限点的唯一用途是被接口消费，插进去而没有接口挂它，
-- 库里就多一批「授权了也做不了事」的记录（与 V025 / V026 同一口径）。
-- 编辑、启停、重置密码属于 2.1c，那时再按接口补 system:user:disable / system:user:reset-password。
--
-- system:user:update 本批被「分配角色」消费：角色决定这个人能做什么，
-- 能改别人的角色等于能改别人的权限，因此它必须单独受控，不能借用建号权限。
--
-- 父节点用子查询取，不写死 id：id 是 GENERATED ALWAYS AS IDENTITY，
-- 写死要求本文件必须按某个隐含顺序执行，而迁移顺序只由版本号保证。
--
-- 幂等：F 层由 ux_sys_menu_perms 兜底，但仍先判重，避免重复执行时靠异常发现「本来就该拒绝」这件事。

-- ① 用户列表权限点
INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id, 'F', '用户列表', 'system:user:list', 5
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/system/users'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:user:list'
  );

-- ② 编辑用户权限点（本批用于分配角色）
INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id, 'F', '编辑用户', 'system:user:update', 20
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/system/users'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:user:update'
  );

-- ③ 刻意不写 sys_role_menu
--
-- 内置超级管理员的权限在鉴权时按「持有 builtin 角色」动态展开为 sys_menu 里的全部权限点，
-- 不落授权数据；一旦这里补了授权行，新增权限点时就会漏补且不报错——超管会突然少一个权限。
-- 新建角色由管理员在界面上勾选，同样不在这里预置。

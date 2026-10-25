-- 最小权限点树 + 内置超级管理员角色
--
-- 只插当前已实现接口对应的权限点（建号一个）。一次性插满 27 个权限点看起来更像「完成」，
-- 但那些权限点没有接口可挂，库里会留下一批「授权了也做不了事」的记录，
-- 将来实现对应接口时也没人能确认它是否真的被挂上——权限点的唯一用途就是被接口消费。
--
-- 目录（M）与菜单（C）两层只为让操作权限点在将来的配置界面里有归属，它们自身不带 perms。
--
-- 本文件保持幂等：M/C 两层没有唯一约束，用 (menu_type, path) 做存在性判断；
-- 操作权限点由 ux_sys_menu_perms 兜底，但同样用 NOT EXISTS 判重，避免重复执行时靠异常发现。
-- 父节点用子查询取，不写死 id：id 是 GENERATED ALWAYS AS IDENTITY，写死要求本文件必须第一个跑，
-- 而迁移的执行顺序是由版本号保证的，不该再靠 id 猜。

-- ① 系统管理目录
INSERT INTO sys_menu (parent_id, menu_type, name, path, sort_no)
SELECT NULL, 'M', '系统管理', '/system', 900
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu WHERE menu_type = 'M' AND path = '/system'
);

-- ② 用户管理菜单
INSERT INTO sys_menu (parent_id, menu_type, name, path, sort_no)
SELECT p.id, 'C', '用户管理', '/system/users', 10
FROM sys_menu p
WHERE p.menu_type = 'M'
  AND p.path = '/system'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE menu_type = 'C' AND path = '/system/users'
  );

-- ③ 新增用户操作权限点（perms 只在 F 层填）
INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id, 'F', '新增用户', 'system:user:create', 10
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/system/users'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:user:create'
  );

-- ④ 内置超级管理员角色
--
-- builtin = true 是「鉴权时动态展开为全部权限点」的唯一开关，见 SaTokenPermissionAdapter 的说明。
-- data_scope_type = 'ALL' 对齐规格「超级管理员的数据范围以 ALL 标记表示」，2.3 才会消费它。
INSERT INTO sys_role (code, name, builtin, data_scope_type, status, remark)
SELECT 'SUPER_ADMIN', '超级管理员', true, 'ALL', 'ACTIVE', '内置角色，不可编辑不可删除'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'SUPER_ADMIN');

-- ⑤ 把初始账号挂到该角色上
--
-- 用 ON CONFLICT 而不是 NOT EXISTS：sys_user_role 的主键就是 (user_id, role_id)，
-- 这里恰好是「已有唯一约束可用」的情形，把判重交给数据库比再查一遍更准（并发下也成立）。
-- 按 lower(login_name) 匹配，与 sys_user 上那个表达式唯一索引的口径一致。
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
         CROSS JOIN sys_role r
WHERE lower(u.login_name) = 'superadmin'
  AND r.code = 'SUPER_ADMIN'
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ⑥ 刻意不写 sys_role_menu
--
-- 超管的权限在鉴权时按「持有 builtin 角色」动态展开为 sys_menu 里全部权限点，不落授权数据。
-- 一旦把行写进 sys_role_menu，新增权限点时就得回来补一条数据，而漏补时不会有任何报错——
-- 超管会突然少一个权限。规格明确禁止这种做法，因此这里一条都不写。

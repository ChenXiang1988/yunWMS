-- 用户管理补一个操作权限点：重置密码
--
-- 只插本批新接口要消费的那一个（与 V025 / V027 同一口径：权限点的唯一用途是被接口消费）。
-- 编辑与启用停用复用 V027 已建的 system:user:update，不重复插入：
-- 三者改的都是「这个账号本身」，拆成三个权限点只会让管理员在配置页里多勾几次，
-- 而任何一次漏勾都表现为「按钮点了没反应」。
--
-- 重置密码独立成点，是因为它比编辑更进一步：拿到它等于能替任何人设置凭据，
-- 而编辑只能改联系方式。两者的风险等级不同，不能共用授权。
--
-- sort_no 取 30：与同一菜单下 列表(5) / 新增(10) / 编辑(20) 的顺序一致，新点在最后。
--
-- 父节点用子查询取，不写死 id：id 是 GENERATED ALWAYS AS IDENTITY，
-- 写死要求本文件必须按某个隐含顺序执行，而迁移顺序只由版本号保证。
--
-- 幂等：F 层由 ux_sys_menu_perms 兜底，但仍先判重，避免重复执行时靠异常发现「本来就该拒绝」这件事。

INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id, 'F', '重置密码', 'system:user:reset', 30
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/system/users'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:user:reset'
  );

-- 刻意不写 sys_role_menu：内置超管的权限在鉴权时按「持有 builtin 角色」动态展开为 sys_menu
-- 里的全部权限点，不落授权数据（理由见 V025 第 ⑥ 段）。新建角色由管理员在界面上勾选。

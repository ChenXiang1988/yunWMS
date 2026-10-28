-- 用户管理补一个操作权限点：踢下线
--
-- 只插本批新接口要消费的那一个（与 V025 / V027 / V028 / V040 同一口径：权限点的唯一用途是被接口消费）。
--
-- 为什么踢下线不复用 system:user:update：
-- 它是安全动作，与「改资料」不是同一件事。能给员工改备注、改联系方式、启停账号的角色，
-- 不一定要有「把某个在线用户立刻赶下线」的能力；反过来，应急时只想把「踢下线」单独授出去，
-- 也不该顺带把整套账号编辑权限一并交出。合并成一个点之后，这两种授权都做不到。
-- 重置密码独立成点（V028）是同一类判断：这也是「作用在别人凭据或会话上」的动作，风险等级不同。
--
-- 编码取 system:user:kick：本仓库自 V033 起操作权限点统一用 system: 前缀，
-- 动作段用动词原形，鉴权按库里的编码逐字比对，前后端必须同串。
--
-- sort_no 取同级 F 节点的当前最大值 + 10，不写死 40：本文件排在 V028 之后只是当前的事实。
-- 多条分支并行时，另一条分支完全可能也给同一个父节点加 F 节点并从「30 之后的 40」开始写；
-- (parent_id, sort_no) 没有唯一约束，两行 40 会静默共存，界面上两个按钮的先后变成偶然。
-- 动态取最大值后，谁后执行谁拿下一个号，合并顺序不同也不会撞车。
--
-- 父节点用子查询取，不写死 id：id 是 GENERATED ALWAYS AS IDENTITY，写死要求本文件必须按某个
-- 隐含顺序执行，而迁移顺序只由版本号保证。C 节点由 V025 建立，先后由版本号保证。
--
-- 幂等：F 层由 ux_sys_menu_perms 兜底，但仍先判重，避免重复执行时靠异常发现「本来就该拒绝」这件事。

INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id,
       'F',
       '踢下线',
       'system:user:kick',
       COALESCE(
           (SELECT max(s.sort_no) FROM sys_menu s
            WHERE s.parent_id = p.id AND s.menu_type = 'F'),
           0) + 10
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/system/users'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:user:kick'
  );

-- 刻意不写 sys_role_menu：内置超管的权限在鉴权时按「持有 builtin 角色」动态展开为 sys_menu
-- 里的全部权限点，不落授权数据（理由见 V025 第 ⑥ 段）。新建角色由管理员在界面上勾选。

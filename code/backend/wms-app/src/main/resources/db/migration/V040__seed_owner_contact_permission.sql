-- 客户管理：补「维护联系人」权限点
--
-- 结构不变：M 客户管理 → C 货主档案 → 第六个 F 操作权限点（V033 建前三个、V036 第四个、V037 第五个）。
--
-- 为什么联系人单独成点，不复用 system:owner:update：
-- 联系人是货主的独立子表（owner_contact），登记的是「找谁」——姓名、电话、邮箱与角色，
-- 与货主主体的编码、结算主体、注册地址不在同一份表单里提交。合并成一个点之后，任何被授予
-- 「改个电话」的角色都能顺手改掉结算主体（V033 第 3 段已把 update 点定义成「编辑货主」）；
-- 联系人变更往往由不同岗位的人员发起、影响的是对外沟通链路，拆开才能分别授予与收回。
-- 权限矩阵对该能力只列了一个点（维护联系人），不拆 CRUD，因此这里只插 1 个 F 节点。
--
-- 权限点编码：权限矩阵写的是 owner:contact:edit，本仓库自 V033 起的实现统一用 system:owner: 前缀，
-- 这里沿用实现前缀 system:owner:contact:edit——鉴权按库里的编码逐字比对，前后端必须同串；
-- 存量权限点编码本次不动（已生效的迁移禁止修改）。
--
-- 只插 F 节点、不插 sys_role_menu：内置超级管理员的权限在鉴权时按「持有 builtin 角色」动态展开
-- 为 sys_menu 里的全部权限点，一旦这里补授权行，新增权限点时就会漏补且不报错。理由与 V033 第 ⑥ 段一致。
--
-- 父节点用子查询取，不写死 id：id 是 GENERATED ALWAYS AS IDENTITY，写死要求本文件按某个隐式
-- 顺序执行，而迁移顺序只由版本号保证。C 节点由 V033 建立，V039 已先于本文件执行，依赖由版本号保证。
--
-- sort_no 取同级 F 节点的当前最大值 + 10，不写死 60：本文件排在 V037 之后只是当前的事实。
-- 多条分支并行时，另一条分支完全可能也给同一个父节点加 F 节点并从「50 之后的 60」开始写；
-- (parent_id, sort_no) 没有唯一约束，两行 60 会静默共存，界面上两个按钮的先后变成偶然。
-- 动态取最大值后，谁后执行谁拿下一个号，合并顺序不同也不会撞车。
--
-- 幂等：F 层由 ux_sys_menu_perms 兜底，但同样先判重，避免重复执行时靠异常发现「本来就该拒绝」这件事。

INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id,
       'F',
       '维护联系人',
       'system:owner:contact:edit',
       COALESCE(
           (SELECT max(s.sort_no) FROM sys_menu s
            WHERE s.parent_id = p.id AND s.menu_type = 'F'),
           0) + 10
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/master/owners'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:owner:contact:edit'
  );

-- 客户管理：补「删除货主」权限点
--
-- 结构不变：M 客户管理 → C 货主档案 → 第四个 F 操作权限点（V033 已建前三个）。
--
-- 为什么删除单独成点，不复用 system:owner:update：
-- 编辑一条主数据与让它从管理界面消失，风险等级不同。合并之后，任何被授予「改客户电话」的角色
-- 都能顺手删掉一条草稿货主，而删除不弹影响清单、重建的是一条「编码已占用、前缀已占用」的新档案
-- （ux_owner_code / ux_owner_sku_prefix 都是全表唯一索引，软删除不释放它们）。
-- 拆分让「谁能删」与「谁能改」成为两个可分别授予的开关。
--
-- 只插 F 节点、不插 sys_role_menu：内置超级管理员的权限在鉴权时按「持有 builtin 角色」动态展开为
-- sys_menu 里的全部权限点，一旦这里补授权行，新增权限点时就会漏补且不报错。理由与 V033 第 ⑥ 段一致。
--
-- 父节点用子查询取，不写死 id：id 是 GENERATED ALWAYS AS IDENTITY，写死要求本文件按某个隐式顺序执行，
-- 而迁移顺序只由版本号保证。
--
-- 幂等：F 层由 ux_sys_menu_perms 兜底，但同样先判重，避免重复执行时靠异常发现「本来就该拒绝」这件事。

INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id, 'F', '删除货主', 'system:owner:delete', 40
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/master/owners'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:owner:delete'
  );

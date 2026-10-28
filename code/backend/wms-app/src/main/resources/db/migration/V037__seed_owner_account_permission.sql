-- 客户管理：补「开通主账号」权限点
--
-- 结构不变：M 客户管理 → C 货主档案 → 第五个 F 操作权限点（V033 建前三个、V036 建第四个）。
--
-- 为什么开号单独成点，不复用 system:owner:update：
-- 开号会创建一条**能登录货主端**的账号，并给它一个由操作人设定的初始密码。这与「改个结算主体」
-- 的风险等级不同：拿到 update 的人可以给自己熟悉的货主开一个能进系统的账号，而整个过程在货主
-- 列表上看不出任何痕迹（货主字段一个都没变）。拆开让「谁能开号」可单独授予与收回。
--
-- 只插 F 节点、不插 sys_role_menu：内置超级管理员的权限在鉴权时按「持有 builtin 角色」动态展开
-- 为 sys_menu 里的全部权限点。理由与 V033 第 ⑥ 段、V036 一致。
--
-- 父节点用子查询取，不写死 id：id 是 GENERATED ALWAYS AS IDENTITY，写死要求本文件按某个隐式
-- 顺序执行，而迁移顺序只由版本号保证。
--
-- 幂等：F 层由 ux_sys_menu_perms 兜底，但仍先判重，避免重复执行时靠异常发现「本来就该拒绝」。

INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id, 'F', '开通主账号', 'system:owner:account', 50
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/master/owners'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:owner:account'
  );

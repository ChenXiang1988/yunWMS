-- 审计日志：补「来源端」列，并补查询端点要消费的菜单与权限点
--
-- 为什么来源端必须单独成列，而不是从操作人的主体类型推导：
-- 规格要求每条日志 MUST 包含来源端（platform-administration「审计日志」），审计记录的是**当时发生的事实**。
-- 主体类型回答的是「这个账号能进哪个端」，与「这次请求从哪个端发出」不是同一件事：一旦将来允许
-- 跨端访问（例如内部员工持同一账号进两端，或某天开放代操作），推导就会静默得出错误结论，
-- 而错误结论写在审计里是没有痕迹的——回查时没人知道那一列是推出来的还是记下来的。
--
-- 允许 NULL：历史数据没有这个值（补数据只能靠猜），而且系统内部触发的操作（定时任务、回调）
-- 本来就没有「端」这个概念。CHECK 在 NULL 上求值为 NULL、不视为违反，因此本约束只约束有值的情形。
--
-- 按对象倒序查询的索引不在这里新建：V021 已建 ix_audit_biz (biz_type, biz_id, created_at DESC)，
-- 正是本端点 WHERE biz_type = ? AND biz_id = ? ORDER BY created_at DESC 的形状。
-- 再加一条同列同序的索引不会让查询更快，只会让每次写入多维护一棵 B 树。

ALTER TABLE sys_audit_log ADD COLUMN source_portal varchar(16);

ALTER TABLE sys_audit_log ADD CONSTRAINT ck_audit_source_portal
    CHECK (source_portal IN ('CONSOLE', 'PORTAL'));

COMMENT ON COLUMN sys_audit_log.source_portal IS
    '请求来源端：CONSOLE 仓网端 / PORTAL 货主端；系统内部触发为空';

-- ① 审计日志菜单，挂在 M 系统管理下（与 C 用户管理 / C 角色管理 / C 数据字典同级）
--
-- 父节点用子查询取，不写死 id：id 是 GENERATED ALWAYS AS IDENTITY，写死要求本文件按某个隐式顺序
-- 执行，而迁移顺序只由版本号保证。
INSERT INTO sys_menu (parent_id, menu_type, name, path, sort_no)
SELECT p.id, 'C', '审计日志', '/system/audit-logs', 40
FROM sys_menu p
WHERE p.menu_type = 'M'
  AND p.path = '/system'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE menu_type = 'C' AND path = '/system/audit-logs'
  );

-- ② 日志查询权限点
--
-- 只插 F 节点、不插 sys_role_menu：内置超级管理员的权限在鉴权时按「持有 builtin 角色」动态展开为
-- sys_menu 里的全部权限点。理由与 V026 第 ④ 段、V033、V036 一致。
--
-- 幂等：F 层由 ux_sys_menu_perms 兜底，但仍先判重，避免重复执行时靠异常发现「本来就该拒绝」。
INSERT INTO sys_menu (parent_id, menu_type, name, perms, sort_no)
SELECT p.id, 'F', '日志查询', 'system:audit:list', 10
FROM sys_menu p
WHERE p.menu_type = 'C'
  AND p.path = '/system/audit-logs'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu WHERE perms = 'system:audit:list'
  );

-- ③ 刻意不提供删除与修改权限点
--
-- 规格场景「日志不可删除」的满足方式是**结构上没有这条路径**：没有 DELETE 端点、没有 UPDATE 端点，
-- 也就不需要（也不应该存在）「审计日志删除」这样的权限点——插进去等于给界面留了一个可以被授出去的入口。

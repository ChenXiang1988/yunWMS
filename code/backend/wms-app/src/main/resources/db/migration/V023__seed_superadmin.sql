-- 初始超级管理员账号
--
-- 为什么需要：V001–V022 只建结构、不写数据，空库启动后 sys_user 为空，
-- 没有任何凭据能换到第一张令牌，系统在「第一个账号被建出来」之前完全不可用。
--
-- 初始密码：Admin@1234（11 位，含大写、小写与数字，满足密码策略的长度 8–20 位与三类字符要求）。
-- 这里写死 BCrypt 哈希而不是明文：明文一旦进仓库就是永久泄露，改密也换不回已经外流的记录。
-- 哈希由生产同款的 BCryptPasswordEncoder 以默认代价因子 10 生成，盐在生成时已固定；
-- 因此本行不可被「重新生成一份哈希」原地替换——换了哈希，已经发出去的初始密码就登不进来了。
-- must_change_password = true 是规格「首次登录必须强制改密」的落点：
-- 初始密码只够换一次登录，改密后该标记才由改密流程清掉。
--
-- 不分配角色：角色与接口权限属于 2.2，此处预置角色只能靠猜，将来必然要迁移一次；
-- 登录链路不查角色（见 PasswordLoginService），没有角色不影响本账号登入。
--
-- created_by / updated_by 留空：种子数据没有操作人，填 0 会指向一个不存在的用户，
-- 而这两列在全库的含义是「谁做的这次变更」，写一个假 id 比留空更难解释。
--
-- 幂等：唯一索引建在 lower(login_name) 这个表达式上，ON CONFLICT 按列名匹配不到它，
-- 故用 WHERE NOT EXISTS 判重；本文件被重复执行时不会产生第二个账号，也不会撞唯一索引。
INSERT INTO sys_user (login_name, display_name, subject_type, password_hash,
                      must_change_password, status, email)
SELECT 'superadmin', '超级管理员', 'STAFF',
       '$2a$10$KH/tm7Sex.d0LfZvfMMm0uvY0n3W0WhNtBLQUZyX2ktk0LKde.Bv.',
       true, 'ACTIVE', 'admin@yunwms.local'
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE lower(login_name) = 'superadmin');

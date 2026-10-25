-- 补 sys_user 的两处模型缺陷
--
-- 1) wecom_user_id
--    规格「用户管理」要求内部员工 MUST 记录邮箱与企业微信 userid，用于应用消息推送。
--    推送能力本身后置（本期不实现），但「记录」是规格义务，不是推送的实现细节：
--    字段现在补齐，将来接推送时不必为一个本该存在的列再迁一次表、再回填一次历史账号。
--    可空，且不回填任何数据：现有行（superadmin）没有企业微信账号可填，
--    而货主账号走邮件通知，不使用该字段。
--
-- 2) fk_sys_user_owner
--    此前 owner_id 无外键，传入一个不存在的货主也能建号成功。后果是账号指向一个不存在的货主：
--    登录能过，之后所有按货主过滤的查询都查不到数据，且没有任何线索指向「建号时填错了 id」。
--    wms-auth 不能依赖 wms-master，应用层没有能力查货主是否存在，只能由数据库兜底。
--    CHECK ck_user_subject 只保证「货主账号必须有 owner_id」，保证不了「这个 owner 真的存在」。
--
--    现有数据只有 superadmin，其 owner_id 为 NULL，而外键对 NULL 不生效，本迁移不会失败。
--
-- 建索引不加：ix_sys_user_owner 已是 (owner_id) 上的部分索引，条件 owner_id IS NOT NULL，
-- 外键在 owner 侧删除时的反查条件是 owner_id = ?，等式已蕴含该谓词，可以走这条索引。

ALTER TABLE sys_user ADD COLUMN wecom_user_id varchar(64);

ALTER TABLE sys_user
    ADD CONSTRAINT fk_sys_user_owner FOREIGN KEY (owner_id) REFERENCES owner (id);

COMMENT ON COLUMN sys_user.wecom_user_id IS
    '企业微信 userid，内部员工用于应用消息推送；货主账号不使用，可为空';

COMMENT ON CONSTRAINT fk_sys_user_owner ON sys_user IS
    '货主账号的 owner_id 必须指向存在的货主；内部员工该列为空，不受本约束影响';

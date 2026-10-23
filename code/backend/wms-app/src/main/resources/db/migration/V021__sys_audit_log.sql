-- 审计日志
--
-- 只追加，不修改、不删除，因此不加 updated_at 与 deleted_at。
-- 首期不做分区；数据量上去后按 created_at 做月分区，主键需改成 (id, created_at)。

CREATE TABLE sys_audit_log (
    id             bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    biz_type       varchar(32)  NOT NULL,
    biz_id         varchar(64)  NOT NULL,
    action         varchar(32)  NOT NULL,

    operator_id    bigint,
    operator_name  varchar(64)  NOT NULL,
    operator_ip    inet,

    summary        varchar(500),
    before_value   jsonb,
    after_value    jsonb,

    created_at     timestamptz  NOT NULL DEFAULT now()
);

-- 按对象追溯：某个货主、某个商品改过什么
CREATE INDEX ix_audit_biz ON sys_audit_log (biz_type, biz_id, created_at DESC);

-- 按人追溯
CREATE INDEX ix_audit_operator ON sys_audit_log (operator_id, created_at DESC);

CREATE INDEX ix_audit_time ON sys_audit_log (created_at DESC);

COMMENT ON TABLE  sys_audit_log IS '审计日志，只追加；不提供修改与删除入口';
COMMENT ON COLUMN sys_audit_log.biz_type IS '业务类型，如 OWNER、SKU、USER';
COMMENT ON COLUMN sys_audit_log.action IS '操作类型，如 CREATE、UPDATE、TRANSITION、AUDIT、IMPORT、PRINT';
COMMENT ON COLUMN sys_audit_log.before_value IS '变更前快照，仅记录变更字段；新增时为空';
COMMENT ON COLUMN sys_audit_log.after_value IS '变更后快照，仅记录变更字段；删除时为空';
COMMENT ON COLUMN sys_audit_log.operator_name IS '冗余存名字，避免人员改名后历史记录失真';

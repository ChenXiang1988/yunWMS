-- 统一编码规则
--
-- 首期两条：货主编码 OWNER + 4 位、SKUID（货主前缀 + 6 位，规则记在 SKU 上）。
-- 取号必须用 UPDATE ... RETURNING 原子递增，不能用 SELECT MAX + 1。

CREATE TABLE sys_code_rule (
    id            bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rule_code     varchar(32)  NOT NULL,
    name          varchar(64)  NOT NULL,
    prefix        varchar(16)  NOT NULL DEFAULT '',
    seq_length    smallint     NOT NULL,
    current_value bigint       NOT NULL DEFAULT 0,
    step          int          NOT NULL DEFAULT 1,
    remark        varchar(255),

    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    bigint,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    bigint,

    CONSTRAINT ck_rule_seq_length CHECK (seq_length BETWEEN 1 AND 12),
    CONSTRAINT ck_rule_step CHECK (step >= 1),
    CONSTRAINT ck_rule_current CHECK (current_value >= 0)
);

CREATE UNIQUE INDEX ux_sys_code_rule_code ON sys_code_rule (rule_code);

COMMENT ON TABLE  sys_code_rule IS '统一编码规则；编码生成后不复用';
COMMENT ON COLUMN sys_code_rule.current_value IS '当前已分配到的流水号，取号时原子递增';
COMMENT ON COLUMN sys_code_rule.seq_length IS '流水位数，超出后拒绝生成而不是进位';

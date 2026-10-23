-- 货主（客户）主档
-- 所有业务表通过 owner_id 归属到货主，本表是全库的外键根。

CREATE TABLE owner (
    id                 bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 标识
    code               varchar(20)  NOT NULL,
    name               varchar(100) NOT NULL,
    short_name         varchar(20),
    name_en            varchar(100),
    external_code      varchar(64),
    sku_prefix         varchar(4)   NOT NULL,

    -- 属性
    owner_type         varchar(20)  NOT NULL,
    settlement_entity  varchar(100) NOT NULL,
    status             varchar(20)  NOT NULL DEFAULT 'DRAFT',

    -- 地址
    country            varchar(2)   NOT NULL,
    province           varchar(64),
    city               varchar(64)  NOT NULL,
    district           varchar(64),
    postal_code        varchar(20),
    address_line       varchar(255) NOT NULL,

    -- 其他
    remark             varchar(500),

    -- 审计
    created_at         timestamptz  NOT NULL DEFAULT now(),
    created_by         bigint,
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    updated_by         bigint,
    deleted_at         timestamptz,

    CONSTRAINT ck_owner_status
        CHECK (status IN ('DRAFT', 'POTENTIAL', 'ACTIVE', 'SUSPENDED', 'TERMINATED')),
    CONSTRAINT ck_owner_sku_prefix
        CHECK (sku_prefix ~ '^[A-Z0-9]{4}$'),
    CONSTRAINT ck_owner_country
        CHECK (country ~ '^[A-Z]{2}$')
);

-- 编码全局唯一且不复用，故不带 deleted_at 条件
CREATE UNIQUE INDEX ux_owner_code ON owner (code);

-- SKU 前缀全局唯一，生成后不可修改，是 SKUID 的前四位
CREATE UNIQUE INDEX ux_owner_sku_prefix ON owner (sku_prefix);

-- 外部码允许为空；软删除后允许被新货主使用
CREATE UNIQUE INDEX ux_owner_external_code
    ON owner (external_code)
    WHERE external_code IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX ix_owner_status ON owner (status) WHERE deleted_at IS NULL;

COMMENT ON TABLE  owner IS '货主（客户）主档';
COMMENT ON COLUMN owner.code IS '系统编码，形如 OWNER0001，由编码规则生成，不可修改';
COMMENT ON COLUMN owner.short_name IS '货主简称，用于 SKUID 标签；为空时取 name 前 8 字';
COMMENT ON COLUMN owner.sku_prefix IS 'SKUID 前四位，全局唯一，创建后不可修改';
COMMENT ON COLUMN owner.external_code IS '外部系统编码，可选，未删除时唯一';
COMMENT ON COLUMN owner.owner_type IS '客户类型，取自数据字典';
COMMENT ON COLUMN owner.settlement_entity IS '结算主体，开票与对账的抬头';
COMMENT ON COLUMN owner.status IS 'DRAFT 草稿 / POTENTIAL 潜在 / ACTIVE 合作中 / SUSPENDED 暂停 / TERMINATED 终止';

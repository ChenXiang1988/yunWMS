-- 货主税务登记
-- 支持多国多州：同一货主可有多个国家、同一国家可有多个州、同一州可有多种税号类型。

CREATE TABLE owner_tax_registration (
    id          bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id    bigint       NOT NULL REFERENCES owner (id),

    country     varchar(2)   NOT NULL,
    province    varchar(64),
    tax_type    varchar(20)  NOT NULL,
    tax_number  varchar(64)  NOT NULL,
    is_default  boolean      NOT NULL DEFAULT false,

    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  bigint,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint,
    deleted_at  timestamptz,

    CONSTRAINT ck_tax_country CHECK (country ~ '^[A-Z]{2}$')
);

-- 同一货主 + 国家 + 州 + 税号类型 不允许重复
-- province 可为空，用 COALESCE 把 NULL 归一，否则空省的多条记录会被当作互不冲突
CREATE UNIQUE INDEX ux_owner_tax_unique
    ON owner_tax_registration (owner_id, country, COALESCE(province, ''), tax_type)
    WHERE deleted_at IS NULL;

-- 同一货主同一国家只能有一个默认税号
CREATE UNIQUE INDEX ux_owner_tax_default
    ON owner_tax_registration (owner_id, country)
    WHERE is_default AND deleted_at IS NULL;

CREATE INDEX ix_owner_tax_owner
    ON owner_tax_registration (owner_id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE  owner_tax_registration IS '货主税务登记，支持多国多州';
COMMENT ON COLUMN owner_tax_registration.tax_type IS '税号类型，如 EIN、STATE_SALES、VAT、GST，取自数据字典';
COMMENT ON COLUMN owner_tax_registration.is_default IS '同国默认税号，每国至多一个';

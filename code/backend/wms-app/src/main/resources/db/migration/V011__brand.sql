-- 品牌（货主级）
-- 品牌是货主的商业资产，不跨货主共享；不同货主允许有同名品牌。

CREATE TABLE brand (
    id          bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id    bigint      NOT NULL REFERENCES owner (id),

    name        varchar(64) NOT NULL,
    name_en     varchar(64),
    status      varchar(10) NOT NULL DEFAULT 'ACTIVE',

    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  bigint,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  bigint,
    deleted_at  timestamptz,

    CONSTRAINT ck_brand_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- 同一货主内品牌名不重复；跨货主同名互不影响
CREATE UNIQUE INDEX ux_brand_owner_name
    ON brand (owner_id, name)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_brand_owner ON brand (owner_id) WHERE deleted_at IS NULL;

COMMENT ON TABLE  brand IS '品牌，货主级主数据，不跨货主共享';
COMMENT ON COLUMN brand.owner_id IS '货主 A 与货主 B 可各建一个 Nike，互不影响';

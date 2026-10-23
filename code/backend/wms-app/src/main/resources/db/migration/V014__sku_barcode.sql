-- 商品条码
--
-- 条码的唯一来源。包装层级不另存条码字段，改为在本表用 pack_level 标记归属。
-- 一个 SKU 的同一层可以有多个条码（不同供应商贴不同的码），所以 (sku_id, pack_level) 不唯一。

CREATE TABLE sku_barcode (
    id          bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku_id      bigint      NOT NULL REFERENCES sku (id) ON DELETE CASCADE,
    owner_id    bigint      NOT NULL REFERENCES owner (id),

    barcode     varchar(64) NOT NULL,
    pack_level  smallint    NOT NULL DEFAULT 1,
    is_primary  boolean     NOT NULL DEFAULT false,

    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  bigint,
    deleted_at  timestamptz,

    CONSTRAINT ck_barcode_level CHECK (pack_level BETWEEN 1 AND 3),
    CONSTRAINT ck_barcode_value CHECK (length(btrim(barcode)) >= 4)
);

-- 同货主内条码唯一；软删后允许复用（旧商品下架，码可以给新品）
CREATE UNIQUE INDEX ux_sku_barcode_owner
    ON sku_barcode (owner_id, barcode)
    WHERE deleted_at IS NULL;

-- 扫码入口：按条码直接定位，不带货主条件时用于「无上下文」检索
CREATE INDEX ix_sku_barcode_lookup
    ON sku_barcode (barcode)
    WHERE deleted_at IS NULL;

-- 每个 SKU 至多一个主条码
CREATE UNIQUE INDEX ux_sku_barcode_primary
    ON sku_barcode (sku_id)
    WHERE is_primary AND deleted_at IS NULL;

COMMENT ON TABLE  sku_barcode IS '商品条码，条码的唯一来源';
COMMENT ON COLUMN sku_barcode.pack_level IS '该条码属于包装层级的哪一层：1 单品 / 2 内箱 / 3 外箱';
COMMENT ON COLUMN sku_barcode.barcode IS '同货主内唯一；跨货主可重复，因此扫码必须带货主上下文';

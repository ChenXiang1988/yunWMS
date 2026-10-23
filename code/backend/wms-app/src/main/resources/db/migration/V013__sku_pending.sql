-- 商品待审版本
--
-- 已通过的商品修改关键字段时，改动写在这里，业务继续读 sku 表。
-- 审核通过：把本表内容覆盖进 sku 并删除本行；
-- 审核驳回：直接删除本行。
--
-- 同一 SKU 同一时刻至多一行，由主键保证。

CREATE TABLE sku_pending (
    sku_id            bigint       PRIMARY KEY REFERENCES sku (id) ON DELETE CASCADE,

    -- 与 sku 同构的可变字段
    name              varchar(200) NOT NULL,
    category_id       bigint       REFERENCES category (id),
    brand_id          bigint       REFERENCES brand (id),
    uom               varchar(16)  NOT NULL,
    length_mm         int,
    width_mm          int,
    height_mm         int,
    weight_g          int,
    volume_mm3        bigint,
    temperature_zone  varchar(16),
    has_battery       boolean      NOT NULL DEFAULT false,
    battery_type      varchar(32),
    expiry_managed    boolean      NOT NULL DEFAULT false,
    shelf_life_days   int,
    batch_managed     boolean      NOT NULL DEFAULT false,
    serial_managed    boolean      NOT NULL DEFAULT false,
    origin_country    varchar(2),
    hs_code           varchar(16),
    images            text[]       NOT NULL DEFAULT '{}',
    remark            varchar(500),

    -- 提交信息
    submitted_at      timestamptz  NOT NULL DEFAULT now(),
    submitted_by      bigint,

    CONSTRAINT ck_pending_images CHECK (cardinality(images) <= 5)
);

COMMENT ON TABLE  sku_pending IS '商品待审版本，与 sku 一对一，同一 SKU 至多一行';
COMMENT ON COLUMN sku_pending.sku_id IS '同时是主键与外键，保证一个 SKU 只有一个待审版本';

-- 商品主档（线上生效版）
--
-- 双版本设计：本表存业务正在使用的数据；
-- 已通过商品修改关键字段时，改动写入 sku_pending，审核通过后才覆盖本表。
--
-- 尺寸与重量为「单品」口径，内箱与外箱的箱规在 sku_pack。

CREATE TABLE sku (
    id                bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 标识
    sku_id            varchar(10)  NOT NULL,
    owner_id          bigint       NOT NULL REFERENCES owner (id),
    owner_sku_code    varchar(64)  NOT NULL,
    name              varchar(200) NOT NULL,

    -- 归属
    category_id       bigint       REFERENCES category (id),
    brand_id          bigint       REFERENCES brand (id),

    -- 计量：长度用毫米、重量用克，全部为整数，避免浮点误差累积
    uom               varchar(16)  NOT NULL,
    length_mm         int,
    width_mm          int,
    height_mm         int,
    weight_g          int,
    volume_mm3        bigint,
    temperature_zone  varchar(16),

    -- 合规
    has_battery       boolean      NOT NULL DEFAULT false,
    battery_type      varchar(32),
    expiry_managed    boolean      NOT NULL DEFAULT false,
    shelf_life_days   int,
    batch_managed     boolean      NOT NULL DEFAULT false,
    serial_managed    boolean      NOT NULL DEFAULT false,
    origin_country    varchar(2),
    hs_code           varchar(16),

    -- 其他
    images            text[]       NOT NULL DEFAULT '{}',
    remark            varchar(500),

    -- 状态：审核与启停是两个独立维度
    audit_status      varchar(16)  NOT NULL DEFAULT 'DRAFT',
    enabled           boolean      NOT NULL DEFAULT false,

    -- 审计
    created_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        bigint,
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    updated_by        bigint,
    deleted_at        timestamptz,

    CONSTRAINT ck_sku_id_format   CHECK (sku_id ~ '^[A-Z0-9]{10}$'),
    CONSTRAINT ck_sku_audit       CHECK (audit_status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_sku_images      CHECK (cardinality(images) <= 5),
    CONSTRAINT ck_sku_length      CHECK (length_mm IS NULL OR length_mm > 0),
    CONSTRAINT ck_sku_width       CHECK (width_mm  IS NULL OR width_mm  > 0),
    CONSTRAINT ck_sku_height      CHECK (height_mm IS NULL OR height_mm > 0),
    CONSTRAINT ck_sku_weight      CHECK (weight_g  IS NULL OR weight_g  > 0),
    CONSTRAINT ck_sku_shelf_life  CHECK (shelf_life_days IS NULL OR shelf_life_days > 0)
);

-- SKUID 全局唯一且永不复用，故不带 deleted_at 条件
CREATE UNIQUE INDEX ux_sku_sku_id ON sku (sku_id);

-- 货主商品编码在货主内唯一且不复用
CREATE UNIQUE INDEX ux_sku_owner_code ON sku (owner_id, owner_sku_code);

-- 审核队列按状态查，只索引待审核部分
CREATE INDEX ix_sku_pending_review
    ON sku (created_at)
    WHERE audit_status = 'PENDING' AND deleted_at IS NULL;

CREATE INDEX ix_sku_owner ON sku (owner_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_sku_category ON sku (category_id) WHERE deleted_at IS NULL;

COMMENT ON TABLE  sku IS '商品主档，存线上生效版本';
COMMENT ON COLUMN sku.sku_id IS '十位业务编码，四位货主前缀 + 六位流水，全局唯一且不复用';
COMMENT ON COLUMN sku.enabled IS '启停状态，与 audit_status 独立；仅审核通过后允许启用';
COMMENT ON COLUMN sku.images IS '商品图片的对象存储路径数组，最多 5 张';
COMMENT ON COLUMN sku.weight_g IS '单品重量，单位克';
COMMENT ON COLUMN sku.shelf_life_days IS '保质期天数，仅在 expiry_managed 为真时有效';

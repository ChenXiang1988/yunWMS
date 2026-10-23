-- 商品包装层级
--
-- 链式三层：单品 → 内箱 → 外箱。不含托盘，托盘是装载单元不是商品属性。
-- 每层的 qty_per_parent 是「几个本层 = 一个上一层」，外箱 24 件 = 4 内箱 × 6 件。
--
-- 尺寸与重量只在第 2、3 层出现：单品的尺寸在主档，不重复录入。

CREATE TABLE sku_pack (
    id              bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku_id          bigint       NOT NULL REFERENCES sku (id) ON DELETE CASCADE,

    level           smallint     NOT NULL,
    unit_name       varchar(32)  NOT NULL,
    qty_per_parent  int          NOT NULL,

    length_mm       int,
    width_mm        int,
    height_mm       int,
    weight_g        int,

    created_at      timestamptz  NOT NULL DEFAULT now(),
    created_by      bigint,
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    updated_by      bigint,
    deleted_at      timestamptz,

    CONSTRAINT ck_pack_level CHECK (level BETWEEN 1 AND 3),

    -- 单品层不填箱规，尺寸来自主档；换算数量固定为 1
    CONSTRAINT ck_pack_level1 CHECK (
        level <> 1
        OR (qty_per_parent = 1
            AND length_mm IS NULL AND width_mm IS NULL
            AND height_mm IS NULL AND weight_g IS NULL)
    ),

    -- 内箱与外箱必须给出换算数量与完整箱规。
    -- 必须显式写 IS NOT NULL：CHECK 里 NULL 比较的结果是 NULL，
    -- 而 NULL 不会让 CHECK 失败，只写 `> 0` 等于允许留空。
    CONSTRAINT ck_pack_upper CHECK (
        level = 1
        OR (qty_per_parent >= 2
            AND length_mm IS NOT NULL AND length_mm > 0
            AND width_mm  IS NOT NULL AND width_mm  > 0
            AND height_mm IS NOT NULL AND height_mm > 0
            AND weight_g  IS NOT NULL AND weight_g  > 0)
    )
);

-- 一个 SKU 的每一层只有一行
CREATE UNIQUE INDEX ux_sku_pack_level
    ON sku_pack (sku_id, level)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE  sku_pack IS '商品包装层级，链式三层，不含托盘';
COMMENT ON COLUMN sku_pack.level IS '1 单品 / 2 内箱 / 3 外箱';
COMMENT ON COLUMN sku_pack.qty_per_parent IS '几个本层等于一个上一层；第 1 层固定为 1';

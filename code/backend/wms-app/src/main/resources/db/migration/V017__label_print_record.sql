-- SKUID 标签打印记录
--
-- 追加型日志，不提供删除与修改入口，因此不加 deleted_at。

CREATE TABLE label_print_record (
    id                bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku_id            bigint       NOT NULL REFERENCES sku (id),
    owner_id          bigint       NOT NULL REFERENCES owner (id),

    copies            int          NOT NULL,
    channel           varchar(16)  NOT NULL,
    template_version  varchar(16)  NOT NULL,
    has_watermark     boolean      NOT NULL DEFAULT false,

    printed_by        bigint       NOT NULL,
    printed_at        timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT ck_label_copies CHECK (copies BETWEEN 1 AND 500),
    CONSTRAINT ck_label_channel CHECK (channel IN ('DOWNLOAD', 'BROWSER'))
);

CREATE INDEX ix_label_sku_time ON label_print_record (sku_id, printed_at DESC);
CREATE INDEX ix_label_owner_time ON label_print_record (owner_id, printed_at DESC);

COMMENT ON TABLE  label_print_record IS 'SKUID 标签打印记录，只追加不修改';
COMMENT ON COLUMN label_print_record.copies IS '单次打印张数，上限 500';
COMMENT ON COLUMN label_print_record.template_version IS '标签模板版本，用于回溯某个批次用的是哪版模板';
COMMENT ON COLUMN label_print_record.has_watermark IS '草稿或待审核状态下打印会带水印，此处留痕';

-- 商品批量导入任务
--
-- 只新增不更新。已经存在的货主商品编码进错误报告，不修改原商品。
-- 文件与错误报告存对象存储，本表只存 key。

CREATE TABLE sku_import_task (
    id                bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id          bigint       NOT NULL REFERENCES owner (id),

    file_name         varchar(255) NOT NULL,
    file_key          varchar(255) NOT NULL,
    error_report_key  varchar(255),

    status            varchar(16)  NOT NULL DEFAULT 'PENDING',
    total_rows        int          NOT NULL DEFAULT 0,
    success_rows      int          NOT NULL DEFAULT 0,
    failed_rows       int          NOT NULL DEFAULT 0,

    started_at        timestamptz,
    finished_at       timestamptz,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        bigint,

    CONSTRAINT ck_import_status CHECK (
        status IN ('PENDING', 'RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED')
    ),
    CONSTRAINT ck_import_counts CHECK (
        total_rows >= 0 AND success_rows >= 0 AND failed_rows >= 0
        AND success_rows + failed_rows <= total_rows
    )
);

-- 同一货主同时只能有一个未完成的任务，阻止并发导入
CREATE UNIQUE INDEX ux_import_running_per_owner
    ON sku_import_task (owner_id)
    WHERE status IN ('PENDING', 'RUNNING');

CREATE INDEX ix_import_owner_time
    ON sku_import_task (owner_id, created_at DESC);

COMMENT ON TABLE  sku_import_task IS '商品批量导入任务，只新增不更新';
COMMENT ON COLUMN sku_import_task.status IS 'PENDING 待处理 / RUNNING 处理中 / SUCCESS 全部成功 / PARTIAL 部分成功 / FAILED 失败';
COMMENT ON COLUMN sku_import_task.error_report_key IS '错误报告的对象存储 key，成功时为空';

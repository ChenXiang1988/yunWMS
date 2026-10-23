-- 数据字典
-- 字典类型与字典项。业务代码通过 dict_type + value 引用，不引用自增 ID。

CREATE TABLE sys_dict (
    id          bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dict_type   varchar(64)  NOT NULL,
    name        varchar(64)  NOT NULL,
    name_en     varchar(64),
    remark      varchar(255),
    status      varchar(10)  NOT NULL DEFAULT 'ACTIVE',

    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  bigint,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint,
    deleted_at  timestamptz,

    CONSTRAINT ck_dict_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- 不用部分索引：sys_dict_item 的外键要用它做目标，而 PostgreSQL 的外键
-- 不能指向部分唯一索引。字典类型本身也不复用，全量唯一即可。
CREATE UNIQUE INDEX ux_sys_dict_type ON sys_dict (dict_type);

CREATE TABLE sys_dict_item (
    id          bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dict_type   varchar(64)  NOT NULL REFERENCES sys_dict (dict_type),

    label       varchar(64)  NOT NULL,
    label_en    varchar(64),
    value       varchar(64)  NOT NULL,
    sort_no     int          NOT NULL DEFAULT 0,
    status      varchar(10)  NOT NULL DEFAULT 'ACTIVE',
    ext         jsonb,

    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  bigint,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint,
    deleted_at  timestamptz,

    CONSTRAINT ck_dict_item_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- 同一字典类型下取值唯一
CREATE UNIQUE INDEX ux_sys_dict_item_value
    ON sys_dict_item (dict_type, value)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_sys_dict_item_type
    ON sys_dict_item (dict_type, sort_no)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE  sys_dict IS '数据字典类型，预置后由管理员维护';
COMMENT ON TABLE  sys_dict_item IS '数据字典项；停用后不再出现在选择列表中，但历史数据仍能正常展示';
COMMENT ON COLUMN sys_dict_item.ext IS '扩展属性，用于承载该项的特殊配置';

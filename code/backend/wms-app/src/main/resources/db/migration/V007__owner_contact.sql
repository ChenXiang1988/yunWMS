-- 货主联系人
-- 一个货主可有多条联系人，其中至多一条为「主要联系人」。

CREATE TABLE owner_contact (
    id          bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id    bigint       NOT NULL REFERENCES owner (id),

    name        varchar(64)  NOT NULL,
    role_type   varchar(20)  NOT NULL DEFAULT 'BUSINESS',
    phone       varchar(32),
    email       varchar(128),
    is_primary  boolean      NOT NULL DEFAULT false,
    remark      varchar(255),

    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  bigint,
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    updated_by  bigint,
    deleted_at  timestamptz
);

-- 每个货主至多一个主要联系人，由数据库保证而非代码判断
CREATE UNIQUE INDEX ux_owner_contact_primary
    ON owner_contact (owner_id)
    WHERE is_primary AND deleted_at IS NULL;

CREATE INDEX ix_owner_contact_owner
    ON owner_contact (owner_id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE  owner_contact IS '货主联系人';
COMMENT ON COLUMN owner_contact.role_type IS '联系人角色，取自数据字典，如法人、财务、商务、现场联系人';
COMMENT ON COLUMN owner_contact.is_primary IS '主要联系人，每个货主至多一个';

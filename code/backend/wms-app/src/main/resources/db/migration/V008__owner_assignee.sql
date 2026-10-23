-- 货主与内部负责客服的关系（一主一副）
--
-- 本表同时是「ASSIGNED 数据范围」的推导依据：
-- 客服能看哪些货主，取决于这里有没有他。

CREATE TABLE owner_assignee (
    id             bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id       bigint      NOT NULL REFERENCES owner (id),
    user_id        bigint      NOT NULL REFERENCES sys_user (id),
    relation_type  varchar(10) NOT NULL,

    created_at     timestamptz NOT NULL DEFAULT now(),
    created_by     bigint,

    CONSTRAINT ck_assignee_relation
        CHECK (relation_type IN ('PRIMARY', 'SECONDARY'))
);

-- 同一货主只能有一个主负责人和一个副负责人
CREATE UNIQUE INDEX ux_owner_assignee_role
    ON owner_assignee (owner_id, relation_type);

-- 反向查询：某人负责哪些货主，数据范围过滤走这条
CREATE INDEX ix_owner_assignee_user ON owner_assignee (user_id);

COMMENT ON TABLE  owner_assignee IS '货主负责客服，一主一副，一个客服可负责多个货主';
COMMENT ON COLUMN owner_assignee.relation_type IS 'PRIMARY 主负责人 / SECONDARY 副负责人';

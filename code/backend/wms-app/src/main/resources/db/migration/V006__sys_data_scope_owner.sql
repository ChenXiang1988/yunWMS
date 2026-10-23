-- 数据范围的「指定货主」明细
--
-- 三种范围：ALL 全部 / ASSIGNED 我负责的货主 / CUSTOM 指定货主。
-- 前两种是规则，不落明细；只有 CUSTOM 需要本表存具体名单。
--
-- 为什么 ASSIGNED 不存名单：客服负责的货主会变，存成静态列表后
-- 每新增一个货主都要回写所有客服的范围，漏一个就少看一个货主。
-- 改成按 owner_assignee 实时推导，新增即生效。

CREATE TABLE sys_data_scope_owner (
    id         bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_id    bigint      REFERENCES sys_role (id) ON DELETE CASCADE,
    user_id    bigint      REFERENCES sys_user (id) ON DELETE CASCADE,
    owner_id   bigint      NOT NULL REFERENCES owner (id),
    created_at timestamptz NOT NULL DEFAULT now(),

    -- 一行只能属于角色或用户之一
    CONSTRAINT ck_scope_owner_subject
        CHECK ((role_id IS NULL) <> (user_id IS NULL))
);

-- 同一主体不能重复绑同一货主
CREATE UNIQUE INDEX ux_scope_owner_role
    ON sys_data_scope_owner (role_id, owner_id)
    WHERE role_id IS NOT NULL;

CREATE UNIQUE INDEX ux_scope_owner_user
    ON sys_data_scope_owner (user_id, owner_id)
    WHERE user_id IS NOT NULL;

COMMENT ON TABLE  sys_data_scope_owner IS 'CUSTOM 范围下的指定货主明细，ALL 与 ASSIGNED 不落本表';
COMMENT ON COLUMN sys_data_scope_owner.role_id IS '角色默认范围；与 user_id 互斥';
COMMENT ON COLUMN sys_data_scope_owner.user_id IS '用户覆盖范围；与 role_id 互斥';

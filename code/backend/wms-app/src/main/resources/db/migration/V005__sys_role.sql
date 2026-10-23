-- 角色、角色授权、用户授权

CREATE TABLE sys_role (
    id               bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code             varchar(64)  NOT NULL,
    name             varchar(64)  NOT NULL,
    builtin          boolean      NOT NULL DEFAULT false,
    data_scope_type  varchar(10)  NOT NULL DEFAULT 'CUSTOM',
    sort_no          int          NOT NULL DEFAULT 0,
    status           varchar(10)  NOT NULL DEFAULT 'ACTIVE',
    remark           varchar(255),

    created_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       bigint,
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       bigint,
    deleted_at       timestamptz,

    CONSTRAINT ck_role_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_role_scope
        CHECK (data_scope_type IN ('ALL', 'ASSIGNED', 'CUSTOM'))
);

CREATE UNIQUE INDEX ux_sys_role_code ON sys_role (code);

CREATE TABLE sys_role_menu (
    role_id    bigint      NOT NULL REFERENCES sys_role (id) ON DELETE CASCADE,
    menu_id    bigint      NOT NULL REFERENCES sys_menu (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, menu_id)
);

CREATE TABLE sys_user_role (
    user_id    bigint      NOT NULL REFERENCES sys_user (id) ON DELETE CASCADE,
    role_id    bigint      NOT NULL REFERENCES sys_role (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX ix_sys_user_role_role ON sys_user_role (role_id);

COMMENT ON TABLE  sys_role IS '角色，功能权限与数据范围默认值的载体';
COMMENT ON COLUMN sys_role.code IS '角色标识，程序引用用，唯一';
COMMENT ON COLUMN sys_role.builtin IS '内置角色标记，超级管理员为 true，不可编辑不可删除';
COMMENT ON COLUMN sys_role.data_scope_type IS 'ALL 全部货主 / ASSIGNED 我负责的货主 / CUSTOM 指定货主';
COMMENT ON TABLE  sys_role_menu IS '角色与权限点的关联';
COMMENT ON TABLE  sys_user_role IS '用户与角色的关联，一个用户可多角色';

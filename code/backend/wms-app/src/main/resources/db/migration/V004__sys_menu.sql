-- 权限点
-- 参考 RuoYi 的 menu_type：M 目录 / C 菜单 / F 按钮。
-- 权限点随版本发布维护，不提供增删入口，因此本表不加 deleted_at。

CREATE TABLE sys_menu (
    id            bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id     bigint       REFERENCES sys_menu (id),
    menu_type     char(1)      NOT NULL,
    name          varchar(64)  NOT NULL,
    perms         varchar(100),
    path          varchar(200),
    component     varchar(200),
    icon          varchar(64),
    sort_no       int          NOT NULL DEFAULT 0,
    visible       boolean      NOT NULL DEFAULT true,
    status        varchar(10)  NOT NULL DEFAULT 'ACTIVE',

    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    bigint,
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    updated_by    bigint,

    CONSTRAINT ck_menu_type CHECK (menu_type IN ('M', 'C', 'F')),
    CONSTRAINT ck_menu_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    -- 按钮必须带权限点标识，目录不需要
    CONSTRAINT ck_menu_perms CHECK (menu_type <> 'F' OR perms IS NOT NULL)
);

-- 权限点标识是授权判断的唯一依据，不允许重复
CREATE UNIQUE INDEX ux_sys_menu_perms
    ON sys_menu (perms)
    WHERE perms IS NOT NULL;

CREATE INDEX ix_sys_menu_parent ON sys_menu (parent_id, sort_no);

COMMENT ON TABLE  sys_menu IS '权限点与菜单，随版本发布维护，不提供界面增删';
COMMENT ON COLUMN sys_menu.menu_type IS 'M 目录 / C 菜单 / F 按钮';
COMMENT ON COLUMN sys_menu.perms IS '权限点标识，格式 模块:实体:动作，如 owner:owner:create';
COMMENT ON COLUMN sys_menu.path IS '前端路由路径，按钮类型为空';

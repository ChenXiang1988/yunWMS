-- 系统用户
-- 内部员工与货主账号同表，用 subject_type 区分。
-- 全库 created_by / updated_by 指向本表。

CREATE TABLE sys_user (
    id                  bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 身份
    login_name          varchar(64)  NOT NULL,
    display_name        varchar(64)  NOT NULL,
    subject_type        varchar(10)  NOT NULL,
    owner_id            bigint,

    -- 凭据
    password_hash       varchar(100) NOT NULL,
    password_updated_at timestamptz  NOT NULL DEFAULT now(),
    must_change_password boolean     NOT NULL DEFAULT true,

    -- 状态
    status              varchar(10)  NOT NULL DEFAULT 'ACTIVE',
    failed_attempts     smallint     NOT NULL DEFAULT 0,
    locked_until        timestamptz,
    last_login_at       timestamptz,

    -- 数据范围：为空表示继承角色默认
    data_scope_type     varchar(10),

    -- 联系
    email               varchar(128),

    -- 审计
    created_at          timestamptz  NOT NULL DEFAULT now(),
    created_by          bigint,
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    updated_by          bigint,
    deleted_at          timestamptz,

    CONSTRAINT ck_user_subject CHECK (
        (subject_type = 'STAFF' AND owner_id IS NULL)
        OR (subject_type = 'OWNER' AND owner_id IS NOT NULL)
    ),
    CONSTRAINT ck_user_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    -- 员工必须绑邮箱，否则无法自助找回；货主可不填，走客服重置
    CONSTRAINT ck_user_email_required
        CHECK (subject_type = 'OWNER' OR email IS NOT NULL)
);

-- 登录框只有一个，登录名必须全局唯一且不区分大小写
CREATE UNIQUE INDEX ux_sys_user_login_name ON sys_user (lower(login_name));

-- 邮箱用于找回密码，一个邮箱只能绑一个账号
CREATE UNIQUE INDEX ux_sys_user_email
    ON sys_user (lower(email))
    WHERE email IS NOT NULL;

CREATE INDEX ix_sys_user_owner ON sys_user (owner_id) WHERE owner_id IS NOT NULL;

COMMENT ON TABLE  sys_user IS '系统用户，含内部员工与货主账号';
COMMENT ON COLUMN sys_user.login_name IS '登录名；员工手填，货主账号取货主编码，全局唯一且不区分大小写';
COMMENT ON COLUMN sys_user.subject_type IS 'STAFF 内部员工 / OWNER 货主用户';
COMMENT ON COLUMN sys_user.owner_id IS '货主用户必填，内部员工必须为空';
COMMENT ON COLUMN sys_user.must_change_password IS '首次登录或重置密码后为 true，强制改密';
COMMENT ON COLUMN sys_user.status IS 'ACTIVE 启用 / DISABLED 停用';
COMMENT ON COLUMN sys_user.failed_attempts IS '连续登录失败次数，成功登录后归零';
COMMENT ON COLUMN sys_user.locked_until IS '锁定截止时间，为空表示未锁定；锁定是派生状态，不占 status';

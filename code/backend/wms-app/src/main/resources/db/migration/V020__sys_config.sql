-- 系统参数
--
-- 只读使用，不提供界面编辑入口，变更由运维直接改数据。
-- 敏感项（如 SMTP 密码）不放本表，走环境变量。

CREATE TABLE sys_config (
    id             bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    config_key     varchar(64)  NOT NULL,
    config_value   varchar(500) NOT NULL,
    default_value  varchar(500) NOT NULL,
    description    varchar(255) NOT NULL,

    created_at     timestamptz  NOT NULL DEFAULT now(),
    created_by     bigint,
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    updated_by     bigint
);

CREATE UNIQUE INDEX ux_sys_config_key ON sys_config (config_key);

COMMENT ON TABLE  sys_config IS '系统参数，只读使用，不提供界面编辑入口';
COMMENT ON COLUMN sys_config.default_value IS '缺省值；取不到配置时回落至此，保证代码不会因缺配置崩溃';
COMMENT ON COLUMN sys_config.config_value IS '当前值；敏感项不放本表，走环境变量';

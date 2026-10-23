-- 密码重置令牌
-- 只存令牌哈希，明文仅出现在邮件链接里，库里拿不到就无法伪造。

CREATE TABLE sys_password_reset_token (
    id            bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       bigint       NOT NULL REFERENCES sys_user (id),
    token_hash    varchar(64)  NOT NULL,
    expires_at    timestamptz  NOT NULL,
    used_at       timestamptz,
    requested_ip  inet,
    created_at    timestamptz  NOT NULL DEFAULT now()
);

-- 校验时按哈希查，不做全表扫
CREATE UNIQUE INDEX ux_reset_token_hash
    ON sys_password_reset_token (token_hash);

-- 限流与清理都按人加时间查
CREATE INDEX ix_reset_token_user
    ON sys_password_reset_token (user_id, created_at DESC);

COMMENT ON TABLE  sys_password_reset_token IS '密码重置令牌，一次性，30 分钟过期';
COMMENT ON COLUMN sys_password_reset_token.token_hash IS '令牌的 SHA-256 十六进制摘要，不存明文';
COMMENT ON COLUMN sys_password_reset_token.used_at IS '使用时间，非空即已失效，不可再次使用';
COMMENT ON COLUMN sys_password_reset_token.requested_ip IS '发起请求的 IP，用于异常排查与限流';

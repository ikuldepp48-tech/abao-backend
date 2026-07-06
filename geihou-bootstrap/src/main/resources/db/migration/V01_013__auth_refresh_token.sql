-- Geihou Bootstrap runtime migration copy.
-- Source candidate: /Users/mac/Desktop/abao-projects/abao-backend/db/migrations/V01_013__auth_refresh_token.sql
-- Geihou is the platform; Abao is tenant sample #1 only.

CREATE TABLE auth_refresh_token (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  token_key       VARCHAR(64)  NOT NULL                  COMMENT 'refresh token SHA-256 lookup key',
  token_hash      CHAR(64)     NOT NULL                  COMMENT 'refresh token SHA-256 user-bound hash',
  user_id         BIGINT       NOT NULL                  COMMENT '用户ID',
  tenant_id       BIGINT       NOT NULL DEFAULT 0        COMMENT '租户ID(0=平台用户)',
  user_role       VARCHAR(32)  NOT NULL                  COMMENT 'ENUM_USER_ROLE',
  expire_time     DATETIME     NOT NULL                  COMMENT '过期时间',
  consumed_time   DATETIME                               COMMENT '成功消费时间',
  revoked_time    DATETIME                               COMMENT '吊销时间',
  revoke_reason   VARCHAR(64)                            COMMENT '吊销原因',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',
  UNIQUE KEY uk_token_key (token_key),
  KEY idx_expire_time (expire_time),
  KEY idx_user_id (user_id),
  KEY idx_consumed_time (consumed_time),
  KEY idx_revoked_time (revoked_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台登录 refresh token';

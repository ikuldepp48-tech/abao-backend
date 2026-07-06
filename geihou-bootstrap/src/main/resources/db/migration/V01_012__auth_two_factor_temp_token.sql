-- Geihou Bootstrap runtime migration copy.
-- Source candidate: /Users/mac/Desktop/abao-projects/abao-backend/db/migrations/V01_012__auth_two_factor_temp_token.sql
-- Geihou is the platform; Abao is tenant sample #1 only.

CREATE TABLE auth_two_factor_temp_token (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  temp_token_key  VARCHAR(64)  NOT NULL                  COMMENT '2FA 临时 token opaque key',
  token_hash      CHAR(64)     NOT NULL                  COMMENT '临时 token SHA-256 哈希',
  user_id         BIGINT       NOT NULL                  COMMENT '已通过账号密码认证的用户ID',
  tenant_id       BIGINT       NOT NULL DEFAULT 0        COMMENT '租户ID(0=平台用户)',
  user_role       VARCHAR(32)  NOT NULL                  COMMENT 'ENUM_USER_ROLE',
  expire_time     DATETIME     NOT NULL                  COMMENT '过期时间',
  consumed_time   DATETIME                               COMMENT '成功消费时间',
  two_factor_fail_count INT     NOT NULL DEFAULT 0        COMMENT '2FA code 连续失败次数(达到阈值后临时 token 失效)',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',
  UNIQUE KEY uk_temp_token_key (temp_token_key),
  KEY idx_expire_time (expire_time),
  KEY idx_user_id (user_id),
  KEY idx_consumed_time (consumed_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台登录 2FA 临时 token';

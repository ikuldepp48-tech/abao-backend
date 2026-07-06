-- Geihou Bootstrap runtime migration copy.
-- Source candidate: /Users/mac/Desktop/abao-projects/abao-backend/db/migrations/V01_004__auth_token_revoked.sql
-- Header fixed before first Flyway migrate. Do not edit this migration after it is applied;
-- create a later migration version for any future schema or seed change.
-- Geihou is the platform; Abao is tenant sample #1 only.

CREATE TABLE auth_token_revoked (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  jti             VARCHAR(64)  NOT NULL                  COMMENT 'JWT 的 jti',
  user_id         BIGINT       NOT NULL                  COMMENT '用户ID',
  revoke_reason   VARCHAR(64)  NOT NULL                  COMMENT 'LOGOUT / PASSWORD_CHANGE / FORCE_OFFLINE / ROLE_CHANGE',
  revoke_time     DATETIME     NOT NULL                  COMMENT '吊销时间',
  expire_time     DATETIME     NOT NULL                  COMMENT 'token 自然过期时间(到期后此记录可清理)',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',
  UNIQUE KEY uk_jti (jti),
  KEY idx_user (user_id, revoke_time),
  KEY idx_expire_time (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Token 吊销表';

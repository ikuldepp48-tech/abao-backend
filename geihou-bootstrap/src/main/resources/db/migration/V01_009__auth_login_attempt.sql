-- Geihou Bootstrap runtime migration copy.
-- Source candidate: /Users/mac/Desktop/abao-projects/abao-backend/db/migrations/V01_009__auth_login_attempt.sql
-- Geihou is the platform; Abao is tenant sample #1 only.

CREATE TABLE auth_login_attempt (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  username        VARCHAR(64)                            COMMENT '登录用户名(失败时也记录原始用户名)',
  auth_status     VARCHAR(32)  NOT NULL                  COMMENT 'AUTHENTICATED / TWO_FACTOR_REQUIRED / DENIED',
  deny_reason     VARCHAR(64)                            COMMENT '拒绝原因,成功或进入 2FA 时为空',
  client_ip       VARCHAR(64)                            COMMENT '客户端 IP',
  user_agent      VARCHAR(512)                           COMMENT 'User-Agent',
  attempt_time    DATETIME     NOT NULL                  COMMENT '认证尝试时间',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',
  KEY idx_username_time (username, attempt_time),
  KEY idx_status_time (auth_status, attempt_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台登录认证尝试日志';

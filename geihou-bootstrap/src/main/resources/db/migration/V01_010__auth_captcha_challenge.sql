-- Geihou Bootstrap runtime migration copy.
-- Source candidate: /Users/mac/Desktop/abao-projects/abao-backend/db/migrations/V01_010__auth_captcha_challenge.sql
-- Geihou is the platform; Abao is tenant sample #1 only.

CREATE TABLE auth_captcha_challenge (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  captcha_key     VARCHAR(64)  NOT NULL                  COMMENT '验证码 opaque key',
  answer_hash     CHAR(64)     NOT NULL                  COMMENT '验证码答案 SHA-256 哈希',
  expire_time     DATETIME     NOT NULL                  COMMENT '过期时间',
  consumed_time   DATETIME                               COMMENT '成功校验后的消费时间',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',
  UNIQUE KEY uk_captcha_key (captcha_key),
  KEY idx_expire_time (expire_time),
  KEY idx_consumed_time (consumed_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台登录图形验证码挑战';

-- Geihou Bootstrap runtime migration copy.
-- Source candidate: /Users/mac/Desktop/abao-projects/abao-backend/db/migrations/V01_005__auth_user.sql
-- Header fixed before first Flyway migrate. Do not edit this migration after it is applied;
-- create a later migration version for any future schema or seed change.
-- Geihou is the platform; Abao is tenant sample #1 only.

CREATE TABLE auth_user (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id           BIGINT       NOT NULL DEFAULT 0        COMMENT '租户ID(0=平台用户,>0=客户租户用户)',
  user_role           VARCHAR(32)  NOT NULL                  COMMENT 'ENUM_USER_ROLE: CUSTOMER / STAFF / CK_WORKER / OWNER / CONSULTANT / PLATFORM_OPERATOR',
  username            VARCHAR(64)                            COMMENT '用户名(老板/咨询师/平台运营用)',
  phone               VARCHAR(32)                            COMMENT '手机号(全角色都可有,顾客/员工主登录)',
  email               VARCHAR(128)                           COMMENT '邮箱(咨询师/平台运营用)',
  wechat_openid       VARCHAR(64)                            COMMENT '微信 OpenID(顾客/员工微信登录)',
  wechat_unionid      VARCHAR(64)                            COMMENT '微信 UnionID(跨应用统一)',
  password_hash       VARCHAR(128)                           COMMENT '密码 Bcrypt 哈希(禁止明文)',
  password_salt       VARCHAR(64)                            COMMENT '密码盐',
  two_factor_enabled  BIT(1)       NOT NULL DEFAULT 0        COMMENT '是否启用 2FA',
  two_factor_secret   VARCHAR(128)                           COMMENT 'TOTP secret(加密存储)',
  nickname            VARCHAR(64)                            COMMENT '昵称',
  avatar              VARCHAR(512)                           COMMENT '头像 URL',
  status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / LOCKED / DISABLED',
  status_reason       VARCHAR(255)                           COMMENT '状态原因(锁定/禁用)',
  last_login_time     DATETIME                               COMMENT '上次登录时间',
  last_login_ip       VARCHAR(64)                            COMMENT '上次登录 IP',
  login_fail_count    INT         NOT NULL DEFAULT 0         COMMENT '连续失败次数(5 次锁定)',
  creator             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time         DATETIME     NOT NULL                  COMMENT '创建时间',
  updater             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time         DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted             BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',
  UNIQUE KEY uk_tenant_phone (tenant_id, phone, deleted),
  UNIQUE KEY uk_tenant_username (tenant_id, username, deleted),
  UNIQUE KEY uk_tenant_openid (tenant_id, wechat_openid, deleted),
  KEY idx_tenant_role (tenant_id, user_role, status),
  KEY idx_phone (phone),
  KEY idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户主表(4 类用户 + 6 个细分角色统一存储)';

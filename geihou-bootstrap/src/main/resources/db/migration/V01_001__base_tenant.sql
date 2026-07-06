-- Geihou Bootstrap runtime migration copy.
-- Source candidate: /Users/mac/Desktop/abao-projects/abao-backend/db/migrations/V01_001__base_tenant.sql
-- Header fixed before first Flyway migrate. Do not edit this migration after it is applied;
-- create a later migration version for any future schema or seed change.
-- Geihou is the platform; Abao is tenant sample #1 only.

-- ============================================================
-- TABLE: tenants
-- OWNER: 组 0 - 基础设施
-- 子系统: 跨所有子系统使用(承载层)
-- 微服务: geihou-module-system(48092)
-- 多租户: 自身是租户表,不含 tenant_id
-- 不可篡改: 否
-- ============================================================
CREATE TABLE tenants (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_code     VARCHAR(32)  NOT NULL                  COMMENT '租户编码(对外唯一标识,如 abao / xxx)',
  tenant_name     VARCHAR(128) NOT NULL                  COMMENT '租户名称(显示用)',

  -- A/B 类型
  merchant_type   VARCHAR(8)   NOT NULL DEFAULT 'B'      COMMENT 'ENUM_MERCHANT_TYPE: A(连锁中央厨房) / B(单店)',

  -- 生命周期
  status          VARCHAR(20)  NOT NULL DEFAULT 'TRIAL'  COMMENT 'ENUM_TENANT_STATUS: TRIAL / ACTIVE / SUSPENDED / TERMINATED',
  life_stage      VARCHAR(20)  NOT NULL DEFAULT 'STARTUP' COMMENT 'ENUM_TENANT_LIFE_STAGE: STARTUP / EXPLORATION / FORMATION / DEVELOPMENT / MATURE',

  -- 接入时间
  contract_start_date  DATE                              COMMENT '合同开始日期',
  contract_end_date    DATE                              COMMENT '合同结束日期',

  -- 业务日设置(对应跨日订单切分)
  business_day_cutoff_hour TINYINT NOT NULL DEFAULT 3   COMMENT '业务日切分小时(默认 03:00,即凌晨 3 点切日)',
  timezone        VARCHAR(32)  NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '租户时区',

  -- 联系信息
  contact_name    VARCHAR(64)                            COMMENT '主联系人',
  contact_phone   VARCHAR(32)                            COMMENT '主联系电话',

  -- 审计字段(yudao 标准)
  creator         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',
  updater         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time     DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted         BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除(禁止 hard delete)',

  -- 索引
  UNIQUE KEY uk_tenant_code (tenant_code),
  KEY idx_status (status),
  KEY idx_merchant_type (merchant_type),
  KEY idx_life_stage (life_stage)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户主表(几好平台的客户)';


-- ============================================================
-- TABLE: tenant_subsystem_enabled
-- OWNER: 组 0 - 基础设施
-- 子系统: 跨所有子系统(配置层)
-- 微服务: geihou-module-system(48092)
-- 多租户: 必含 tenant_id
-- 不可篡改: 否(但 changed 时必须留 @OperateLog)
-- ============================================================
CREATE TABLE tenant_subsystem_enabled (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL                  COMMENT '租户ID',
  subsystem_id    TINYINT      NOT NULL                  COMMENT 'ENUM_SUBSYSTEM_ID: 1-11',
  enabled         BIT(1)       NOT NULL DEFAULT 0        COMMENT '是否启用',
  enable_time     DATETIME                               COMMENT '启用时间',
  disable_time    DATETIME                               COMMENT '关闭时间',

  -- 审计字段
  creator         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',
  updater         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time     DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted         BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',

  -- 索引
  UNIQUE KEY uk_tenant_subsystem (tenant_id, subsystem_id),
  KEY idx_subsystem (subsystem_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户 × 子系统启用配置(决定客户能用哪些功能)';

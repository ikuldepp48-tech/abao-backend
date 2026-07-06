-- Geihou Bootstrap runtime migration copy.
-- Source candidate: /Users/mac/Desktop/abao-projects/abao-backend/db/migrations/V01_006__auth_role_permission_tables.sql
-- Header fixed before first Flyway migrate. Do not edit this migration after it is applied;
-- create a later migration version for any future schema or seed change.
-- Geihou is the platform; Abao is tenant sample #1 only.

-- ============================================================
-- TABLE: auth_role
-- OWNER: 组 0 - 基础设施
-- 多租户: 必含 tenant_id(0=平台内置角色,>0=租户自定义角色)
-- H141 PRD 偏离: STORED 可空生成列替代 UNIQUE(...,deleted)
-- ============================================================
CREATE TABLE auth_role (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL DEFAULT 0        COMMENT '租户ID',
  role_code       VARCHAR(64)  NOT NULL                  COMMENT '角色编码(如 SHOP_MANAGER / CASHIER)',
  role_name       VARCHAR(64)  NOT NULL                  COMMENT '角色名称(显示用)',
  description     VARCHAR(255)                           COMMENT '角色说明',
  is_builtin      BIT(1)       NOT NULL DEFAULT 0        COMMENT '是否内置角色(不可删除)',
  is_admin        BIT(1)       NOT NULL DEFAULT 0        COMMENT '是否管理员角色',
  status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
  creator         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',
  updater         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time     DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted         BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',
  active_role_code VARCHAR(64) GENERATED ALWAYS AS (IF(deleted = 0, role_code, NULL)) STORED COMMENT 'H141 生成列:未删除=role_code,已删除=NULL',
  UNIQUE KEY uk_tenant_active_role_code (tenant_id, active_role_code),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- ============================================================
-- TABLE: auth_user_role
-- 用户-角色关联(多对多)
-- H141 PRD 偏离: STORED 可空生成列替代 UNIQUE(...,deleted)
-- ============================================================
CREATE TABLE auth_user_role (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL                  COMMENT '租户ID',
  user_id         BIGINT       NOT NULL                  COMMENT '用户ID',
  role_id         BIGINT       NOT NULL                  COMMENT '角色ID',
  creator         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',
  updater         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time     DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted         BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',
  active_user_id  BIGINT       GENERATED ALWAYS AS (IF(deleted = 0, user_id, NULL)) STORED COMMENT 'H141 生成列:未删除=user_id,已删除=NULL',
  active_role_id  BIGINT       GENERATED ALWAYS AS (IF(deleted = 0, role_id, NULL)) STORED COMMENT 'H141 生成列:未删除=role_id,已删除=NULL',
  UNIQUE KEY uk_tenant_active_user_role (tenant_id, active_user_id, active_role_id),
  KEY idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联';

-- ============================================================
-- TABLE: auth_permission
-- 权限点定义(全局,跨租户共享 — 内置定义)
-- H141 PRD 偏离: STORED 可空生成列替代 UNIQUE(...,deleted)
-- ============================================================
CREATE TABLE auth_permission (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  permission_code VARCHAR(128) NOT NULL                  COMMENT '权限编码(如 ORDER:READ / FINANCE:WRITE)',
  permission_name VARCHAR(128) NOT NULL                  COMMENT '权限名称',
  subsystem_id    TINYINT                                COMMENT 'ENUM_SUBSYSTEM_ID(1-11),NULL=跨子系统通用',
  module_name     VARCHAR(64)                            COMMENT '所属模块(如 order / finance)',
  resource        VARCHAR(64)                            COMMENT '资源(如 order / customer)',
  action          VARCHAR(32)                            COMMENT '动作(READ / WRITE / DELETE / EXPORT)',
  risk_level      VARCHAR(20)  NOT NULL DEFAULT 'LOW'    COMMENT 'LOW / MEDIUM / HIGH(HIGH 操作必须二次审批)',
  description     VARCHAR(255)                           COMMENT '说明',
  creator         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',
  updater         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time     DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted         BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',
  active_permission_code VARCHAR(128) GENERATED ALWAYS AS (IF(deleted = 0, permission_code, NULL)) STORED COMMENT 'H141 生成列:未删除=permission_code,已删除=NULL',
  UNIQUE KEY uk_active_permission_code (active_permission_code),
  KEY idx_subsystem_module (subsystem_id, module_name),
  KEY idx_risk_level (risk_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限点定义(全局)';

-- ============================================================
-- TABLE: auth_role_permission
-- 角色-权限关联
-- H141 PRD 偏离: STORED 可空生成列替代 UNIQUE(...,deleted)
-- ============================================================
CREATE TABLE auth_role_permission (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL DEFAULT 0        COMMENT '租户ID(0=内置角色权限)',
  role_id         BIGINT       NOT NULL                  COMMENT '角色ID',
  permission_id   BIGINT       NOT NULL                  COMMENT '权限ID',
  creator         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',
  updater         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time     DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted         BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',
  active_role_id      BIGINT   GENERATED ALWAYS AS (IF(deleted = 0, role_id, NULL)) STORED COMMENT 'H141 生成列:未删除=role_id,已删除=NULL',
  active_permission_id BIGINT  GENERATED ALWAYS AS (IF(deleted = 0, permission_id, NULL)) STORED COMMENT 'H141 生成列:未删除=permission_id,已删除=NULL',
  UNIQUE KEY uk_tenant_active_role_permission (tenant_id, active_role_id, active_permission_id),
  KEY idx_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-权限关联';

-- ============================================================
-- 初始化内置角色(tenant_id=0,全平台共用)
-- OWNER: 组 0 - 基础设施
-- ============================================================
INSERT INTO auth_role (tenant_id, role_code, role_name, description, is_builtin, is_admin, status, creator, create_time, updater, update_time) VALUES
-- 平台层角色
(0, 'PLATFORM_OPERATOR',   '平台运营',   '几好平台运营人员(管理租户 + 全平台数据)', 1, 1, 'ACTIVE', 'system', NOW(), 'system', NOW()),
(0, 'CONSULTANT',          '咨询师',     '几好商业咨询师(跨租户服务 + 全审计)', 1, 0, 'ACTIVE', 'system', NOW(), 'system', NOW()),
(0, 'PLATFORM_DEVELOPER',  '平台开发者', '几好平台技术人员(运维 + 监控)', 1, 1, 'ACTIVE', 'system', NOW(), 'system', NOW()),
-- 客户租户内角色(模板,各租户可基于此 fork)
(0, 'OWNER',               '老板',       '企业老板(全权限)', 1, 1, 'ACTIVE', 'system', NOW(), 'system', NOW()),
(0, 'SHOP_MANAGER',        '店长',       '门店店长(店铺运营全权限)', 1, 0, 'ACTIVE', 'system', NOW(), 'system', NOW()),
(0, 'CK_MANAGER',          '中央厨房负责人', 'A 类型中央厨房负责人', 1, 0, 'ACTIVE', 'system', NOW(), 'system', NOW()),
(0, 'CASHIER',             '收银员',     '门店收银员', 1, 0, 'ACTIVE', 'system', NOW(), 'system', NOW()),
(0, 'WAITER',              '服务员',     '门店服务员', 1, 0, 'ACTIVE', 'system', NOW(), 'system', NOW()),
(0, 'KITCHEN_COOK',        '厨房后厨',   '门店后厨', 1, 0, 'ACTIVE', 'system', NOW(), 'system', NOW()),
(0, 'CK_WORKER',           '中央厨房工人', 'A 类型中央厨房一线员工', 1, 0, 'ACTIVE', 'system', NOW(), 'system', NOW()),
(0, 'CUSTOMER',            '顾客',       '终端消费者', 1, 0, 'ACTIVE', 'system', NOW(), 'system', NOW());

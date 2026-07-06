-- Geihou Bootstrap runtime migration copy.
-- Source candidate: /Users/mac/Desktop/abao-projects/abao-backend/db/migrations/V01_002__base_microservice.sql
-- Header fixed before first Flyway migrate. Do not edit this migration after it is applied;
-- create a later migration version for any future schema or seed change.
-- Geihou is the platform; Abao is tenant sample #1 only.

-- ============================================================
-- TABLE: microservice_registry
-- OWNER: 组 0 - 基础设施
-- 子系统: 运维层(承载所有 11 子系统的元数据)
-- 微服务: geihou-module-infra(48093)
-- 多租户: 否(运维元数据,跨租户共享)
-- 不可篡改: 否
-- ============================================================
CREATE TABLE microservice_registry (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  service_name    VARCHAR(64)  NOT NULL                  COMMENT '服务名(如 geihou-module-finance)',
  service_port    INT          NOT NULL                  COMMENT '服务端口(48080-48091/92/93)',
  subsystem_id    TINYINT                                COMMENT 'ENUM_SUBSYSTEM_ID: 1-11(公共服务为 NULL)',
  service_type    VARCHAR(20)  NOT NULL                  COMMENT 'BUSINESS(业务) / COMMON(公共) / GATEWAY(网关)',
  health_check_url VARCHAR(255) NOT NULL                 COMMENT '健康检查 URL',

  -- 启动配置
  startup_priority TINYINT     NOT NULL DEFAULT 50       COMMENT '启动优先级(1-100,小的先启动)',
  is_required     BIT(1)       NOT NULL DEFAULT 1        COMMENT '是否必启(运维参考)',

  -- 审计字段
  creator         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',
  updater         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time     DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted         BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',

  -- 索引
  UNIQUE KEY uk_service_name (service_name),
  KEY idx_subsystem (subsystem_id),
  KEY idx_type (service_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微服务注册元数据(运维 + 监控用)';


-- ============================================================
-- TABLE: service_health_log
-- OWNER: 组 0 - 基础设施
-- 子系统: 运维层
-- 微服务: geihou-module-infra(48093)
-- 多租户: 否
-- 不可篡改: 是(日志类,只 INSERT 不 UPDATE/DELETE)
-- ============================================================
CREATE TABLE service_health_log (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  service_name    VARCHAR(64)  NOT NULL                  COMMENT '服务名',
  check_time      DATETIME     NOT NULL                  COMMENT '检查时间',
  status          VARCHAR(16)  NOT NULL                  COMMENT 'UP / DOWN / TIMEOUT / DEGRADED',
  response_time_ms INT                                   COMMENT '响应时间(ms)',
  error_message   TEXT                                   COMMENT '错误信息',

  -- 仅 INSERT 字段
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',

  -- 索引
  KEY idx_service_check_time (service_name, check_time),
  KEY idx_status (status, check_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务健康检查历史(不可篡改日志)';

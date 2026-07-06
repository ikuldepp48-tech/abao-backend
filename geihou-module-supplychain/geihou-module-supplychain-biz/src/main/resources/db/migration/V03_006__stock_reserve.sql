-- V03_006__stock_reserve.sql
-- G2-01B1: Stock reservation table
-- Codex 裁决 #5: 新增 stock_reserve 表，记录 checkout_session/source_record_id 级预留、幂等、状态 RESERVED/RELEASED/COMMITTED
-- status 是表内状态，不是全局 ENUM_STOCK_EVENT_TYPE

CREATE TABLE stock_reserve (
  id                BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id         BIGINT       NOT NULL                  COMMENT '租户ID',

  -- 预留关联
  stock_item_id     BIGINT       NOT NULL                  COMMENT '库存品项 ID',
  location_id       BIGINT       NOT NULL                  COMMENT '库位 ID',
  sku_code          VARCHAR(64)  NOT NULL                  COMMENT 'SKU(冗余,加速查询)',

  -- 预留数量
  quantity          DECIMAL(18,4) NOT NULL                 COMMENT '预留数量(永远为正)',
  unit              VARCHAR(16)  NOT NULL                  COMMENT '单位(冗余,commit 时写入 stock_event)',

  -- 来源关联（checkout_session 级或 source_record_id 级）
  source_module     VARCHAR(64)  NOT NULL                  COMMENT '来源模块(checkout / order / manual)',
  source_record_id  BIGINT       NOT NULL                  COMMENT '来源记录 ID(checkout_session.id 等)',
  reference_no      VARCHAR(64)                            COMMENT '业务单号',

  -- 幂等键（同 tenant + idempotent_key 只能创建一条 RESERVED）
  idempotent_key    VARCHAR(64)  NOT NULL                  COMMENT '幂等键(客户端 UUID)',

  -- 表内状态（非全局 ENUM_STOCK_EVENT_TYPE）
  status            VARCHAR(20)  NOT NULL DEFAULT 'RESERVED' COMMENT 'RESERVED / RELEASED / COMMITTED',

  -- 关联事件（commit 时写入的 stock_event.id）
  commit_event_id   BIGINT                                 COMMENT 'commit 时写入的 CONSUME_OUT 事件 ID',

  -- 操作人
  operator_user_id  BIGINT       NOT NULL                  COMMENT '操作人',

  -- 审计
  creator           VARCHAR(64)  NOT NULL DEFAULT '',
  create_time       DATETIME     NOT NULL,
  updater           VARCHAR(64)  NOT NULL DEFAULT '',
  update_time       DATETIME     NOT NULL,
  deleted           BIT(1)       NOT NULL DEFAULT 0,

  -- 索引
  UNIQUE KEY uk_tenant_idempotent (tenant_id, idempotent_key, deleted),
  KEY idx_tenant_source (tenant_id, source_module, source_record_id),
  KEY idx_tenant_item_location (tenant_id, stock_item_id, location_id, status),
  KEY idx_tenant_status (tenant_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存预留记录(RESERVED/RELEASED/COMMITTED)';

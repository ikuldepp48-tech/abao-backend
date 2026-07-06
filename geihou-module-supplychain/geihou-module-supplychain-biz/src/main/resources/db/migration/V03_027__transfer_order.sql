-- V03_027__transfer_order.sql
-- G2-02S: transfer_order 调拨单主表 + transfer_order_item 调拨单明细
-- Source: PRD-组2-02 §2.1 (transfer_order), §4.2 (TRANSFER_OUT/TRANSFER_IN 链路), §4.3 (SENT/RECEIVED)
-- 状态机: PENDING → SENT → RECEIVED; PENDING → CANCELLED; SENT 不可取消
-- 库存变更通过 StockEventService.recordEvent 写 TRANSFER_OUT/TRANSFER_IN 事件，不直接写 stock_balance

CREATE TABLE transfer_order (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id           BIGINT       NOT NULL                  COMMENT '租户ID(tenant_id first index)',

  -- 单号
  transfer_no         VARCHAR(64)  NOT NULL                  COMMENT '调拨单号(租户内唯一)',

  -- 源/目标库位
  from_location_id    BIGINT       NOT NULL                  COMMENT '源库位ID(stock_location)',
  to_location_id      BIGINT       NOT NULL                  COMMENT '目标库位ID(stock_location)',

  -- 状态机
  status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / SENT / RECEIVED / CANCELLED',

  -- 操作人
  created_by          BIGINT       NOT NULL                  COMMENT '创建人ID',
  shipped_by          BIGINT                                 COMMENT '发货人ID',
  received_by         BIGINT                                 COMMENT '收货人ID',
  cancelled_by        BIGINT                                 COMMENT '取消人ID',

  -- 时间戳
  shipped_at          DATETIME                               COMMENT '发货时间(SENT 时填写)',
  received_at         DATETIME                               COMMENT '收货时间(RECEIVED 时填写)',
  cancelled_at        DATETIME                               COMMENT '取消时间(CANCELLED 时填写)',

  -- 备注
  remark              VARCHAR(500)                           COMMENT '备注',
  cancel_reason       VARCHAR(500)                           COMMENT '取消原因(CANCELLED 时必填)',

  -- 审计
  creator             VARCHAR(64)  NOT NULL DEFAULT '',
  create_time         DATETIME     NOT NULL,
  updater             VARCHAR(64)  NOT NULL DEFAULT '',
  update_time         DATETIME     NOT NULL,
  deleted             BIT(1)       NOT NULL DEFAULT 0,

  UNIQUE KEY uk_tenant_transfer_no (tenant_id, transfer_no, deleted),
  KEY idx_tenant_from_to (tenant_id, from_location_id, to_location_id),
  KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调拨单主表';

CREATE TABLE transfer_order_item (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id           BIGINT       NOT NULL                  COMMENT '租户ID',

  transfer_order_id   BIGINT       NOT NULL                  COMMENT '调拨单ID',

  -- 物料信息
  product_id          BIGINT       NOT NULL                  COMMENT '产品ID(product_master)',
  stock_item_id       BIGINT       NOT NULL                  COMMENT '库存品项ID(stock_item)',
  sku_code            VARCHAR(64)  NOT NULL                  COMMENT 'SKU编码(冗余)',

  -- 数量/单位
  quantity            DECIMAL(18,4) NOT NULL                 COMMENT '调拨数量(正数)',
  unit                VARCHAR(16)  NOT NULL                  COMMENT '单位',

  -- 关联库存事件
  out_event_id        BIGINT                                 COMMENT '发货时写入的 TRANSFER_OUT stock_event ID',
  in_event_id         BIGINT                                 COMMENT '收货时写入的 TRANSFER_IN stock_event ID',

  -- 审计
  creator             VARCHAR(64)  NOT NULL DEFAULT '',
  create_time         DATETIME     NOT NULL,
  updater             VARCHAR(64)  NOT NULL DEFAULT '',
  update_time         DATETIME     NOT NULL,
  deleted             BIT(1)       NOT NULL DEFAULT 0,

  KEY idx_tenant_order (tenant_id, transfer_order_id),
  KEY idx_tenant_sku (tenant_id, sku_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调拨单明细';

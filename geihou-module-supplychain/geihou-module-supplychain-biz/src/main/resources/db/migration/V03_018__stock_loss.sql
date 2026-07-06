-- V03_018__stock_loss.sql
-- G2-02I-3: stock_loss 损耗/报废单
-- Source: PRD-组2-02 节 2.1, ENUM_LOSS_REASON
-- 注意: DDL 中 status 的 DEFAULT 'PENDING_APPROVE' 仅为兜底，应用层(Service)必须显式设置 status 值，不依赖 DDL default。

CREATE TABLE stock_loss (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL                  COMMENT '租户ID',

  -- 单号
  loss_no         VARCHAR(64)  NOT NULL                  COMMENT '损耗单号(租户内唯一)',

  -- 损耗类型
  loss_type       VARCHAR(16)  NOT NULL                  COMMENT 'LOSS(损耗) / SCRAP(报废)',

  -- 库存项 + 库位
  stock_item_id   BIGINT       NOT NULL                  COMMENT '库存品项ID',
  sku_code        VARCHAR(64)  NOT NULL                  COMMENT 'SKU编码(冗余)',
  location_id     BIGINT       NOT NULL                  COMMENT '库位ID',

  -- 数量 + 金额
  quantity        DECIMAL(18,4) NOT NULL                 COMMENT '损耗数量(正数)',
  unit            VARCHAR(16)  NOT NULL                  COMMENT '单位',
  unit_cost       DECIMAL(18,4)                          COMMENT '单位成本(创建时从stock_balance读取)',
  total_amount    DECIMAL(18,4)                          COMMENT '总金额 = quantity × unit_cost',

  -- 损耗原因 (ENUM_LOSS_REASON)
  loss_reason     VARCHAR(30)  NOT NULL                  COMMENT 'ENUM_LOSS_REASON',
  remark          VARCHAR(500)                           COMMENT '备注(OTHER 原因时必填)',

  -- 状态机
  status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING_APPROVE' COMMENT 'PENDING_APPROVE / APPROVED / REJECTED / CANCELLED',

  -- 审批
  approver_user_id BIGINT                                COMMENT '审批人ID',
  approve_time    DATETIME                               COMMENT '审批时间',
  reject_reason   VARCHAR(500)                           COMMENT '拒绝原因',

  -- 关联库存事件
  stock_event_id  BIGINT                                 COMMENT '审批通过后写入的 stock_event ID',

  -- 操作人
  operator_user_id BIGINT       NOT NULL                 COMMENT '创建人ID',

  -- 审计
  creator         VARCHAR(64)  NOT NULL DEFAULT '',
  create_time     DATETIME     NOT NULL,
  updater         VARCHAR(64)  NOT NULL DEFAULT '',
  update_time     DATETIME     NOT NULL,
  deleted         BIT(1)       NOT NULL DEFAULT 0,

  UNIQUE KEY uk_tenant_loss_no (tenant_id, loss_no, deleted),
  KEY idx_tenant_status (tenant_id, status),
  KEY idx_tenant_reason (tenant_id, loss_reason),
  KEY idx_tenant_item (tenant_id, stock_item_id),
  KEY idx_source_event (stock_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='损耗/报废单';

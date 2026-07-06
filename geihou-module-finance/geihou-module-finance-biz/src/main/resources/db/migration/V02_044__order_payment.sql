-- ============================================================
-- TABLE: order_payment
-- OWNER: 组 1 - 订单管理 (G1-01B payment slice)
-- 订单支付流水(不可篡改) — INSERT ONLY
-- 仅 INSERT(支付记录不可改): 无 deleted 字段, 无 UPDATE/DELETE 路径
-- 金额精度: DECIMAL(18,4) + Java BigDecimal (H5 第 9 项)
-- ============================================================
CREATE TABLE order_payment (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL                  COMMENT '租户ID(不变性 12)',
  order_id        BIGINT       NOT NULL                  COMMENT '订单ID',

  -- 支付流水号(平台返回,唯一)
  payment_no      VARCHAR(64)  NOT NULL                  COMMENT '支付流水号',
  external_no     VARCHAR(128)                           COMMENT '第三方支付流水号(微信/支付宝)',

  -- 支付信息
  payment_method  VARCHAR(32)  NOT NULL                  COMMENT 'ENUM_PAYMENT_METHOD',
  payment_amount  DECIMAL(18,4) NOT NULL                 COMMENT '支付金额',
  payment_status  VARCHAR(20)  NOT NULL                  COMMENT 'INITIATED / SUCCESS / FAILED',

  -- 时间
  initiated_time  DATETIME     NOT NULL                  COMMENT '发起时间',
  paid_time       DATETIME                               COMMENT '支付完成时间(SUCCESS 才有)',
  failed_time     DATETIME                               COMMENT '支付失败时间',
  failed_reason   VARCHAR(255)                           COMMENT '失败原因',

  -- 仅 INSERT(支付记录不可改)
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',

  UNIQUE KEY uk_tenant_payment_no (tenant_id, payment_no),
  KEY idx_tenant_order (tenant_id, order_id),
  KEY idx_tenant_status_time (tenant_id, payment_status, paid_time),
  KEY idx_external (external_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单支付流水(不可篡改)';

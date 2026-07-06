-- ============================================================
-- TABLE: order_refund
-- OWNER: 组 1 - 订单管理 (G1-01C refund chain slice)
-- 退款单(必须关联原单)
-- 金额精度: DECIMAL(18,4) + Java BigDecimal (H5 第 9 项)
-- 软删除: deleted BIT(1) + @TableLogic
-- ============================================================
CREATE TABLE order_refund (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL                  COMMENT '租户ID(不变性 12)',

  refund_no       VARCHAR(32)  NOT NULL                  COMMENT '退款单号',
  original_order_id BIGINT     NOT NULL                  COMMENT '原订单ID(必须存在)',
  original_payment_id BIGINT   NOT NULL                  COMMENT '原支付流水ID',

  -- 退款金额 / 范围
  refund_amount   DECIMAL(18,4) NOT NULL                 COMMENT '退款金额',
  refund_type     VARCHAR(20)  NOT NULL                  COMMENT 'FULL(全退) / PARTIAL(部分退)/ ITEM(按明细退)',
  refund_item_ids VARCHAR(1024)                          COMMENT '部分退款的明细ID(逗号分隔)',

  -- 退款原因(必填)
  reason_type     VARCHAR(32)  NOT NULL                  COMMENT 'CUSTOMER_REQUEST / QUALITY_ISSUE / WRONG_ORDER / OUT_OF_STOCK / OTHER',
  reason_detail   VARCHAR(500) NOT NULL                  COMMENT '详细原因(必填)',

  -- 状态
  status          VARCHAR(20)  NOT NULL                  COMMENT 'ENUM_REFUND_STATUS(SSOT): PENDING_REVIEW / APPROVED / REJECTED / REFUNDING / REFUNDED / REFUND_FAILED',

  -- 审批(高金额必审批)
  approver_user_id BIGINT                                COMMENT '审批人',
  approve_time    DATETIME                               COMMENT '审批时间',
  approve_remark  VARCHAR(500)                           COMMENT '审批备注',

  -- 退款执行
  refund_time     DATETIME                               COMMENT '退款完成时间',
  external_refund_no VARCHAR(128)                        COMMENT '第三方退款流水号',

  -- 失败信息
  fail_reason     VARCHAR(500)                           COMMENT '退款失败原因',

  -- 审计字段
  creator         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人(发起退款的员工)',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',
  updater         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time     DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted         BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',

  UNIQUE KEY uk_tenant_refund_no (tenant_id, refund_no, deleted),
  KEY idx_tenant_original_order (tenant_id, original_order_id),
  KEY idx_tenant_status (tenant_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单(必须关联原单)';

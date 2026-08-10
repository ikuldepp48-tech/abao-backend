-- ============================================================
-- TABLE: finance_stock_saga_intent
-- OWNER: G0-04H185 FIN-CONSISTENCY (slice 2C-2D, saga intent)
-- 子系统: 经营 ① 钱进来(7 大经营动作)
-- 多租户: 必含 tenant_id
-- 软删除: 无 (无 deleted 列, 无 @TableLogic)
-- 外键: 无
--
-- 本表是 checkout 终态化的 durable saga intent: 每个 saga
-- (checkout_session) 一行, 冻结终态化所需全部参数。当前仅支持
-- CHECKOUT saga; REFUND 未来接入。
--
-- 冻结契约:
-- - 不可变身份: (tenant_id, saga_type, saga_id)
-- - saga_id = checkout_session.id
-- - expected_checkout_status 必须为 INITIATED
-- - target_checkout_status ∈ {ABANDONED, EXPIRED, FAILED}
-- - cart_event_type 经 CartEventTypeEnum.fromCode 校验
-- - 配对: ABANDONED/FAILED -> CHECKOUT_ABANDONED, EXPIRED -> CART_EXPIRED
-- - operator_role ∈ {CUSTOMER, STAFF}
--
-- 终态化状态机 (finalization_status):
-- PENDING -> FINALIZED (finalized_at 记录终态化时间)
--
-- 参考:
-- - G0-04H185-FIN-CONSISTENCY-SAGA-PROTOCOL-FREEZE.md (第 7 节 finalizer)
-- ============================================================
CREATE TABLE finance_stock_saga_intent (
  id                        BIGINT UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id                 BIGINT           NOT NULL                  COMMENT '租户ID',

  -- saga 身份
  saga_type                 VARCHAR(16)      NOT NULL                  COMMENT 'FinanceStockSagaType: CHECKOUT',
  saga_id                   BIGINT           NOT NULL                  COMMENT 'saga 实例 ID (checkout_session.id)',

  -- 终态化参数 (冻结)
  cart_id                   BIGINT           NOT NULL                  COMMENT '结算购物车 ID',
  expected_checkout_status  VARCHAR(20)      NOT NULL                  COMMENT 'CAS 期望状态: INITIATED',
  target_checkout_status    VARCHAR(20)      NOT NULL                  COMMENT '终态: ABANDONED/EXPIRED/FAILED',
  cart_event_type           VARCHAR(32)      NOT NULL                  COMMENT 'CartEventTypeEnum code (CHECKOUT_ABANDONED/CART_EXPIRED)',
  operator_user_id          BIGINT           NOT NULL                  COMMENT '操作人 (customer/staff)',
  operator_role             VARCHAR(32)      NOT NULL                  COMMENT 'CUSTOMER / STAFF',

  -- 终态化状态机
  finalization_status       VARCHAR(32)      NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / FINALIZED',
  finalized_at              DATETIME(3)      NULL                      COMMENT '终态化时间',

  -- 审计 (无 creator/updater/deleted)
  create_time               DATETIME(3)      NOT NULL                  COMMENT '创建时间',
  update_time               DATETIME(3)      NOT NULL                  COMMENT '更新时间',

  -- 索引
  UNIQUE KEY uk_fsi_identity
    (tenant_id, saga_type, saga_id),
  KEY idx_fsi_scan
    (tenant_id, finalization_status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='checkout 终态化 durable saga intent 表';

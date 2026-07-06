-- ============================================================
-- TABLE: order_event_log
-- 订单事件流水(不可篡改,只 INSERT)
-- 任何订单状态变更必须写入这张表,用于审计 + 事件回放
-- 无 deleted 字段,无 update/delete 路径 (INSERT-only)
-- ============================================================
CREATE TABLE order_event_log (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL                  COMMENT '租户ID',
  order_id        BIGINT       NOT NULL                  COMMENT '订单ID',

  -- 事件信息
  event_type      VARCHAR(32)  NOT NULL                  COMMENT 'CREATE / PAY / KITCHEN_IN / READY / DELIVER / COMPLETE / CANCEL / REFUND_INITIATE / REFUND_COMPLETE',
  before_status   VARCHAR(32)                            COMMENT '变更前状态',
  after_status    VARCHAR(32)                            COMMENT '变更后状态',

  -- 操作者
  operator_user_id BIGINT                                COMMENT '操作人 user_id',
  operator_role   VARCHAR(32)                            COMMENT 'CUSTOMER / STAFF / OWNER / SYSTEM(系统自动)',

  -- 事件载荷(JSON,合法 JSON)
  payload         TEXT                                   COMMENT 'JSON(必须是合法 JSON — Track A1 教训 4)',

  -- 客户端环境
  client_ip       VARCHAR(64)                            COMMENT '客户端 IP',

  -- 仅 INSERT
  event_time      DATETIME     NOT NULL                  COMMENT '事件时间',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',

  KEY idx_tenant_order_time (tenant_id, order_id, event_time),
  KEY idx_tenant_event_type (tenant_id, event_type, event_time),
  KEY idx_tenant_operator (tenant_id, operator_user_id, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单事件流水(不可篡改)';

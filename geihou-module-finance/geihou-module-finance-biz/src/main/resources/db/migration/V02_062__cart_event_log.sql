-- ============================================================
-- TABLE: cart_event_log
-- OWNER: 组 1-04 购物车结算 (G1-04A first code slice)
-- 子系统: 经营 ① 钱进来(7 大经营动作)
-- 不可篡改: 仅 INSERT, 无 UPDATE/DELETE 路径 (AC-4)
-- 金额精度: DECIMAL(18,4) + Java BigDecimal (H5 第 9 项)
-- Cart 6 根因: 事务三写之一 (根因 3 fire-and-forget 防御)
-- ============================================================
CREATE TABLE cart_event_log (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id           BIGINT       NOT NULL                  COMMENT '租户ID',
  cart_id             BIGINT       NOT NULL                  COMMENT '购物车 ID',

  -- 事件类型 — ENUM_CART_EVENT_TYPE 7 值
  event_type          VARCHAR(32)  NOT NULL                  COMMENT 'ENUM_CART_EVENT_TYPE: ITEM_ADDED / ITEM_QUANTITY_CHANGED / ITEM_REMOVED / CART_CLEARED / CHECKOUT_STARTED / CHECKOUT_ABANDONED / CART_EXPIRED',

  -- 事件时间
  event_time          DATETIME     NOT NULL                  COMMENT '事件发生时间',

  -- 操作人
  operator_user_id    BIGINT       NOT NULL                  COMMENT '操作人 user_id',
  operator_role       VARCHAR(32)  NOT NULL                  COMMENT '操作人角色: CUSTOMER / STAFF',

  -- 事件详情
  sku_id              BIGINT                                 COMMENT '关联 SKU ID',
  quantity_before     INT                                    COMMENT '变更前数量',
  quantity_after      INT                                    COMMENT '变更后数量',
  amount_before       DECIMAL(18,4)                          COMMENT '变更前金额',
  amount_after        DECIMAL(18,4)                          COMMENT '变更后金额',

  -- 扩展信息 (JSON)
  extra               JSON                                   COMMENT '扩展信息 JSON',

  -- 请求信息
  client_ip           VARCHAR(64)                            COMMENT '客户端 IP',
  user_agent          VARCHAR(512)                           COMMENT 'User-Agent',
  device_id           VARCHAR(64)                            COMMENT '设备 ID',

  -- 仅 INSERT, 不可篡改 (无 updater/update_time/deleted)
  create_time         DATETIME     NOT NULL                  COMMENT '创建时间 (仅 INSERT, 不可篡改)',

  -- 索引
  KEY idx_cart_time (cart_id, event_time),
  KEY idx_tenant_event (tenant_id, event_type, event_time),
  KEY idx_operator (operator_user_id, event_time),
  KEY idx_sku_time (sku_id, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车事件流水(不可篡改)';

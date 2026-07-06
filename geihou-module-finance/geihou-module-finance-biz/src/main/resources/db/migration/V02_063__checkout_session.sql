-- ============================================================
-- TABLE: checkout_session
-- OWNER: 组 1-04 购物车结算 (G1-04B checkout session slice)
-- 子系统: 经营 ① 钱进来(7 大经营动作)
-- 多租户: 必含 tenant_id
-- 金额精度: DECIMAL(18,4) + Java BigDecimal (H5 第 9 项)
-- 软删除: deleted BIT(1)
-- CG-7 Option C: order_id remains NULL after simulated pay.
--   Checkout-to-order conversion deferred to G1-04C.
-- CG-8 StockApi degraded: no stock_reservation_id column.
--   Oversell risk: this slice does NOT claim production stock safety.
--   StockApi (组 2-01) integration required for production.
-- CG-9 Simulated payment: payment_method records simulated bridge only.
--   Not real WeChat Pay, no signature verification.
-- CG-11 PromotionApi missing: applied_promotions/applied_coupon_ids
--   always NULL; locked_discount always 0.
-- ============================================================
CREATE TABLE checkout_session (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id           BIGINT       NOT NULL                  COMMENT '租户ID',

  -- 关联购物车
  cart_id             BIGINT       NOT NULL                  COMMENT '关联购物车 ID',
  customer_user_id    BIGINT       NOT NULL                  COMMENT '顾客 user_id',
  shop_id             BIGINT       NOT NULL                  COMMENT '门店 ID',

  -- Session token (server-generated UUID, unique, never client-provided)
  session_token       VARCHAR(64)  NOT NULL                  COMMENT '服务端生成的唯一 session token (UUID)',

  -- 状态 — ENUM_CHECKOUT_STATUS 5 值
  status              VARCHAR(20)  NOT NULL DEFAULT 'INITIATED' COMMENT 'ENUM_CHECKOUT_STATUS: INITIATED / PAID / ABANDONED / EXPIRED / FAILED',

  -- 金额 (全部 DECIMAL(18,4) + Java BigDecimal)
  subtotal_amount     DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '小计金额(锁定快照)',
  discount_amount     DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '优惠总额',
  locked_discount     DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '锁定优惠(CG-11: 始终0)',
  total_amount        DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '总金额(应付)',

  -- 优惠券/促销 (CG-11: 始终 NULL)
  applied_promotions  TEXT                                   COMMENT '已应用促销(CG-11: 始终NULL)',
  applied_coupon_ids  VARCHAR(512)                           COMMENT '已应用优惠券ID(CG-11: 始终NULL)',

  -- 支付信息 (CG-9: 模拟本地支付桥)
  payment_method      VARCHAR(32)                            COMMENT '支付方式 ENUM_PAYMENT_METHOD (模拟)',
  payment_time        DATETIME                               COMMENT '支付时间(模拟支付成功时记录)',
  payment_trade_no    VARCHAR(64)                            COMMENT '支付流水号(模拟)',

  -- 订单 (CG-7 Option C: 始终 NULL, G1-04C 回填)
  order_id            BIGINT                                 COMMENT '订单ID(CG-7: 始终NULL, G1-04C补全)',

  -- 业务日期
  business_date       DATE                                   COMMENT '业务日期(跨日切分)',

  -- 过期时间 (lazy expiry check in service method)
  expire_time         DATETIME     NOT NULL                  COMMENT '过期时间(默认5分钟)',

  -- 渠道
  channel             VARCHAR(32)  NOT NULL                  COMMENT 'ENUM_ORDER_CHANNEL(SSOT)',

  -- 备注
  remark              VARCHAR(512)                           COMMENT '备注',

  -- 审计字段
  creator             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time         DATETIME     NOT NULL                  COMMENT '创建时间',
  updater             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time         DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted             BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',

  -- 索引
  UNIQUE KEY uk_session_token (session_token),
  UNIQUE KEY uk_tenant_cart_active (tenant_id, cart_id, status, deleted),
  KEY idx_tenant_customer (tenant_id, customer_user_id, status),
  KEY idx_tenant_shop_date (tenant_id, shop_id, business_date),
  KEY idx_expire_time (expire_time, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算会话表';

-- ============================================================
-- TABLE: cart_item
-- OWNER: 组 1-04 购物车结算 (G1-04A first code slice)
-- 子系统: 经营 ① 钱进来(7 大经营动作)
-- 金额精度: DECIMAL(18,4) + Java BigDecimal (H5 第 9 项)
-- Cart 6 根因: sku_id BIGINT NOT NULL (根因 2 防御)
-- 快照: sku_name/unit_price 加入时锁定，不可后改 (AC-9)
-- 软删除: deleted BIT(1)
-- ============================================================
CREATE TABLE cart_item (
  id                    BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id             BIGINT       NOT NULL                  COMMENT '租户ID',
  cart_id               BIGINT       NOT NULL                  COMMENT '购物车 ID',

  -- SKU — 严格 BIGINT (根因 2 防御)
  sku_id                BIGINT       NOT NULL                  COMMENT 'SKU ID (严格 BIGINT, 防 Cart 根因 2)',
  spu_id                BIGINT       NOT NULL                  COMMENT 'SPU ID',

  -- 快照 (加入时锁定, 不可后改 — AC-9)
  sku_name_snapshot     VARCHAR(128) NOT NULL                  COMMENT 'SKU 名称快照',
  sku_image_snapshot    VARCHAR(512)                           COMMENT 'SKU 图片快照',
  unit_price_snapshot   DECIMAL(18,4) NOT NULL                 COMMENT '单价快照 (加入时锁定)',

  -- 数量
  quantity              INT          NOT NULL                  COMMENT '数量',

  -- 加料/做法 (JSON)
  options               JSON                                   COMMENT '加料/做法 JSON',
  options_extra_price   DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '加料额外价格',

  -- 金额 (全部 DECIMAL(18,4) + Java BigDecimal)
  item_subtotal         DECIMAL(18,4) NOT NULL                 COMMENT '项小计',
  item_discount         DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '项优惠',
  item_total            DECIMAL(18,4) NOT NULL                 COMMENT '项总价',

  -- 营销
  applied_promotion_id  BIGINT                                 COMMENT '应用的促销活动 ID',

  -- 项状态 — ENUM_CART_ITEM_STATE 4 值
  item_state            VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT 'ENUM_CART_ITEM_STATE: NORMAL / SOLD_OUT / PRICE_CHANGED / UNAVAILABLE',

  -- 审计字段
  creator               VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time           DATETIME     NOT NULL                  COMMENT '创建时间',
  updater               VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time           DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted               BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',

  -- 索引
  KEY idx_cart (cart_id, deleted),
  KEY idx_sku (sku_id),
  KEY idx_tenant_sku (tenant_id, sku_id),
  KEY idx_promotion (applied_promotion_id),
  KEY idx_item_state (item_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车项明细';

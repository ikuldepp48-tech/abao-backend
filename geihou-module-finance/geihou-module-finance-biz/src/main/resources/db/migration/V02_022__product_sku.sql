-- ============================================================
-- TABLE: product_sku
-- Specific specification under an SPU (e.g., "Medium Americano", "Large Americano")
-- Source: PRD-G1-02 Section 2.2 DDL
-- Note: sku_id is BIGINT strict (Cart root cause 2 defense)
-- Note: All price fields use DECIMAL(18,4) + Java BigDecimal, no double/float
-- ============================================================
CREATE TABLE product_sku (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY  COMMENT 'SKU ID (BIGINT strict, Cart root cause 2 defense)',
  tenant_id           BIGINT       NOT NULL                  COMMENT 'Tenant ID',
  spu_id              BIGINT       NOT NULL                  COMMENT 'SPU ID',

  sku_code            VARCHAR(64)  NOT NULL                  COMMENT 'SKU code',
  sku_name            VARCHAR(128) NOT NULL                  COMMENT 'SKU name (includes spec, e.g., "Americano - Large")',
  spec_attributes     JSON                                   COMMENT 'Spec attributes JSON (e.g., {"size":"large","temperature":"hot"})',

  -- Prices (must use BigDecimal, never double/float)
  list_price          DECIMAL(18,4) NOT NULL                 COMMENT 'List price (original price, BigDecimal)',
  selling_price       DECIMAL(18,4) NOT NULL                 COMMENT 'Current selling price',
  cost_price          DECIMAL(18,4)                          COMMENT 'Cost price (from BOM reverse, if available)',
  member_price        DECIMAL(18,4)                          COMMENT 'Member price (nullable)',

  -- Purchase limits
  daily_limit         INT                                    COMMENT 'Daily purchase limit (NULL = unlimited)',
  per_order_limit     INT                                    COMMENT 'Per-order purchase limit',
  min_order_quantity  INT          NOT NULL DEFAULT 1        COMMENT 'Minimum order quantity',

  -- Stock strategy (actual stock in stock_balance)
  stock_strategy      VARCHAR(20)  NOT NULL                  COMMENT 'TRACK_STOCK (needs BOM reverse stock) / UNLIMITED (virtual / made-to-order)',

  -- Status (ENUM_SKU_STATUS: NEW/ACTIVE/SOLD_OUT/PAUSED/DEPRECATED)
  status              VARCHAR(20)  NOT NULL DEFAULT 'NEW'    COMMENT 'NEW / ACTIVE / SOLD_OUT (sold out) / PAUSED / DEPRECATED',
  status_reason       VARCHAR(255)                           COMMENT 'Status change reason',

  -- Display
  primary_image_url   VARCHAR(512)                           COMMENT 'SKU primary image (defaults to SPU image)',

  -- Sales stats (real)
  total_sold_count    INT          NOT NULL DEFAULT 0        COMMENT 'Cumulative real sold count',

  -- Audit fields
  creator             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT 'Creator',
  create_time         DATETIME     NOT NULL                  COMMENT 'Create time',
  updater             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT 'Updater',
  update_time         DATETIME     NOT NULL                  COMMENT 'Update time',
  deleted             BIT(1)       NOT NULL DEFAULT 0        COMMENT 'Soft delete',

  UNIQUE KEY uk_tenant_sku (tenant_id, sku_code, deleted),
  KEY idx_spu (spu_id, status),
  KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Product SKU table';

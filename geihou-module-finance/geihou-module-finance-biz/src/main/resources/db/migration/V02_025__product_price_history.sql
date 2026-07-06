-- ============================================================
-- TABLE: product_price_history
-- Price change history (immutable, INSERT-only)
-- Invariance 11: All price changes must leave a trace
-- Source: PRD-G1-02 Section 2.2 DDL
-- ============================================================
CREATE TABLE product_price_history (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id           BIGINT       NOT NULL                  COMMENT 'Tenant ID',
  sku_id              BIGINT       NOT NULL                  COMMENT 'SKU ID',

  -- Old / new prices
  old_list_price      DECIMAL(18,4)                          COMMENT 'Old list price',
  new_list_price      DECIMAL(18,4) NOT NULL                 COMMENT 'New list price',
  old_selling_price   DECIMAL(18,4)                          COMMENT 'Old selling price',
  new_selling_price   DECIMAL(18,4) NOT NULL                 COMMENT 'New selling price',

  -- Change info
  change_reason       VARCHAR(255) NOT NULL                  COMMENT 'Change reason (required, prevents arbitrary price changes)',
  change_type         VARCHAR(32)  NOT NULL                  COMMENT 'MANUAL (manual) / PROMOTION (promotion) / COST_BASED (cost change) / MARKET_BASED (market adjustment)',
  changed_by_user_id  BIGINT       NOT NULL                  COMMENT 'User ID who made the change',

  -- INSERT-only fields (no update/delete path)
  change_time         DATETIME     NOT NULL                  COMMENT 'Change time',
  create_time         DATETIME     NOT NULL                  COMMENT 'Create time',

  KEY idx_sku_time (sku_id, change_time),
  KEY idx_tenant_type (tenant_id, change_type, change_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Price change history (immutable)';

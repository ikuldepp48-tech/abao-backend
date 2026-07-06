-- ============================================================
-- TABLE: product_spu
-- Standard Product Unit (e.g., "Americano")
-- Source: PRD-G1-02 Section 2.2 DDL
-- Note: spu_type uses FINISHED (not NORMAL) per G1-02C Codex ruling
-- ============================================================
CREATE TABLE product_spu (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id           BIGINT       NOT NULL                  COMMENT 'Tenant ID',

  spu_code            VARCHAR(64)  NOT NULL                  COMMENT 'SPU code (unique within tenant)',
  spu_name            VARCHAR(128) NOT NULL                  COMMENT 'SPU name',
  spu_short_name      VARCHAR(64)                            COMMENT 'Short name (for POS display)',

  category_id         BIGINT       NOT NULL                  COMMENT 'Product category ID',

  -- Product type (affects BOM + stock logic)
  spu_type            VARCHAR(32)  NOT NULL                  COMMENT 'FINISHED (finished product) / SEMI_FINISHED (semi-finished, type A) / RAW_MATERIAL (raw material) / COMBO (combo, future) / SERVICE (service fee, future)',

  -- Display info
  primary_image_url   VARCHAR(512)                           COMMENT 'Primary image URL',
  image_gallery       JSON                                   COMMENT 'Image gallery JSON array',
  description         TEXT                                   COMMENT 'Description',

  -- Marketing flags
  is_recommended      BIT(1)       NOT NULL DEFAULT 0        COMMENT 'Is recommended',
  is_new_arrival      BIT(1)       NOT NULL DEFAULT 0        COMMENT 'Is new arrival',
  sort_order          INT          NOT NULL DEFAULT 0        COMMENT 'Sort weight',

  -- Sales stats (snapshot, real data, not fabricated)
  total_sold_count    INT          NOT NULL DEFAULT 0        COMMENT 'Cumulative real sold count (data integrity)',

  -- Status (ENUM_SPU_STATUS: NEW/ACTIVE/PAUSED/DEPRECATED)
  status              VARCHAR(20)  NOT NULL DEFAULT 'NEW'    COMMENT 'NEW (draft) / ACTIVE (on sale) / PAUSED (temporarily off) / DEPRECATED (permanently retired)',

  -- Audit fields
  creator             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT 'Creator',
  create_time         DATETIME     NOT NULL                  COMMENT 'Create time',
  updater             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT 'Updater',
  update_time         DATETIME     NOT NULL                  COMMENT 'Update time',
  deleted             BIT(1)       NOT NULL DEFAULT 0        COMMENT 'Soft delete (no hard delete allowed)',

  UNIQUE KEY uk_tenant_spu (tenant_id, spu_code, deleted),
  KEY idx_tenant_category (tenant_id, category_id, status),
  KEY idx_tenant_status (tenant_id, status, sort_order),
  KEY idx_type (tenant_id, spu_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Product SPU master table';

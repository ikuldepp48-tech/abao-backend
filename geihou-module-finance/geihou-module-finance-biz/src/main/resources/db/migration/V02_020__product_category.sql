-- ============================================================
-- TABLE: product_category
-- Product category (supports multi-level hierarchy)
-- Source: PRD-G1-02 Section 2.2 DDL
-- ============================================================
CREATE TABLE product_category (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id           BIGINT       NOT NULL                  COMMENT 'Tenant ID',

  category_code       VARCHAR(64)  NOT NULL                  COMMENT 'Category code (unique within tenant)',
  category_name       VARCHAR(128) NOT NULL                  COMMENT 'Category name',
  parent_category_id  BIGINT                                 COMMENT 'Parent category ID (NULL = top level)',
  category_path       VARCHAR(512)                           COMMENT 'Path (root/mid/leaf, accelerates queries)',
  level               TINYINT      NOT NULL                  COMMENT 'Hierarchy level (1/2/3)',

  icon                VARCHAR(255)                           COMMENT 'Icon URL',
  sort_order          INT          NOT NULL DEFAULT 0        COMMENT 'Sort order',
  status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',

  -- Audit fields
  creator             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT 'Creator',
  create_time         DATETIME     NOT NULL                  COMMENT 'Create time',
  updater             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT 'Updater',
  update_time         DATETIME     NOT NULL                  COMMENT 'Update time',
  deleted             BIT(1)       NOT NULL DEFAULT 0        COMMENT 'Soft delete (no hard delete allowed)',

  UNIQUE KEY uk_tenant_code (tenant_id, category_code, deleted),
  KEY idx_parent (parent_category_id, status, sort_order),
  KEY idx_path (category_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Product category';

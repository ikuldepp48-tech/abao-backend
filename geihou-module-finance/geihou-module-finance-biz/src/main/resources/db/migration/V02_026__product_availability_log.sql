-- ============================================================
-- TABLE: product_availability_log
-- Availability (status) change history (immutable, INSERT-only)
-- Invariance 11: All status changes must leave a trace
-- Source: PRD-G1-02 Section 2.2 DDL
-- ============================================================
CREATE TABLE product_availability_log (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id           BIGINT       NOT NULL                  COMMENT 'Tenant ID',

  -- Target (SPU, SKU, or COMBO)
  target_type         VARCHAR(10)  NOT NULL                  COMMENT 'SPU / SKU / COMBO',
  target_id           BIGINT       NOT NULL                  COMMENT 'Target ID',

  -- Status change
  old_status          VARCHAR(20)                            COMMENT 'Old status',
  new_status          VARCHAR(20)  NOT NULL                  COMMENT 'New status',

  -- Reason
  change_reason       VARCHAR(255) NOT NULL                  COMMENT 'Change reason',
  changed_by_user_id  BIGINT       NOT NULL                  COMMENT 'User ID who made the change',

  -- INSERT-only fields
  change_time         DATETIME     NOT NULL                  COMMENT 'Change time',
  create_time         DATETIME     NOT NULL                  COMMENT 'Create time',

  KEY idx_target (target_type, target_id, change_time),
  KEY idx_tenant_time (tenant_id, change_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Availability change history (immutable)';

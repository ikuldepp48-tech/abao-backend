-- Geihou Bootstrap runtime migration copy.
-- Runtime-applied by Flyway from geihou-bootstrap classpath resources.
-- Keep the DDL body byte-identical to the candidate copy.

CREATE TABLE auth_rbac_provisioning_audit (
  id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  attempt_id       VARCHAR(64)  NOT NULL,
  tenant_id        BIGINT       NOT NULL,
  merchant_type    VARCHAR(8)   NULL,
  outcome          VARCHAR(16)  NOT NULL,
  roles_expected   INT          NOT NULL DEFAULT 0,
  roles_inserted   INT          NOT NULL DEFAULT 0,
  roles_skipped    INT          NOT NULL DEFAULT 0,
  grants_expected  INT          NOT NULL DEFAULT 0,
  grants_inserted  INT          NOT NULL DEFAULT 0,
  grants_skipped   INT          NOT NULL DEFAULT 0,
  grants_preserved INT          NOT NULL DEFAULT 0,
  error_class      VARCHAR(255) NULL,
  error_detail     VARCHAR(1024) NULL,
  triggered_by     VARCHAR(64)  NOT NULL DEFAULT '',
  creator          VARCHAR(64)  NOT NULL DEFAULT '',
  create_time      DATETIME     NOT NULL,
  updater          VARCHAR(64)  NOT NULL DEFAULT '',
  update_time      DATETIME     NOT NULL,
  KEY idx_attempt (attempt_id),
  KEY idx_tenant_outcome (tenant_id, outcome),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

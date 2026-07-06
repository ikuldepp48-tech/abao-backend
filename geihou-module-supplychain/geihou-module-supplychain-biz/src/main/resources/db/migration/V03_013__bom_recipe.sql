-- V03_013__bom_recipe.sql
-- G2-02A: BOM recipe header table (配方头)

CREATE TABLE bom_recipe (
  id                BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id         BIGINT       NOT NULL                  COMMENT '租户ID',
  product_id        BIGINT       NOT NULL                  COMMENT '产品ID(product_master.id)',
  version_no        INT          NOT NULL                  COMMENT '版本号(同产品递增)',
  status            VARCHAR(32)  NOT NULL                  COMMENT '状态(DRAFT/ACTIVE/ARCHIVED)',
  remark            VARCHAR(512)                           COMMENT '备注',
  creator           VARCHAR(64)  NOT NULL DEFAULT '',
  create_time       DATETIME     NOT NULL,
  updater           VARCHAR(64)  NOT NULL DEFAULT '',
  update_time       DATETIME     NOT NULL,
  deleted           BIT(1)       NOT NULL DEFAULT 0,
  UNIQUE KEY uk_tenant_product_version (tenant_id, product_id, version_no, deleted),
  KEY idx_tenant_product_status (tenant_id, product_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BOM配方头';

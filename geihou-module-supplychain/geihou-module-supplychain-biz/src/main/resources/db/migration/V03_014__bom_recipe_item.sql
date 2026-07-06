-- V03_014__bom_recipe_item.sql
-- G2-02A: BOM recipe item table (配方明细)

CREATE TABLE bom_recipe_item (
  id                      BIGINT         UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id               BIGINT         NOT NULL                  COMMENT '租户ID',
  recipe_id               BIGINT         NOT NULL                  COMMENT '配方ID(bom_recipe.id)',
  component_product_id    BIGINT         NOT NULL                  COMMENT '子项产品ID(product_master.id)',
  quantity                DECIMAL(18,6)  NOT NULL                  COMMENT '用量',
  waste_rate              DECIMAL(10,6)  NOT NULL DEFAULT 0        COMMENT '损耗率(0 <= waste_rate < 1)',
  creator                 VARCHAR(64)    NOT NULL DEFAULT '',
  create_time             DATETIME       NOT NULL,
  updater                 VARCHAR(64)    NOT NULL DEFAULT '',
  update_time             DATETIME       NOT NULL,
  deleted                 BIT(1)         NOT NULL DEFAULT 0,
  KEY idx_tenant_recipe (tenant_id, recipe_id, deleted),
  KEY idx_tenant_component (tenant_id, component_product_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BOM配方明细';

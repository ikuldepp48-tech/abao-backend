-- V03_005__stock_item.sql
-- G2-01B1: Stock item master table

CREATE TABLE stock_item (
  id                BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id         BIGINT       NOT NULL                  COMMENT '租户ID',
  sku_code          VARCHAR(64)  NOT NULL                  COMMENT 'SKU 编码',
  item_name         VARCHAR(128) NOT NULL                  COMMENT '品项名',
  category          VARCHAR(64)                            COMMENT '分类(肉/菜/调料/包材)',
  unit              VARCHAR(16)  NOT NULL                  COMMENT '单位',
  shelf_life_days   INT                                    COMMENT '保质期(天)',
  storage_condition VARCHAR(64)                            COMMENT '储存条件(常温/冷藏/冷冻)',
  is_raw_material   BIT(1)       NOT NULL DEFAULT 0        COMMENT '是否原料',
  is_semi_finished  BIT(1)       NOT NULL DEFAULT 0        COMMENT '是否半成品',
  is_finished       BIT(1)       NOT NULL DEFAULT 0        COMMENT '是否成品',
  creator           VARCHAR(64)  NOT NULL DEFAULT '',
  create_time       DATETIME     NOT NULL,
  updater           VARCHAR(64)  NOT NULL DEFAULT '',
  update_time       DATETIME     NOT NULL,
  deleted           BIT(1)       NOT NULL DEFAULT 0,
  UNIQUE KEY uk_tenant_sku (tenant_id, sku_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存品项';

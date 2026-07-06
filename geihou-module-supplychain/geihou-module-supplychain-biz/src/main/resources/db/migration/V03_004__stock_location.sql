-- V03_004__stock_location.sql
-- G2-01B1: Stock location master table

CREATE TABLE stock_location (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL                  COMMENT '租户ID',
  location_code   VARCHAR(64)  NOT NULL                  COMMENT '库位编码',
  location_name   VARCHAR(128) NOT NULL                  COMMENT '库位名',
  location_type   VARCHAR(32)  NOT NULL                  COMMENT 'CENTRAL_KITCHEN(中央厨房仓)/ STORE(门店仓)/ TRANSIT(在途)/ VIRTUAL(虚拟)',
  store_id        BIGINT                                 COMMENT '所属门店(STORE 类型必填)',
  is_active       BIT(1)       NOT NULL DEFAULT 1        COMMENT '是否启用',
  creator         VARCHAR(64)  NOT NULL DEFAULT '',
  create_time     DATETIME     NOT NULL,
  updater         VARCHAR(64)  NOT NULL DEFAULT '',
  update_time     DATETIME     NOT NULL,
  deleted         BIT(1)       NOT NULL DEFAULT 0,
  UNIQUE KEY uk_tenant_code (tenant_id, location_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库位定义';

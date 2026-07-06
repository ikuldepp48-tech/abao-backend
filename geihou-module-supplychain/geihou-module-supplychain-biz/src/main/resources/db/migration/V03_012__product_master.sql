-- V03_012__product_master.sql
-- G2-02A: Product master table (产品/物料主档)

CREATE TABLE product_master (
  id                BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id         BIGINT       NOT NULL                  COMMENT '租户ID',
  product_code      VARCHAR(64)  NOT NULL                  COMMENT '产品编码',
  product_name      VARCHAR(128) NOT NULL                  COMMENT '产品名称',
  product_type      VARCHAR(32)  NOT NULL                  COMMENT '产品类型(FINISHED/SEMI_FINISHED/RAW_MATERIAL)',
  sku_code          VARCHAR(64)                            COMMENT '关联SKU编码(可空)',
  unit              VARCHAR(16)  NOT NULL                  COMMENT '单位',
  category          VARCHAR(64)                            COMMENT '分类',
  description       VARCHAR(512)                           COMMENT '描述',
  is_active         BIT(1)       NOT NULL DEFAULT 1        COMMENT '是否启用',
  creator           VARCHAR(64)  NOT NULL DEFAULT '',
  create_time       DATETIME     NOT NULL,
  updater           VARCHAR(64)  NOT NULL DEFAULT '',
  update_time       DATETIME     NOT NULL,
  deleted           BIT(1)       NOT NULL DEFAULT 0,
  UNIQUE KEY uk_tenant_product_code (tenant_id, product_code, deleted),
  KEY idx_tenant_sku (tenant_id, sku_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品主档';

-- G2-01B3B: stock mapping coverage audit
-- Records existing SKIP-path observations. This is not a mapping truth table.

CREATE TABLE stock_mapping_coverage_audit (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  coverage_type VARCHAR(32) NOT NULL COMMENT 'LOCATION_MISSING / SKU_CODE_MISSING / STOCK_ITEM_MISSING',
  sku_id BIGINT NULL COMMENT 'finance sku_id',
  sku_code VARCHAR(64) NULL COMMENT 'SKU 编码',
  store_id BIGINT NULL COMMENT '门店ID',
  location_type VARCHAR(32) NULL COMMENT '库位类型',
  source_module VARCHAR(64) NOT NULL COMMENT '来源模块',
  source_record_id BIGINT NOT NULL COMMENT '来源记录ID',
  idempotent_key VARCHAR(128) NOT NULL COMMENT '幂等/关联键',
  mode VARCHAR(20) NOT NULL COMMENT 'AUDIT_ONLY / ENFORCE',
  first_seen_time DATETIME NOT NULL COMMENT '首次发现时间',
  last_seen_time DATETIME NOT NULL COMMENT '最近发现时间',
  seen_count INT NOT NULL DEFAULT 1 COMMENT '发现次数',
  creator VARCHAR(64) NOT NULL DEFAULT '' COMMENT '创建者',
  create_time DATETIME NOT NULL COMMENT '创建时间',
  updater VARCHAR(64) NOT NULL DEFAULT '' COMMENT '更新者',
  update_time DATETIME NOT NULL COMMENT '更新时间',
  deleted BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_coverage_source (
    tenant_id, coverage_type, source_module, source_record_id, idempotent_key, deleted
  ),
  KEY idx_tenant_last_seen (tenant_id, last_seen_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存映射覆盖率审计';

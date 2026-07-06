-- ============================================================
-- TABLE: product_combo
-- 套餐定义。combo_sku_id 关联 product_sku.id(spu.type = COMBO)。
-- Source: PRD-G1-02 节 2.2 DDL / G1-02F 任务包节 6.1
-- Note: combo_price DECIMAL(18,4) + Java BigDecimal,严禁 double/float。
--       status 使用 ENUM_COMBO_STATUS(ACTIVE/PAUSED/DEPRECATED)。
--       无 sort_order 字段(G1-02F 任务包约束:不支持的 combo sort_order 不引入)。
-- ============================================================
CREATE TABLE product_combo (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id           BIGINT       NOT NULL                  COMMENT '租户ID',
  combo_sku_id        BIGINT       NOT NULL                  COMMENT '套餐自身的 SKU ID(spu.type = COMBO)',

  combo_name          VARCHAR(128) NOT NULL                  COMMENT '套餐名',
  combo_price         DECIMAL(18,4) NOT NULL                 COMMENT '套餐总价(BigDecimal)',

  -- 起止时间(限时套餐)
  effective_from      DATETIME                               COMMENT '生效开始',
  effective_until     DATETIME                               COMMENT '生效结束(NULL = 长期)',

  status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / PAUSED / DEPRECATED (ENUM_COMBO_STATUS)',

  -- 审计字段
  creator             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time         DATETIME     NOT NULL                  COMMENT '创建时间',
  updater             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time         DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted             BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',

  UNIQUE KEY uk_combo_sku (combo_sku_id, deleted),
  KEY idx_tenant_status (tenant_id, status, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐定义';

-- ============================================================
-- TABLE: product_addon_option
-- 加料选项。option_sku_id 关联同租户 product_sku.id。
-- Source: PRD-G1-02 节 2.2 DDL / G1-02F 任务包节 6.1
-- Note: extra_price DECIMAL(18,4) + Java BigDecimal,严禁 double/float。
--       status 使用 ENUM_ADDON_OPTION_STATUS(ACTIVE/SOLD_OUT/DISABLED)。
-- ============================================================
CREATE TABLE product_addon_option (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id           BIGINT       NOT NULL                  COMMENT '租户ID',
  addon_group_id      BIGINT       NOT NULL                  COMMENT '所属加料组',

  option_sku_id       BIGINT       NOT NULL                  COMMENT '该选项对应的 SKU ID(用于 BOM 反推 + 计价)',
  option_name         VARCHAR(128) NOT NULL                  COMMENT '选项名(如"加奶油")',
  extra_price         DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '额外加价(BigDecimal)',
  sort_order          INT          NOT NULL DEFAULT 0        COMMENT '排序',
  status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / SOLD_OUT / DISABLED (ENUM_ADDON_OPTION_STATUS)',

  -- 审计字段
  creator             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time         DATETIME     NOT NULL                  COMMENT '创建时间',
  updater             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time         DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted             BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',

  KEY idx_group (addon_group_id, sort_order),
  KEY idx_tenant_sku (tenant_id, option_sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加料选项';

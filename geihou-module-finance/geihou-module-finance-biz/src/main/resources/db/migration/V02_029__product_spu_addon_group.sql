-- ============================================================
-- TABLE: product_spu_addon_group
-- SPU-加料组映射表 (many-to-many mapping)
-- Required by ProductApi.getAddonGroupsBySpu (PRD-G1-02 Section 3.3)
-- product_addon_group has no spu_id field (G1-02F R-1/R-6 gap).
-- ============================================================
CREATE TABLE product_spu_addon_group (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id           BIGINT       NOT NULL                  COMMENT '租户ID',
  spu_id              BIGINT       NOT NULL                  COMMENT 'SPU ID',
  addon_group_id      BIGINT       NOT NULL                  COMMENT '加料组 ID',
  sort_order          INT          NOT NULL DEFAULT 0        COMMENT '排序',
  creator             VARCHAR(64)  NOT NULL DEFAULT '',
  create_time         DATETIME     NOT NULL,
  updater             VARCHAR(64)  NOT NULL DEFAULT '',
  update_time         DATETIME     NOT NULL,
  deleted             BIT(1)       NOT NULL DEFAULT 0,
  UNIQUE KEY uk_tenant_spu_addon (tenant_id, spu_id, addon_group_id, deleted),
  KEY idx_spu (spu_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SPU-加料组映射';

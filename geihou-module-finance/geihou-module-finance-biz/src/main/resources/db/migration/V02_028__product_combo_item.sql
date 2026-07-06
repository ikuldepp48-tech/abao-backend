-- ============================================================
-- TABLE: product_combo_item
-- 套餐内组成。item_sku_id 关联同租户 ACTIVE/SOLD_OUT 状态的 product_sku.id。
-- Source: PRD-G1-02 节 2.2 DDL / G1-02F 任务包节 6.1
-- Note: 不含 sort_order 字段(G1-02F 任务包约束:套餐内排序按 id 即插入顺序)。
--       AC-11 增强约束:同套餐内 (combo_id, item_sku_id, deleted) 唯一。
-- ============================================================
CREATE TABLE product_combo_item (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id           BIGINT       NOT NULL                  COMMENT '租户ID',
  combo_id            BIGINT       NOT NULL                  COMMENT '所属套餐 ID',

  item_sku_id         BIGINT       NOT NULL                  COMMENT '套餐内 SKU ID',
  quantity            INT          NOT NULL                  COMMENT '该 SKU 在套餐中的数量',
  is_optional         BIT(1)       NOT NULL DEFAULT 0        COMMENT '是否可选(2选1 / N选M)',
  alternative_group   INT                                    COMMENT '可选组编号(同组内 N 选 1)',

  -- 审计字段
  creator             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time         DATETIME     NOT NULL                  COMMENT '创建时间',
  updater             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time         DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted             BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',

  UNIQUE KEY uk_combo_item (combo_id, item_sku_id, deleted),
  KEY idx_combo (combo_id),
  KEY idx_item_sku (item_sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='套餐组成';

-- ============================================================
-- TABLE: order_items
-- 订单明细(每一行对应一个 SKU)
-- 商品快照: 下单时刻冻结,即使商品后续改名也不变
-- 不可变: sku_id/sku_code/sku_name/unit_price/quantity/item_total 创建后不允许 UPDATE
-- 金额精度: DECIMAL(18,4) + Java BigDecimal (H5 第 9 项)
-- ============================================================
CREATE TABLE order_items (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL                  COMMENT '租户ID',
  order_id        BIGINT       NOT NULL                  COMMENT '订单ID',

  -- 商品快照(下单时刻冻结,即使商品后续改名也不变)
  sku_id          BIGINT       NOT NULL                  COMMENT 'SKU ID',
  sku_code        VARCHAR(64)  NOT NULL                  COMMENT 'SKU 编码',
  sku_name        VARCHAR(128) NOT NULL                  COMMENT 'SKU 名称(下单时快照)',
  spu_id          BIGINT       NOT NULL                  COMMENT 'SPU ID',
  spu_name        VARCHAR(128) NOT NULL                  COMMENT 'SPU 名称',
  category_id     BIGINT       NOT NULL                  COMMENT '分类ID',

  -- 价格 / 数量(全部 BigDecimal)
  unit_price      DECIMAL(18,4) NOT NULL                 COMMENT '单价(下单时快照)',
  quantity        DECIMAL(18,4) NOT NULL                 COMMENT '数量(支持小数,如 0.5 份)',
  unit            VARCHAR(16)  NOT NULL DEFAULT '份'     COMMENT '单位',

  -- 优惠 / 实际收款
  item_discount   DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '明细优惠金额',
  item_total      DECIMAL(18,4) NOT NULL                 COMMENT '明细应收 = unit_price × quantity',
  item_paid       DECIMAL(18,4) NOT NULL                 COMMENT '明细实收 = item_total - item_discount',

  -- 加料 / 套餐(用 JSON 存复杂结构,允许半结构化)
  modifiers       TEXT                                   COMMENT 'JSON: 加料 / 套餐组件 / 备注(必须是合法 JSON — Track A1 教训 4)',

  -- 退款情况
  refunded_quantity DECIMAL(18,4) NOT NULL DEFAULT 0     COMMENT '已退数量',
  refunded_amount DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '已退金额',

  -- 出餐状态(行级,KDS 联动)
  item_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / KITCHEN / READY / SERVED',

  -- 审计字段
  creator         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',
  updater         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time     DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted         BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',

  KEY idx_tenant_order (tenant_id, order_id),
  KEY idx_tenant_sku_date (tenant_id, sku_id, create_time),
  KEY idx_tenant_category (tenant_id, category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细';

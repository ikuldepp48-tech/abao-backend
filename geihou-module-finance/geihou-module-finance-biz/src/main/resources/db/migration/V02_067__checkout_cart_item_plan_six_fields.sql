-- ============================================================
-- ALTER TABLE: checkout_cart_item_plan
-- OWNER: G0-04H185 FIN-CONSISTENCY (checkout classification plan slice 2C-2B)
-- 子系统: 经营 ① 钱回来(7 大经营动作)
--
-- 切片 2C-2B: 新增六字段(sku_code/stock_strategy/bom_product_id/
-- stock_item_id/location_id/classification_reason)到 checkout_cart_item_plan。
-- 六列全部允许 NULL,不加默认值,不做历史数据回填。
-- 条件约束由 CheckoutCartItemPlanCreate / Store fail-closed 校验。
--
-- 冻结契约(00-全局接口契约汇总表 §5.8.2):
-- - sku_code VARCHAR(64): 源 skuCode blank -> null 落库(与 classification_reason 无绑定)
-- - stock_strategy VARCHAR(20): 源 stockStrategy 非法 -> null 落库(禁止 VARCHAR(16))
-- - bom_product_id BIGINT: classification=BOM 时正数;其他 null
-- - stock_item_id BIGINT: NON_BOM+TRACK_STOCK 时正数;其他 null
-- - location_id BIGINT: BOM 或 NON_BOM+TRACK_STOCK 时正数;其他 null
-- - classification_reason VARCHAR(32): classification=UNMAPPED 时非空(CheckoutClassificationReason.name());其他 null
--
-- 不变式: write-once;plan 永久保留;六字段按源值有效性独立归一化
-- ============================================================
ALTER TABLE checkout_cart_item_plan
  ADD COLUMN sku_code             VARCHAR(64)  NULL  COMMENT 'SKU编码(归一化,null=源blank,与classification_reason无绑定)',
  ADD COLUMN stock_strategy       VARCHAR(20)  NULL  COMMENT '库存策略 TRACK_STOCK/UNLIMITED(归一化,null=源非法,禁止VARCHAR(16))',
  ADD COLUMN bom_product_id       BIGINT       NULL  COMMENT 'BOM product_id(classification=BOM时正数,其他null)',
  ADD COLUMN stock_item_id        BIGINT       NULL  COMMENT 'NON_BOM+TRACK_STOCK stock_item_id(其他null)',
  ADD COLUMN location_id          BIGINT       NULL  COMMENT 'BOM或NON_BOM+TRACK_STOCK location_id(其他null)',
  ADD COLUMN classification_reason VARCHAR(32) NULL  COMMENT 'UNMAPPED子原因(CheckoutClassificationReason.name(),其他null)';

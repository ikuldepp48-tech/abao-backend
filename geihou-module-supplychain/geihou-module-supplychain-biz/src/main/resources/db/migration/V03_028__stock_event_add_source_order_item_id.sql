-- G2-02W: add nullable source_order_item_id for line-level sales/BOM traceability.
-- Nullable keeps legacy/admin/non-order events compatible while new checkout BOM events can write order_items.id.

ALTER TABLE stock_event
  ADD COLUMN source_order_item_id BIGINT NULL COMMENT '来源订单行ID(销售/BOM反推行级追溯,可空兼容历史数据)';

CREATE INDEX idx_stock_event_tenant_source_line
  ON stock_event (tenant_id, source_module, source_record_id, source_order_item_id, event_type);

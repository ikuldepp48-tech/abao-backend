-- V03_009__stock_item_add_is_active.sql
-- G2-01B3A: Add is_active column to stock_item for mapping governance (enable/disable)

ALTER TABLE stock_item
  ADD COLUMN is_active BIT(1) NOT NULL DEFAULT 1 COMMENT '是否启用' AFTER is_finished;

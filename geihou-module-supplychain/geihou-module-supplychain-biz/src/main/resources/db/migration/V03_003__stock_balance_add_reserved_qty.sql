-- V03_003__stock_balance_add_reserved_qty.sql
-- G2-01B1: Add reserved_qty column to stock_balance
-- Codex 裁决 #4: reserved_qty 在 stock_balance 上维护

ALTER TABLE stock_balance
  ADD COLUMN reserved_qty DECIMAL(18,4) NOT NULL DEFAULT 0 COMMENT '预扣数量' AFTER total_qty;

-- available_qty 语义变更：available_qty = total_qty - reserved_qty
-- 迁移时已有数据的 reserved_qty = 0，available_qty 不变
-- 后续由 StockReserveService 维护三值一致性

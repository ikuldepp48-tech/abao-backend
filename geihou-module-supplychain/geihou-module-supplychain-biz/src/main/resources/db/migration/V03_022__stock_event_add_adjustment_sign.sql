-- G2-02J-1: 为 stock_event 新增 adjustment_sign 字段
-- 持久化 COUNT_ADJUST 事件的盘盈(+1)/盘亏(-1)方向,消除对账推断依赖
-- 字段可空(不影响已有非盘点事件及历史 NULL 行)

ALTER TABLE stock_event
  ADD COLUMN adjustment_sign TINYINT NULL COMMENT '调整方向(+1盘盈/-1盘亏,仅COUNT_ADJUST)';

-- 历史回填策略见 §6
-- 回填存量 COUNT_ADJUST 事件的 adjustment_sign
-- 推断规则：diff = balance_after − prevBalance
--   prevBalance = 同 (tenant_id, stock_item_id, location_id) 维度中
--   event_time 更早（或 event_time 相同但 id 更小）的事件的 balance_after
--   若为首条事件，prevBalance = 0
--   diff > 0 → +1 (盘盈)
--   diff < 0 → −1 (盘亏)
--   diff = 0 → 留 NULL (ambiguous, 不回填, 对账时走 fallback)

UPDATE stock_event se
JOIN (
    SELECT
        se1.id AS event_id,
        CASE
            WHEN se1.balance_after IS NULL THEN NULL
            WHEN COALESCE(
                (SELECT se2.balance_after
                 FROM stock_event se2
                 WHERE se2.tenant_id = se1.tenant_id
                   AND se2.stock_item_id = se1.stock_item_id
                   AND se2.location_id = se1.location_id
                   AND (se2.event_time < se1.event_time
                        OR (se2.event_time = se1.event_time AND se2.id < se1.id))
                 ORDER BY se2.event_time DESC, se2.id DESC
                 LIMIT 1),
                0
            ) < se1.balance_after THEN 1
            WHEN COALESCE(
                (SELECT se2.balance_after
                 FROM stock_event se2
                 WHERE se2.tenant_id = se1.tenant_id
                   AND se2.stock_item_id = se1.stock_item_id
                   AND se2.location_id = se1.location_id
                   AND (se2.event_time < se1.event_time
                        OR (se2.event_time = se1.event_time AND se2.id < se1.id))
                 ORDER BY se2.event_time DESC, se2.id DESC
                 LIMIT 1),
                0
            ) > se1.balance_after THEN -1
            ELSE NULL  -- ambiguous, leave NULL
        END AS inferred_sign
    FROM stock_event se1
    WHERE se1.event_type = 'COUNT_ADJUST'
      AND se1.direction = 'INTERNAL'
) inference ON se.id = inference.event_id
SET se.adjustment_sign = inference.inferred_sign
WHERE se.adjustment_sign IS NULL
  AND se.event_type = 'COUNT_ADJUST'
  AND inference.inferred_sign IS NOT NULL;

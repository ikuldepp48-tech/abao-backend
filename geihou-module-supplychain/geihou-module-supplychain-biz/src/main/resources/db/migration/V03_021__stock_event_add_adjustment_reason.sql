-- G2-02I-2: 为 stock_event 新增 adjustment_reason 字段
-- PRD §5: ADJUSTMENT/COUNT_ADJUST 事件需携带 adjustment_reason VARCHAR(512), >=30 字符
-- 字段可空(不影响已有非盘点事件), 由应用层校验必填+长度

ALTER TABLE stock_event
  ADD COLUMN adjustment_reason VARCHAR(512) NULL COMMENT '调整原因(ADJUSTMENT/COUNT_ADJUST事件必填,>=30字符)';

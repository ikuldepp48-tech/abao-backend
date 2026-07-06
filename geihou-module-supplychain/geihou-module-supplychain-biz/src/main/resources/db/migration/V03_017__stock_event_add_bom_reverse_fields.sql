-- G2-02D-pre: 为 stock_event 新增 BOM 反推扣料前置字段
-- 三个字段均可空，不影响现有事件录入

ALTER TABLE stock_event
  ADD COLUMN parent_event_id BIGINT NULL COMMENT '父事件ID(BOM反推扣料场景,子事件指向父事件)',
  ADD COLUMN recipe_id BIGINT NULL COMMENT '配方ID快照(反推扣料时记录使用的配方)',
  ADD COLUMN recipe_version INT NULL COMMENT '配方版本号快照(反推扣料时记录使用的配方版本)';

-- 加速按父事件查询子事件（租户隔离 + 父事件索引）
CREATE INDEX idx_stock_event_tenant_parent
  ON stock_event (tenant_id, parent_event_id);

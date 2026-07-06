-- V03_020__stock_count_record.sql
-- G2-02I-2: stock_count_record 盘点明细(不可篡改, INSERT-only)
-- Source: PRD-组2-01 §2.2
-- PRD 节 5.4: count_record INSERT-only, 不提供 UPDATE/DELETE
-- actual_qty / diff_qty NOT NULL（对齐 PRD）；无 sku_code / location_id / creator
-- adjustment_event_id 不回填（保持 INSERT-only），追溯通过 stock_event.source_record_id 反查

CREATE TABLE stock_count_record (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL                  COMMENT '租户ID',
  session_id      BIGINT       NOT NULL                  COMMENT '盘点会话ID',

  -- 库存项
  stock_item_id   BIGINT       NOT NULL                  COMMENT '品项ID',

  -- 数量 (全部 NOT NULL，INSERT 时必须填齐)
  system_qty      DECIMAL(18,4) NOT NULL                 COMMENT '系统数量(盘前快照)',
  actual_qty      DECIMAL(18,4) NOT NULL                 COMMENT '实盘数量',
  diff_qty        DECIMAL(18,4) NOT NULL                 COMMENT '差异(实盘 - 系统)',

  -- 差异原因 + 证据
  diff_reason     VARCHAR(512)                           COMMENT '差异原因(diff_qty!=0时必填,>=30字)',
  evidence_url    VARCHAR(512)                           COMMENT '证据照片URL(差异超阈值时必传)',

  -- 关联库存事件 (PRD 字段保留，但不回填——INSERT-only 约束下无法 UPDATE)
  adjustment_event_id BIGINT                             COMMENT '生成的COUNT_ADJUST事件ID(保留字段,本切片不回填)',

  -- 仅 INSERT (无 updater/update_time/creator, 对齐 PRD)
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',

  KEY idx_session (session_id),
  KEY idx_tenant_item (tenant_id, stock_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点明细(不可篡改)';

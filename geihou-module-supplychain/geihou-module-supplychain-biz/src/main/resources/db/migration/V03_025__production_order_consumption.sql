-- V03_025__production_order_consumption.sql
-- G2-02N: production_order_consumption 工单原料消耗记录(领料登记)
-- Source: PRD-组2-03 §2.2, TASK-G2-02N-PRODUCTION-SCAN-PICK-OUTPUT-PACKAGE.md §4
-- 表名沿用 PRD 命名: production_order_consumption
-- 新增 pick_seq 列(幂等序号), 审计字段(creator/create_time/updater/update_time/deleted)
-- service 层 INSERT-only 语义, 不提供 update/delete mapper 方法

CREATE TABLE production_order_consumption (
  id                      BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id               BIGINT       NOT NULL                  COMMENT '租户ID',
  production_order_id     BIGINT       NOT NULL                  COMMENT '工单 ID',
  input_sku_id            BIGINT       NOT NULL                  COMMENT '消耗的 SKU(component product ID)',
  pick_seq                INT          NOT NULL                  COMMENT '领料序号(同一组件第 N 次领料)',
  planned_qty             DECIMAL(18,4) NOT NULL                 COMMENT '计划消耗(BOM 展开后该组件计划数量)',
  actual_qty              DECIMAL(18,4) NOT NULL                 COMMENT '实际消耗(本次领料数量)',
  diff_qty                DECIMAL(18,4) NOT NULL                 COMMENT '差异(实际 - 计划, 本次视角)',
  diff_reason             VARCHAR(255)                           COMMENT '差异原因',
  stock_event_id          BIGINT                                 COMMENT '生成的 PRODUCTION_OUT 事件 ID',
  unit_cost               DECIMAL(18,4)                          COMMENT '消耗时单位成本(加权, G2-03/组3-03 填充)',
  total_cost              DECIMAL(18,4)                          COMMENT '总成本(G2-03/组3-03 填充)',
  -- 审计字段(INSERT-only 语义: service 层不提供 update/delete, 但补全审计字段与其他业务表一致)
  creator                 VARCHAR(64)  NOT NULL DEFAULT '',
  create_time             DATETIME     NOT NULL,
  updater                 VARCHAR(64)  NOT NULL DEFAULT '',
  update_time             DATETIME     NOT NULL,
  deleted                 BIT(1)       NOT NULL DEFAULT 0,
  -- 唯一索引: 防止同一组件同一序号重复插入
  UNIQUE KEY uk_tenant_order_sku_seq (tenant_id, production_order_id, input_sku_id, pick_seq, deleted),
  KEY idx_order (production_order_id),
  KEY idx_tenant_sku (tenant_id, input_sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单原料消耗(领料记录, INSERT-only)';

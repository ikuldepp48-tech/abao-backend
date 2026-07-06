-- V03_026__production_order_output.sql
-- G2-02N: production_order_output 工单产出记录(产出登记)
-- Source: PRD-组2-03 §2.2, TASK-G2-02N-PRODUCTION-SCAN-PICK-OUTPUT-PACKAGE.md §4
-- 表名沿用 PRD 命名: production_order_output
-- 新增 output_seq 列(幂等序号), 审计字段(creator/create_time/updater/update_time/deleted)
-- service 层 INSERT-only 语义, 不提供 update/delete mapper 方法

CREATE TABLE production_order_output (
  id                      BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id               BIGINT       NOT NULL                  COMMENT '租户ID',
  production_order_id     BIGINT       NOT NULL                  COMMENT '工单 ID',
  output_sku_id           BIGINT       NOT NULL                  COMMENT '产出 SKU(半成品 product ID)',
  output_seq              INT          NOT NULL                  COMMENT '产出序号(第 N 次产出登记)',
  actual_output_qty       DECIMAL(18,4) NOT NULL                 COMMENT '实际产出数量',
  output_quality_grade    VARCHAR(20)                            COMMENT 'A / B / C 等级(G2-03 填充)',
  stock_event_id          BIGINT                                 COMMENT '生成的 PRODUCTION_IN 事件 ID',
  unit_cost               DECIMAL(18,4)                          COMMENT '产出单位成本(G2-03/组3-03 填充)',
  total_cost              DECIMAL(18,4)                          COMMENT '总成本(G2-03/组3-03 填充)',
  batch_no                VARCHAR(64)                            COMMENT '批次号(食安追溯)',
  produced_time           DATETIME     NOT NULL                  COMMENT '生产时间',
  expire_time             DATETIME                               COMMENT '过期时间(基于保质期, 可选)',
  -- 审计字段
  creator                 VARCHAR(64)  NOT NULL DEFAULT '',
  create_time             DATETIME     NOT NULL,
  updater                 VARCHAR(64)  NOT NULL DEFAULT '',
  update_time             DATETIME     NOT NULL,
  deleted                 BIT(1)       NOT NULL DEFAULT 0,
  -- 唯一索引: 防止同一产出序号重复插入
  UNIQUE KEY uk_tenant_order_seq (tenant_id, production_order_id, output_seq, deleted),
  KEY idx_order (production_order_id),
  KEY idx_tenant_sku (tenant_id, output_sku_id, produced_time),
  KEY idx_batch (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单产出记录(产出登记, INSERT-only)';

-- G0-04H185-FIN-CONSISTENCY: Expand stock_event.client_request_id to VARCHAR(191)
-- C4 requirement: finance->supplychain command idempotent key may be composite.
-- 191 依据: 原始命令键 128 + "::restore::" 11 字符 + BIGINT 最大 20 位 = 159,
-- 再保留安全余量至 191。
-- Original: VARCHAR(64) nullable (V03_001)
-- New: VARCHAR(191), nullable 显式声明

ALTER TABLE stock_event
  MODIFY COLUMN client_request_id VARCHAR(191) NULL
  COMMENT '客户端请求 ID(幂等)';

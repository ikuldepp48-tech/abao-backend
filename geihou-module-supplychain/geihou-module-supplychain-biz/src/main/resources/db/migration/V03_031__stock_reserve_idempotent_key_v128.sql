-- G0-04H185-FIN-CONSISTENCY: Expand stock_reserve.idempotent_key to VARCHAR(128)
-- C4 requirement: align with idempotent-key header maxLength 128
-- Original: VARCHAR(64) NOT NULL (V03_006)
-- New: VARCHAR(128), NOT NULL 显式声明

ALTER TABLE stock_reserve
  MODIFY COLUMN idempotent_key VARCHAR(128) NOT NULL
  COMMENT '幂等键(原始业务命令键,最大128字符)';

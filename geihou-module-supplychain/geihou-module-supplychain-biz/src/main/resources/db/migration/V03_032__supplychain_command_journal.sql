-- G0-04H185-FIN-CONSISTENCY: supplychain_command_journal table
-- C4: Command journal for finance->supplychain 6 write endpoints
-- INSERT-only, no status/INFLIGHT field, no logic delete, no business table FK

CREATE TABLE supplychain_command_journal (
  id                    BIGINT UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id             BIGINT           NOT NULL                  COMMENT '租户ID',
  operation             VARCHAR(32)      NOT NULL                  COMMENT '操作类型(6值:RESERVE/RELEASE/COMMIT/SALES_OUT_BOM_REVERSE/SALES_REVERSE_RESTORE/OBSERVE_MISSING_MAPPING)',
  business_command_id   VARCHAR(128)     NOT NULL                  COMMENT '业务命令 ID(幂等键)',
  request_body_sha256   CHAR(64)         NOT NULL                  COMMENT '请求体 SHA256 哈希',
  result_schema_version INT              NOT NULL DEFAULT 1        COMMENT '结果快照 schema 版本',
  result_snapshot       JSON             NOT NULL                  COMMENT '结果快照(JSON)',
  executed_at           DATETIME(3)      NOT NULL                  COMMENT '执行时间(毫秒精度)',
  create_time           DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

  UNIQUE KEY uk_tenant_op_cmd (tenant_id, operation, business_command_id),
  KEY idx_tenant_executed (tenant_id, executed_at),
  KEY idx_tenant_op_executed (tenant_id, operation, executed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='supplychain 命令日志(finance->supplychain 6 写端点,C4 一致性基础设施)';

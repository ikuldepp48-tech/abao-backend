-- ============================================================
-- TABLE: finance_stock_command
-- OWNER: G0-04H185 FIN-CONSISTENCY (durable command store slice 1)
-- 子系统: 经营 ① 钱进来(7 大经营动作)
-- 多租户: 必含 tenant_id
-- 软删除: 无 (无 deleted 列, 无 @TableLogic)
-- 外键: 无
--
-- 本表是 finance->supplychain 写命令的耐久执行 saga 状态表。
-- 切片 1 只建立存储与 CAS 状态转换,默认关闭,不接业务。
--
-- 冻结契约:
-- - 不可变身份: (tenant_id, operation, business_command_id)
-- - 不可变逻辑身份: (tenant_id, saga_type, saga_id, step_key, operation)
-- - request_body 是 UTF-8 原始 JSON 字节, 禁止存 DTO canonical hash
-- - request_body_sha256 对 request_body 字节计算, 64 位小写 hex
-- - transport_mode=LOCAL_API_V1 + c0_journal_available=true 被启动校验拒绝
--
-- 状态机 (FinanceStockCommandStatus):
-- PENDING -> IN_FLIGHT -> {SUCCEEDED, RETRY_WAIT, UNKNOWN, NO_EFFECT, STUCK}
-- expired IN_FLIGHT -> UNKNOWN (绝不回 PENDING)
-- unclaimed PENDING/RETRY_WAIT + abort -> CANCELLED
-- IN_FLIGHT/UNKNOWN + abort -> 状态不变, abort_requested=true
--
-- 参考:
-- - G0-04H185-FIN-CONSISTENCY-SAGA-PROTOCOL-FREEZE.md (协议冻结稿)
-- - TASK-G0-04H185-FIN-CONSISTENCY-DURABLE-COMMAND-STORE.md (切片 1 任务)
-- ============================================================
CREATE TABLE finance_stock_command (
  id                        BIGINT UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id                 BIGINT           NOT NULL                  COMMENT '租户ID',

  -- 本地 saga 身份
  saga_type                 VARCHAR(16)      NOT NULL                  COMMENT 'FinanceStockSagaType: CHECKOUT / REFUND',
  saga_id                   BIGINT           NOT NULL                  COMMENT 'saga 实例 ID (checkout_session_id / refund_id)',
  step_key                  VARCHAR(128)     NOT NULL                  COMMENT 'saga 内步骤键 (如 cart_item_id)',
  parent_command_id         BIGINT                                     COMMENT '父命令 ID (如 RESERVE 成功后建 RELEASE)',

  -- 远端命令身份
  operation                 VARCHAR(32)      NOT NULL                  COMMENT 'SupplychainCommandOperationEnum code',
  business_command_id       VARCHAR(128)     NOT NULL                  COMMENT '业务命令 ID (幂等键)',

  -- 发布栅栏
  transport_mode            VARCHAR(16)      NOT NULL                  COMMENT 'FinanceStockTransportMode: LOCAL_API_V1 / HMAC_RPC_V1',
  c0_journal_available      BIT(1)           NOT NULL                  COMMENT '是否可调 command-status (T_c0 栅栏)',
  request_schema_version    INT              NOT NULL DEFAULT 1        COMMENT '请求 body schema 版本',
  request_body              MEDIUMBLOB       NOT NULL                  COMMENT 'UTF-8 原始 JSON 字节',
  request_body_sha256       CHAR(64)         NOT NULL                  COMMENT 'request_body 的 SHA-256 (64 位小写 hex)',

  -- 结果快照
  result_schema_version     INT                                        COMMENT '结果 body schema 版本',
  result_body               MEDIUMBLOB                                 COMMENT '结果 body 字节',

  -- 状态机
  status                    VARCHAR(20)      NOT NULL                  COMMENT 'FinanceStockCommandStatus',
  abort_requested           BIT(1)           NOT NULL DEFAULT 0        COMMENT 'saga 是否请求中止',
  dispatch_attempts         INT              NOT NULL DEFAULT 0        COMMENT '派发尝试次数',
  resolution_attempts       INT              NOT NULL DEFAULT 0        COMMENT 'UNKNOWN 解算尝试次数',
  max_dispatch_attempts     INT              NOT NULL                  COMMENT '派发上限',
  max_resolution_attempts   INT              NOT NULL                  COMMENT '解算上限',

  -- 调度与租约
  next_attempt_at           DATETIME(3)                                COMMENT '下次可尝试时间',
  claim_token               VARCHAR(64)                                COMMENT '当前持有者令牌 (UUID)',
  lease_until               DATETIME(3)                                COMMENT '租约到期时间',

  -- 错误信息
  last_error_code           INT                                        COMMENT '最后错误码',
  last_error_class          VARCHAR(128)                               COMMENT '最后错误类名',
  last_error_message        VARCHAR(512)                               COMMENT '最后错误消息',

  -- 时间戳
  remote_executed_at        DATETIME(3)                                COMMENT '远端执行时间',
  resolved_at               DATETIME(3)                                COMMENT '终态化时间',

  -- 审计 (无 creator/updater/deleted)
  create_time               DATETIME(3)      NOT NULL                  COMMENT '创建时间',
  update_time               DATETIME(3)      NOT NULL                  COMMENT '更新时间',

  -- 索引
  UNIQUE KEY uk_fsc_remote_identity
    (tenant_id, operation, business_command_id),
  UNIQUE KEY uk_fsc_local_step
    (tenant_id, saga_type, saga_id, step_key, operation),
  KEY idx_fsc_dispatch_scan
    (tenant_id, status, next_attempt_at),
  KEY idx_fsc_lease_scan
    (tenant_id, status, lease_until),
  KEY idx_fsc_saga
    (tenant_id, saga_type, saga_id),
  KEY idx_fsc_parent
    (tenant_id, parent_command_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='finance 库存命令耐久执行表';

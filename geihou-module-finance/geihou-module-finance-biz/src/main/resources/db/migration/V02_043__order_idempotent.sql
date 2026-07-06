-- ============================================================
-- TABLE: order_idempotent
-- 幂等防重表(防止 Cart 6 根因之 "fire-and-forget" 导致的重复下单)
-- DB 唯一键实现幂等 (Redis 层待基础设施就绪后补充)
-- 无 deleted 字段 (过期清理,非软删除)
-- ============================================================
CREATE TABLE order_idempotent (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL                  COMMENT '租户ID',

  -- 幂等键(由客户端生成或网关生成)
  idempotent_key  VARCHAR(64)  NOT NULL                  COMMENT '幂等键(UUID,客户端发起请求时携带)',

  -- 关联订单
  order_id        BIGINT                                 COMMENT '最终生成的订单 ID',

  -- 状态
  status          VARCHAR(20)  NOT NULL                  COMMENT 'PROCESSING / SUCCESS / FAILED',

  -- 过期(防止表无限增长)
  expire_time     DATETIME     NOT NULL                  COMMENT '过期时间(默认 24 小时)',

  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',

  UNIQUE KEY uk_tenant_idempotent_key (tenant_id, idempotent_key),
  KEY idx_expire_time (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单幂等防重表';

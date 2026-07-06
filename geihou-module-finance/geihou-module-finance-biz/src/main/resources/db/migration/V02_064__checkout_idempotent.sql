-- ============================================================
-- TABLE: checkout_idempotent
-- OWNER: 组 1-04 购物车结算 (G1-04B checkout session slice)
-- 幂等防重表: 防止重复发起结算
-- DB 唯一键实现幂等 (Redis 层待基础设施就绪后补充)
-- 无 deleted 字段 (过期清理,非软删除)
-- ============================================================
CREATE TABLE checkout_idempotent (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL                  COMMENT '租户ID',

  -- 幂等键(由客户端生成)
  idempotent_key  VARCHAR(64)  NOT NULL                  COMMENT '幂等键(客户端发起请求时携带)',

  -- 关联结算会话
  session_id      BIGINT                                 COMMENT '最终生成的结算会话 ID',
  session_token   VARCHAR(64)                            COMMENT '结算会话 token',

  -- 状态
  status          VARCHAR(20)  NOT NULL                  COMMENT 'PROCESSING / SUCCESS / FAILED',

  -- 过期(防止表无限增长)
  expire_time     DATETIME     NOT NULL                  COMMENT '过期时间(默认 24 小时)',

  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',

  UNIQUE KEY uk_tenant_idempotent_key (tenant_id, idempotent_key),
  KEY idx_expire_time (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算幂等防重表';

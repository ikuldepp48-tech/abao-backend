-- V03_019__stock_count_session.sql
-- G2-02I-2: stock_count_session 盘点会话
-- Source: PRD-组2-01 §2.2, §4.2 盘点状态机
-- 状态机: PLANNING → IN_PROGRESS → DIFF_REVIEW → ADJUSTED
--         (APPROVED 为概念中间态，不持久化；本切片无 CANCELLED)
-- 注意: DDL 中 status 的 DEFAULT 'PLANNING' 仅为兜底，应用层(Service)必须显式设置 status 值。

CREATE TABLE stock_count_session (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL                  COMMENT '租户ID',

  -- 单号
  session_code    VARCHAR(64)  NOT NULL                  COMMENT '盘点单号(租户内唯一)',

  -- 盘点范围
  location_id     BIGINT       NOT NULL                  COMMENT '盘点库位ID',
  count_type      VARCHAR(20)  NOT NULL                  COMMENT 'FULL(全盘) / CYCLE(循环盘) / SPOT(抽盘)',

  -- 计划 / 时间
  scheduled_time  DATETIME                               COMMENT '计划时间',
  start_time      DATETIME                               COMMENT '开始盘点时间',
  end_time        DATETIME                               COMMENT '盘点结束时间',

  -- 状态机
  status          VARCHAR(20)  NOT NULL DEFAULT 'PLANNING' COMMENT 'PLANNING / IN_PROGRESS / DIFF_REVIEW / ADJUSTED',

  -- 汇总
  total_items     INT                                    COMMENT '盘点品项数',
  diff_items      INT                                    COMMENT '差异品项数',
  total_diff_value DECIMAL(18,4)                         COMMENT '总差异金额(绝对值之和)',

  -- 审批
  operator_user_id BIGINT                                COMMENT '盘点员',
  approver_user_id BIGINT                                COMMENT '审批人',
  approve_time    DATETIME                               COMMENT '审批时间',

  -- 审计
  creator         VARCHAR(64)  NOT NULL DEFAULT '',
  create_time     DATETIME     NOT NULL,
  updater         VARCHAR(64)  NOT NULL DEFAULT '',
  update_time     DATETIME     NOT NULL,
  deleted         BIT(1)       NOT NULL DEFAULT 0,

  UNIQUE KEY uk_tenant_session (tenant_id, session_code, deleted),
  KEY idx_tenant_status (tenant_id, status),
  KEY idx_tenant_location (tenant_id, location_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点会话';

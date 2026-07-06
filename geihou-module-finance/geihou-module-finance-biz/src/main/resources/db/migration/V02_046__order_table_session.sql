-- ============================================================
-- TABLE: order_table_session
-- OWNER: 组 1 - 订单管理 (G1-01D table session slice)
-- 子系统: 经营 ① 钱进来(7 大经营动作) — 堂食场景
-- 多租户: 必含 tenant_id + 业务日期联合索引(支撑跨日切分)
-- 不可篡改: session_no 创建后不允许 UPDATE
-- 金额精度: DECIMAL(18,4) + Java BigDecimal (H5 第 9 项)
-- 枚举: ENUM_TABLE_SESSION_STATUS (SSOT: 02-全局枚举表-V2.md)
-- ============================================================
CREATE TABLE order_table_session (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL                  COMMENT '租户ID(不变性 12)',

  -- 门店 / 桌台
  shop_id         BIGINT       NOT NULL                  COMMENT '门店ID',
  table_id        BIGINT       NOT NULL                  COMMENT '桌台ID',
  table_no        VARCHAR(32)  NOT NULL                  COMMENT '桌台号',

  -- 会话信息
  session_no      VARCHAR(32)  NOT NULL                  COMMENT '会话号(租户内唯一)',
  customer_count  INT                                    COMMENT '就餐人数',

  -- 状态(ENUM_TABLE_SESSION_STATUS SSOT)
  status          VARCHAR(20)  NOT NULL                  COMMENT 'ENUM_TABLE_SESSION_STATUS: OPEN / ORDERING / SERVING / SETTLING / CLOSED',

  -- 时间
  open_time       DATETIME     NOT NULL                  COMMENT '开台时间',
  settle_time     DATETIME                               COMMENT '结账时间',
  close_time      DATETIME                               COMMENT '清台时间',

  -- 业务日期(跨日切分,与订单的 business_date 算法一致)
  business_date   DATE         NOT NULL                  COMMENT '业务日期(按租户业务日切分小时计算)',

  -- 关联数据(结账时从订单汇总)
  total_amount    DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '会话总金额(汇总订单)',
  paid_amount     DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '会话总实付',
  order_count     INT          NOT NULL DEFAULT 0        COMMENT '订单数(支持加菜多次下单)',

  -- 审计字段
  creator         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间',
  updater         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time     DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted         BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',

  -- 索引
  UNIQUE KEY uk_tenant_session_no (tenant_id, session_no, deleted),
  KEY idx_tenant_table_status (tenant_id, table_id, status),
  KEY idx_tenant_shop_date (tenant_id, shop_id, business_date),
  KEY idx_tenant_business_date (tenant_id, business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='桌台会话(堂食)';

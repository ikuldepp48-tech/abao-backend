-- ============================================================
-- TABLE: cart
-- OWNER: 组 1-04 购物车结算 (G1-04A first code slice)
-- 子系统: 经营 ① 钱进来(7 大经营动作)
-- 多租户: 必含 tenant_id + 业务日期联合索引
-- 金额精度: DECIMAL(18,4) + Java BigDecimal (H5 第 9 项)
-- 乐观锁: version 字段防并发修改
-- 软删除: deleted BIT(1)
-- Cart 6 根因: no debug/mock fields (root cause 1)
-- ============================================================
CREATE TABLE cart (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id           BIGINT       NOT NULL                  COMMENT '租户ID',

  -- 顾客与门店
  customer_user_id    BIGINT       NOT NULL                  COMMENT '顾客 user_id (JWT claims)',
  shop_id             BIGINT       NOT NULL                  COMMENT '门店 ID',
  table_id            BIGINT                                 COMMENT '桌台 ID (堂食才有)',

  -- 渠道 — ENUM_ORDER_CHANNEL SSOT 9 值 (CG-6 裁决)
  channel             VARCHAR(32)  NOT NULL                  COMMENT 'ENUM_ORDER_CHANNEL(SSOT): DINE_IN / SELF_PICKUP / MEITUAN_TAKEOUT / ELEME_TAKEOUT / DOUYIN_GROUP / MEITUAN_GROUP / WX_PRIVATE / OWN_TAKEOUT / OTHER',

  -- 状态 — ENUM_CART_STATUS 4 值
  status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ENUM_CART_STATUS: ACTIVE / CHECKOUT / CONVERTED / ABANDONED',

  -- 汇总字段
  item_count          INT          NOT NULL DEFAULT 0        COMMENT '购物车项数量',
  total_quantity      INT          NOT NULL DEFAULT 0        COMMENT '总数量',

  -- 金额 (全部 DECIMAL(18,4) + Java BigDecimal)
  subtotal_amount     DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '小计金额',
  discount_amount     DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '优惠总额',
  total_amount        DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '总金额',

  -- 业务日期
  business_date       DATE                                   COMMENT '业务日期(跨日切分)',

  -- 代客下单
  is_staff_assisted   BIT(1)       NOT NULL DEFAULT 0        COMMENT '是否员工代下',
  assisted_by_user_id BIGINT                                 COMMENT '代下员工 user_id',

  -- 乐观锁
  version             INT          NOT NULL DEFAULT 0        COMMENT '乐观锁版本号',

  -- 活跃时间
  last_activity_time  DATETIME     NOT NULL                  COMMENT '最后活跃时间',

  -- 审计字段
  creator             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人',
  create_time         DATETIME     NOT NULL                  COMMENT '创建时间',
  updater             VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time         DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted             BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除',

  -- 索引
  UNIQUE KEY uk_active_cart (tenant_id, customer_user_id, shop_id, status, deleted),
  KEY idx_tenant_status (tenant_id, status, last_activity_time),
  KEY idx_table (table_id, status),
  KEY idx_business_date (tenant_id, business_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车主表';

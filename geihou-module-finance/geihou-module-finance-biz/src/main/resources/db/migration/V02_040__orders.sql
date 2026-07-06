-- ============================================================
-- TABLE: orders
-- OWNER: 组 1 - 订单管理 (G1-01A first code slice)
-- 子系统: 经营 ① 钱进来(7 大经营动作)
-- 多租户: 必含 tenant_id + 业务日期联合索引(支撑跨日切分)
-- 不可篡改: 核心字段(order_no/total_amount/business_date)创建后不允许 UPDATE
-- 金额精度: DECIMAL(18,4) + Java BigDecimal (H5 第 9 项)
-- ============================================================
CREATE TABLE orders (
  id              BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL                  COMMENT '租户ID(不变性 12)',

  -- 订单标识(全平台唯一)
  order_no        VARCHAR(32)  NOT NULL                  COMMENT '订单号(雪花算法或租户前缀+雪花)',

  -- 业务日期(关键!支撑跨日切分,与 created_time 分离)
  business_date   DATE         NOT NULL                  COMMENT '业务日期(按租户业务日切分小时计算)',

  -- 订单基础信息
  channel         VARCHAR(32)  NOT NULL                  COMMENT 'ENUM_ORDER_CHANNEL(SSOT): DINE_IN / SELF_PICKUP / MEITUAN_TAKEOUT / ELEME_TAKEOUT / DOUYIN_GROUP / MEITUAN_GROUP / WX_PRIVATE / OWN_TAKEOUT / OTHER',
  order_type      VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL / REFUND / COMPENSATION',

  -- 顾客信息(可能匿名)
  customer_user_id BIGINT                                COMMENT '顾客 user_id(可空,匿名下单时)',
  customer_phone  VARCHAR(32)                            COMMENT '顾客手机(脱敏存储,日志中要打码)',
  customer_name   VARCHAR(64)                            COMMENT '顾客昵称',
  member_id       BIGINT                                 COMMENT '会员 ID(成为会员后)',
  member_level    VARCHAR(20)                            COMMENT '会员等级(下单时刻快照)',

  -- 门店 / 桌台
  shop_id         BIGINT       NOT NULL                  COMMENT '门店 ID',
  table_session_id BIGINT                                COMMENT '桌台会话 ID(堂食才有)',
  table_no        VARCHAR(32)                            COMMENT '桌台号(堂食才有)',

  -- 金额(全部 BigDecimal,精度 4 位 — H5 第 9 项)
  total_amount    DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '订单总金额(应付)',
  paid_amount     DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '实付金额',
  discount_amount DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '优惠总额(满减+优惠券+会员价)',
  refund_amount   DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '已退款金额',

  -- 渠道平台抽成(美团 / 饿了么等)
  platform_fee    DECIMAL(18,4) NOT NULL DEFAULT 0       COMMENT '渠道平台抽成实际成本(对应蓝图 H4)',

  -- 状态(状态机驱动)
  status          VARCHAR(32)  NOT NULL                  COMMENT 'ENUM_ORDER_STATUS(SSOT): PENDING / PAID / ACCEPTED / PREPARING / READY / DELIVERING / DELIVERED / COMPLETED / REFUNDING / REFUNDED / CANCELLED / EXPIRED',

  -- 支付信息
  payment_method  VARCHAR(32)                            COMMENT 'ENUM_PAYMENT_METHOD',
  pay_time        DATETIME                               COMMENT '支付完成时间',

  -- 出餐 / 配送时间(KDS 联动)
  kitchen_time    DATETIME                               COMMENT '进入厨房时间',
  ready_time      DATETIME                               COMMENT '出餐完成时间',
  delivered_time  DATETIME                               COMMENT '送达 / 取餐时间',
  completed_time  DATETIME                               COMMENT '订单完成时间',
  cancelled_time  DATETIME                               COMMENT '取消时间',
  cancel_reason   VARCHAR(255)                           COMMENT '取消原因',

  -- 营销活动归因(支撑组 9 真实 ROI)
  promotion_ids   VARCHAR(255)                           COMMENT '关联营销活动 ID,逗号分隔',
  coupon_id       BIGINT                                 COMMENT '使用的优惠券 ID',

  -- 备注 / 偏好
  customer_remark VARCHAR(500)                           COMMENT '顾客备注',
  internal_remark VARCHAR(500)                           COMMENT '内部备注',

  -- 版本号(乐观锁,防并发改单)
  version         INT          NOT NULL DEFAULT 0        COMMENT '乐观锁版本号',

  -- 审计字段
  creator         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '创建人(顾客自己 / 员工代下)',
  create_time     DATETIME     NOT NULL                  COMMENT '创建时间(物理时间,可能跨业务日)',
  updater         VARCHAR(64)  NOT NULL DEFAULT ''       COMMENT '更新人',
  update_time     DATETIME     NOT NULL                  COMMENT '更新时间',
  deleted         BIT(1)       NOT NULL DEFAULT 0        COMMENT '软删除(订单原则上不删,只能取消)',

  -- 索引
  UNIQUE KEY uk_tenant_order_no (tenant_id, order_no, deleted),
  KEY idx_tenant_business_date (tenant_id, business_date),
  KEY idx_tenant_status (tenant_id, status, create_time),
  KEY idx_tenant_shop_date (tenant_id, shop_id, business_date),
  KEY idx_tenant_customer (tenant_id, customer_user_id, create_time),
  KEY idx_tenant_channel_date (tenant_id, channel, business_date),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

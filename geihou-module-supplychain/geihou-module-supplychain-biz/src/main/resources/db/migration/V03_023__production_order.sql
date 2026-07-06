-- V03_023__production_order.sql
-- G2-02K: production_order 生产工单主表
-- Source: PRD-组2-02 §2.1, 02-全局枚举表-V2.md ENUM_PRODUCTION_STAGE
-- 状态机服从 ENUM_PRODUCTION_STAGE: CREATED → MATERIAL_REQUEST → IN_PROGRESS → QUALITY_CHECK → COMPLETED / REWORK; 非终态可 CANCELLED
-- 本切片实现: CREATED → MATERIAL_REQUEST → IN_PROGRESS → COMPLETED + 非终态→CANCELLED
-- QUALITY_CHECK / REWORK 流转后续切片实现，DDL 枚举预留。

CREATE TABLE production_order (
  id                  BIGINT       UNSIGNED  AUTO_INCREMENT  PRIMARY KEY,
  tenant_id           BIGINT       NOT NULL                  COMMENT '租户ID(tenant_id first index)',

  -- 工单编号
  order_no            VARCHAR(64)  NOT NULL                  COMMENT '工单号(租户内唯一)',

  -- 产品 + 配方 + 库位
  product_id          BIGINT       NOT NULL                  COMMENT '产品ID(product_master, product_type=SEMI_FINISHED)',
  recipe_id           BIGINT       NOT NULL                  COMMENT '配方ID(bom_recipe, status=ACTIVE)',
  location_id         BIGINT       NOT NULL                  COMMENT '库位ID(stock_location, is_active=true)',

  -- 数量
  planned_qty         DECIMAL(18,4) NOT NULL                 COMMENT '计划生产数量(正数)',
  actual_qty          DECIMAL(18,4)                          COMMENT '实际生产数量(COMPLETED 时填写)',

  -- 状态机 (ENUM_PRODUCTION_STAGE)
  production_stage    VARCHAR(20)  NOT NULL DEFAULT 'CREATED' COMMENT 'ENUM_PRODUCTION_STAGE: CREATED/MATERIAL_REQUEST/IN_PROGRESS/QUALITY_CHECK/COMPLETED/REWORK/CANCELLED',

  -- 计划时间
  plan_start_time     DATETIME                               COMMENT '计划开始时间',
  plan_end_time       DATETIME                               COMMENT '计划结束时间',

  -- 实际时间
  actual_start_time   DATETIME                               COMMENT '实际开始时间(IN_PROGRESS 时填写)',
  actual_end_time     DATETIME                               COMMENT '实际结束时间(COMPLETED 时填写)',

  -- 操作人
  operator_user_id    BIGINT                                 COMMENT '操作人ID(中央厨房员工)',

  -- 备注
  remark              VARCHAR(500)                           COMMENT '备注',

  -- 审计
  creator             VARCHAR(64)  NOT NULL DEFAULT '',
  create_time         DATETIME     NOT NULL,
  updater             VARCHAR(64)  NOT NULL DEFAULT '',
  update_time         DATETIME     NOT NULL,
  deleted             BIT(1)       NOT NULL DEFAULT 0,

  -- tenant_id first indexes
  UNIQUE KEY uk_tenant_order_no (tenant_id, order_no, deleted),
  KEY idx_tenant_stage (tenant_id, production_stage),
  KEY idx_tenant_product (tenant_id, product_id),
  KEY idx_tenant_location (tenant_id, location_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产工单主表(半成品制作工单)';
